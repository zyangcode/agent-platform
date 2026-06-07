# 单 Agent 记忆与 RAG 后端冒烟测试指南

> 本文用于固定验证 Memory v2、RAG MVP、VectorStore 降级链和 Trace Span 是否接入单 Agent 主链路。目标是不用翻聊天记录，也能复测 `Web -> Gateway -> Core Runtime -> Context Slot -> Memory/RAG -> Trace`。

## 1. 验证范围

本指南只验证后端主链路：

```text
JWT 登录
-> 选择 Application / READ_WRITE Profile
-> RAG 管理接口入库
-> Chat SSE 触发 Context Builder
-> Memory 写入与下一轮召回
-> RAG 检索注入
-> Trace 查询 memory.* / rag.* span
```

不验证前端页面审美、RAG 引用 UI、Jar Skill 上传 UI、真实 Qdrant 服务可用性。

## 2. 前置条件

1. Web 后端已启动在 `http://localhost:8080`。
2. Gateway 已由 Web 可访问，`/api/chat/stream` 能返回 SSE。
3. 数据库已执行 Flyway，存在 seed 用户 `admin/admin123`。
4. 至少有一个 `memoryStrategy.mode=READ_WRITE` 的 Profile。

注意：`SESSION_ONLY` 和 `DISABLED` Profile 看不到长期记忆 span 是正确行为，不是故障。

## 3. 自动化测试

先跑 core 定向测试：

```powershell
mvn.cmd -s .mvn/settings.xml -pl agent-platform-core `
  '-Dtest=PreferenceExtractorTest,DefaultAgentRuntimeServiceTest,DefaultMemoryManagementServiceTest,DefaultMemoryRecallServiceTest,DefaultPostgresRagEngineTest,RagSearchServiceConfigurationTest,RagSlotSourceTest' `
  test
```

再跑 Web / Gateway context 测试。需要带 `-am`，避免使用本地仓库里的旧 core snapshot：

```powershell
mvn.cmd -s .mvn/settings.xml -pl agent-platform-web -am `
  '-Dtest=RagKnowledgeControllerTest,MemoryControllerTest,WebApplicationTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  test

mvn.cmd -s .mvn/settings.xml -pl agent-platform-gateway -am `
  '-Dtest=GatewayApplicationTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  test
```

通过标准：

```text
BUILD SUCCESS
Failures: 0
Errors: 0
```

## 4. 登录并选择 Profile

```powershell
$base = 'http://localhost:8080'
$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri ($base + '/api/auth/login') `
  -ContentType 'application/json' `
  -Body $loginBody `
  -TimeoutSec 15

$headers = @{ Authorization = ('Bearer ' + $login.data.accessToken) }

$apps = Invoke-RestMethod -Method Get `
  -Uri ($base + '/api/applications?pageNo=1&pageSize=20') `
  -Headers $headers `
  -TimeoutSec 15

$appId = $apps.data.records[0].applicationId

$profiles = Invoke-RestMethod -Method Get `
  -Uri ($base + '/api/profiles?applicationId=' + $appId + '&pageNo=1&pageSize=100') `
  -Headers $headers `
  -TimeoutSec 15

$profiles.data.records |
  Select-Object profileId,name,modelConfigId,executionMode,visibility,@{Name='memoryMode';Expression={$_.memoryStrategy.mode}} |
  ConvertTo-Json -Depth 5
```

选择 `memoryMode=READ_WRITE` 的 profile：

```powershell
$profileId = ($profiles.data.records | Where-Object { $_.memoryStrategy.mode -eq 'READ_WRITE' } | Select-Object -First 1).profileId
$profileId
```

如果没有 READ_WRITE Profile，先在前端或 Profile API 新建一个。不要用 `SESSION_ONLY` 验证长期记忆写入。

## 5. Memory 主链路冒烟

### 5.1 首轮写入偏好

```powershell
$marker = 'MEM-SMOKE-' + ([Guid]::NewGuid().ToString('N').Substring(0,12))
$body1 = @{
  applicationId = $appId
  agentMode = 'agent'
  profileId = $profileId
  message = "I prefer turquoise scoreboards for basketball planning. Preference marker $marker."
  stream = $true
} | ConvertTo-Json -Depth 8 -Compress

$r1 = Invoke-WebRequest -Method Post `
  -Uri ($base + '/api/chat/stream') `
  -Headers ($headers + @{ Accept = 'text/event-stream' }) `
  -ContentType 'application/json' `
  -Body $body1 `
  -UseBasicParsing `
  -TimeoutSec 120

$firstTrace = ([regex]::Match($r1.Content, '"traceId"\s*:\s*"([^"]+)"')).Groups[1].Value
"marker=$marker"
"firstTrace=$firstTrace"
```

### 5.2 管理接口查询长期记忆

```powershell
Start-Sleep -Seconds 2

$mem = Invoke-RestMethod -Method Get `
  -Uri ($base + "/api/memories?applicationId=$appId&profileId=$profileId&query=$marker&limit=20") `
  -Headers $headers `
  -TimeoutSec 30

"memoryCount=$($mem.data.Count)"
$mem.data | Select-Object id,memoryType,memoryCategory,importance,content | ConvertTo-Json -Depth 5
```

通过标准：

```text
memoryCount >= 1
content 包含 marker
至少有 SUMMARY；英文偏好规则生效后应额外出现 PREFERENCE / preference
```

### 5.3 第二轮召回记忆

```powershell
$body2 = @{
  applicationId = $appId
  agentMode = 'agent'
  profileId = $profileId
  message = "What basketball planning preference did I tell you? Use my remembered preference if available. Marker: $marker"
  stream = $true
} | ConvertTo-Json -Depth 8 -Compress

$r2 = Invoke-WebRequest -Method Post `
  -Uri ($base + '/api/chat/stream') `
  -Headers ($headers + @{ Accept = 'text/event-stream' }) `
  -ContentType 'application/json' `
  -Body $body2 `
  -UseBasicParsing `
  -TimeoutSec 120

$secondTrace = ([regex]::Match($r2.Content, '"traceId"\s*:\s*"([^"]+)"')).Groups[1].Value
"secondTrace=$secondTrace"
($r2.Content -split "`n") | Where-Object { $_ -like 'data:*' } | Select-Object -Last 5
```

通过标准：

```text
SSE 包含 message_delta 或 message
回答内容能引用 turquoise scoreboards / basketball planning preference / marker
```

### 5.4 查询 Memory Trace Span

```powershell
$trace2 = Invoke-RestMethod -Method Get `
  -Uri ($base + '/api/traces/' + $secondTrace) `
  -Headers $headers `
  -TimeoutSec 30

$trace2.data.spans |
  Where-Object { $_.spanName -like 'memory*' } |
  Select-Object spanName,status,attributes |
  ConvertTo-Json -Depth 8
```

通过标准：

```text
memory.recall        SUCCESS, recalledCount >= 1
memory.embedding     SUCCESS, model / dimension / queryChars 有值
memory.vector.search SUCCESS, source_type=memory 语义索引路径可观察
memory.write         SUCCESS, summaryCount >= 1
```

如果 `memory.vector.search.resultCount=0`，但 `memory.recall.recalledCount>=1`，说明向量召回没有命中但 PostgreSQL fallback 命中，主链路仍然可用。

## 6. RAG 主链路冒烟

### 6.1 插入测试知识文档

```powershell
$ragMarker = 'RAG-SMOKE-' + ([Guid]::NewGuid().ToString('N').Substring(0,12))
$ragContent = "Knowledge marker $ragMarker. Red maple court is safe after 19:30 only when the ground is dry."

$ragBody = @{
  applicationId = $appId
  profileId = $profileId
  title = "RAG smoke $ragMarker"
  sourceType = "manual"
  sourceUri = "smoke://$ragMarker"
  content = $ragContent
  chunkTokenBudget = 220
  overlapTokens = 30
} | ConvertTo-Json -Depth 8 -Compress

$ingest = Invoke-RestMethod -Method Post `
  -Uri ($base + '/api/rag/documents') `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body $ragBody `
  -TimeoutSec 60

$documentId = $ingest.data.documentId
$documentId
$ingest.data | ConvertTo-Json -Depth 5
```

通过标准：

```text
documentId 有值
chunkCount >= 1
status = INDEXED
```

### 6.2 管理接口检索知识库

```powershell
$ragSearch = Invoke-RestMethod -Method Get `
  -Uri ($base + "/api/rag/search?applicationId=$appId&profileId=$profileId&query=$ragMarker&topK=5") `
  -Headers $headers `
  -TimeoutSec 30

"ragSearchCount=$($ragSearch.data.Count)"
$ragSearch.data | Select-Object documentId,chunkId,title,score,content | ConvertTo-Json -Depth 5
```

通过标准：

```text
ragSearchCount >= 1
content 包含 ragMarker 或 Red maple court
```

### 6.3 Chat 触发 RAG 注入

```powershell
$ragChatBody = @{
  applicationId = $appId
  agentMode = 'agent'
  profileId = $profileId
  message = "Use the knowledge base to answer this exact marker $ragMarker. When is red maple court safe?"
  stream = $true
} | ConvertTo-Json -Depth 8 -Compress

$ragRun = Invoke-WebRequest -Method Post `
  -Uri ($base + '/api/chat/stream') `
  -Headers ($headers + @{ Accept = 'text/event-stream' }) `
  -ContentType 'application/json' `
  -Body $ragChatBody `
  -UseBasicParsing `
  -TimeoutSec 120

$ragTrace = ([regex]::Match($ragRun.Content, '"traceId"\s*:\s*"([^"]+)"')).Groups[1].Value
"ragTrace=$ragTrace"
($ragRun.Content -split "`n") | Where-Object { $_ -like 'data:*' } | Select-Object -Last 5
```

通过标准：

```text
回答包含 after 19:30 / ground is dry / Red maple court
```

### 6.4 查询 RAG Trace Span

```powershell
$trace = Invoke-RestMethod -Method Get `
  -Uri ($base + '/api/traces/' + $ragTrace) `
  -Headers $headers `
  -TimeoutSec 30

$trace.data.spans |
  Where-Object { $_.spanName -like 'rag*' -or $_.spanName -eq 'context.budget.snapshot' -or $_.spanName -eq 'context.build' } |
  Select-Object spanName,status,attributes |
  ConvertTo-Json -Depth 8
```

通过标准：

```text
context.build           SUCCESS
rag.search              SUCCESS, returnedCount >= 1
context.budget.snapshot SUCCESS, ragTokens > 0
rag.embedding           SUCCESS, model / dimension / queryChars 有值
rag.vector.search       SUCCESS, vectorStore 有值，resultCount 可为 0
```

关键判断：

```text
rag.search.attributes.searchService 应为 DefaultPostgresRagEngine
rag.vector.search.attributes.vectorStore 应为 QdrantVectorStore 或 MockVectorStore
```

如果是 `DefaultLocalRagEngine`，说明管理入库和 Agent 检索没有使用同一个 RAG 事实源，需要检查 `RagSearchServiceConfiguration` 和后端启动配置中的 `spring.datasource.url`。

### 6.5 清理测试文档

```powershell
$deleted = Invoke-RestMethod -Method Delete `
  -Uri ($base + "/api/rag/documents/$documentId?applicationId=$appId&profileId=$profileId") `
  -Headers $headers `
  -TimeoutSec 30

"cleanup.deleted=$($deleted.data)"
```

通过标准：

```text
cleanup.deleted=1
```

## 7. 常见误判

### 7.1 SESSION_ONLY 没有 memory span

这是正确行为。`SESSION_ONLY` 只保留短期上下文，不读写长期记忆。要验证长期记忆完整链路，必须使用 `READ_WRITE` Profile。

### 7.2 memory.vector.search 为 0

不一定是失败。长期记忆事实源是 PostgreSQL，VectorStore 是派生索引。只要 `memory.recall.recalledCount>=1`，说明 fallback 仍可用。

### 7.3 rag.vector.search 为 0

不一定是失败。RAG 的 PostgreSQL keyword fallback 可以命中。若 `rag.search.returnedCount>=1` 且回答引用知识库内容，主链路可用。

### 7.4 PowerShell 中文乱码

PowerShell 可能把 UTF-8 SSE 内容显示成乱码。这通常是终端编码问题，不代表后端返回错误。可用浏览器前端或保存响应后按 UTF-8 查看。

### 7.5 后端修改后 HTTP 冒烟没体现

如果改了 Java 代码，需要重启 Web/Gateway 后端。Maven 测试通过只证明代码层生效，正在运行的服务不会自动热更新。

### 7.6 PowerShell URL 中变量后直接接 `?` 导致误判

PowerShell 双引号字符串里不要写 `".../$documentId?applicationId=..."` 或 `".../$id?applicationId=..."`。`?` 可能被当成变量名的一部分，导致实际请求 URL 不符合预期，表现为删除接口误报 500 或参数丢失。

推荐写法：

```powershell
$deleted = Invoke-RestMethod -Method Delete `
  -Uri ($base + "/api/rag/documents/${documentId}?applicationId=$appId&profileId=$profileId") `
  -Headers $headers `
  -TimeoutSec 30
```

或者：

```powershell
$uri = $base + '/api/rag/documents/' + $documentId + '?applicationId=' + $appId + '&profileId=' + $profileId
$deleted = Invoke-RestMethod -Method Delete -Uri $uri -Headers $headers -TimeoutSec 30
```

## 8. 当前已验证结论

最近一次后端实测结论：

```text
Memory:
- READ_WRITE Profile 下写入、管理查询、下一轮召回、Trace memory span 均通过。
- SESSION_ONLY Profile 下没有长期记忆 span 属于正确策略行为。
- 后端重启后，英文 "I prefer ..." 已能写入 PREFERENCE / preference 记忆。
- 示例：id=99, memoryType=PREFERENCE, memoryCategory=preference, content=User preference: violet scoreboards for basketball planning...
- Trace memory.write 已验证 summaryCount=1, preferenceCount=1。

RAG:
- /api/rag/documents 管理入库成功。
- /api/chat/stream 进入 Agent 后使用 DefaultPostgresRagEngine 检索。
- rag.search returnedCount >= 1。
- rag.embedding / rag.vector.search / context.budget.snapshot 可在 Trace 中观察。
- 带 trace 上下文的 rag.ingest 可观察 embeddingService、vectorStore 和 vectorIndexedCount。
- vector search 为 0 时，PostgreSQL keyword fallback 仍可命中。
- 真实 Qdrant 联调已验证：RAG chunk 能写入 Qdrant，payload.vector_id 保留业务 ID；删除文档后 PostgreSQL 检索和 Qdrant point 均清理完成。
```

## 9. 真实 Qdrant 联调条件

`docker-compose.dev.yml` 已包含 Qdrant：

```text
container: agent-platform-dev-qdrant
http port: 6333
grpc port: 6334
image: qdrant/qdrant:v1.12.6
```

但当前后端默认仍使用 `MockVectorStore`。要让运行时真正走 Qdrant，启动 Web/Gateway 时必须显式配置：

```text
agent.rag.vector-store=qdrant
agent.rag.qdrant.enabled=true
agent.rag.qdrant.base-url=http://localhost:6333
agent.rag.qdrant.collection-name=rag_chunks
agent.rag.qdrant.dimension=768
```

同时 embedding 也要保持 768 维。当前默认 `MockEmbeddingService` 输出 768 维，因此可以先用 mock embedding + Qdrant 验证向量索引链路，不必先接真实 OpenAI-compatible embedding。

真实 Qdrant 联调通过标准：

```text
1. Qdrant 容器 /collections/rag_chunks 可访问。
2. rag.vector.search.attributes.vectorStore = QdrantVectorStore。
3. RAG ingest 后 Qdrant collection 中有 point，payload.vector_id 保留 rag-chunk-* 业务 ID。
4. rag.vector.search.resultCount >= 1。
5. rag.search.returnedCount >= 1。
6. 关闭 Qdrant 后，RAG 仍可通过 PostgreSQL keyword fallback 返回结果。
```

如果只启动了 Qdrant 容器，但没有配置上述后端参数，Trace 中仍可能显示 `rag.vector.search`，但 `vectorStore=MockVectorStore`，不是 Qdrant。此时 collection 为空是符合预期的配置未生效现象，不是 Qdrant 服务本身故障。

注意：Qdrant point id 只能使用数字或 UUID。平台在 Qdrant 内部使用稳定 UUID 作为 point id，并把业务层的 `rag-chunk-*` / `memory-*` 写入 payload.vector_id；如果直接把普通字符串业务 ID 当 point id，Qdrant 会返回 400，表现为 collection 已创建但 points_count 不增加。
