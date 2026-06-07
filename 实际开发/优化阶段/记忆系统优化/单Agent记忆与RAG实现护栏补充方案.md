# 单 Agent 记忆与 RAG 实现护栏补充方案

> 本文补充 `单Agent记忆与RAG设计方案.md`、`单Agent记忆与RAG融合方案.md` 和 `单Agent记忆与RAG-Embedding复用与并行优化方案.md` 中偏工程落地的护栏。重点不是新增大功能，而是防止记忆系统在隐私、冲突、一致性、并发和评测上留下隐患。

## 1. 记忆写入准入与隐私策略

长期记忆不是普通日志，不能把用户对话中所有内容都自动持久化。Memory 写入必须先经过准入判断，再进入提取、去重、入库和向量索引。

### 1.1 写入准入规则

允许自动写入的内容：

| 类型 | 示例 | 默认策略 |
|---|---|---|
| preference | 用户喜欢简洁回答、偏好 Java 方案 | `READ_WRITE` 下允许写入 |
| fact | 用户当前项目使用 Spring Boot 3.4.x | 高置信才写入 |
| summary | 一轮对话的短摘要 | 对话结束后异步写入 |
| tool_failure | 某工具在特定输入下失败及修复建议 | 仅记录脱敏输入和错误类别 |
| reflection | Agent 执行经验、规划教训 | 异步写入，不阻塞主链路 |

禁止自动写入的内容：

- 明确敏感信息：身份证、银行卡、手机号、邮箱、精确住址、API Key、密码、Token。
- 用户明确说“不要记住”“别保存”“仅本次有效”的内容。
- 未经确认的医学、法律、金融结论。
- 工具返回的大段原文、网页全文、RAG chunk 全文。
- 低置信推测，例如“用户可能喜欢 xxx”。

需要降级处理的内容：

- 含敏感信息但有长期价值：先脱敏，再保存摘要。例如“用户的公司邮箱是 a@b.com”不能保存原文，只能保存“用户可能使用公司邮箱沟通，具体邮箱不入记忆”。
- 含临时偏好：设置 `expires_at`，不进入永久偏好。
- 含项目上下文但来源不稳定：写为 `fact`，置信度较低，召回排序靠后。

### 1.2 Memory Strategy 精确定义

| 策略 | 读取长期记忆 | 写入长期记忆 | 会话内短期上下文 | 管理接口 |
|---|---|---|---|---|
| DISABLED | 否 | 否 | 仅当前请求必要上下文 | 可查看/管理已有记忆，但运行时不使用 |
| READ_ONLY | 是 | 否 | 是 | 可查看/禁用，不自动新增 |
| READ_WRITE | 是 | 是 | 是 | 全量可管理 |
| SESSION_ONLY | 否 | 否 | 是，会话结束清除 | 不产生长期 memory span 属于正确行为 |

实现要求：

- `MemoryWriteService` 必须显式检查策略，不能只依赖调用方不调用。
- `MemoryRecallService` 必须显式检查策略，避免未来新入口绕过。
- Trace 中记录 `memoryStrategy`，但不记录敏感原文。

### 1.3 用户显式控制优先级

用户显式指令优先于自动提取：

1. “忘掉/删除/不要再记住 X”：优先查找相关记忆并软禁用。
2. “记住 X”：在敏感扫描通过后写入，importance 初始值高于自动提取。
3. “这只是临时的”：允许写入 session context，不写长期记忆。
4. 手动 pin 的记忆：不被自动衰减、合并、淘汰覆盖。

建议在 `memories.metadata` 中增加或保留以下字段：

```json
{
  "source": "auto|explicit|manual|reflection|tool_failure",
  "sensitivity": "none|redacted|blocked",
  "pinned": false,
  "write_reason": "preference_extractor",
  "extract_confidence": 0.92
}
```

## 2. 冲突记忆处理

语义去重只能处理“相似”，不能处理“相反”。例如：

- 旧记忆：用户喜欢简洁回答。
- 新记忆：用户希望以后回答详细一点。

如果两条都注入上下文，模型会收到矛盾指令。

### 2.1 冲突检测

写入新记忆前，除相似记忆检索外，还要在同 scope 下检索同类候选：

- 同 `tenant_id / application_id / owner_user_id / profile_id`。
- 同 `memory_category`，优先 preference/fact。
- 语义相近但谓词或取值冲突。

第一版可用规则兜底：

- “喜欢/不喜欢”“偏好/不偏好”“要/不要”“简洁/详细”等反义偏好。
- 同一 key 的 preference，例如 `answer_style=concise` 被 `answer_style=detailed` 覆盖。
- 同一事实字段的新值覆盖旧值，例如项目端口、框架版本、部署环境。

后续可用 LLM 做异步冲突判断，但不能阻塞主链路。

### 2.2 覆盖关系

建议通过 metadata 记录替代关系，避免立刻物理删除：

```json
{
  "supersedes_memory_id": 123,
  "superseded_by_memory_id": 456,
  "conflict_resolution": "newer_explicit_preference_wins"
}
```

处理规则：

- 用户显式新偏好 > 自动旧偏好。
- pinned 记忆 > 自动新记忆，除非用户显式要求覆盖。
- 高置信事实 > 低置信事实。
- 新事实覆盖旧事实时，旧事实软禁用或标记 superseded。
- 召回阶段默认过滤 `status=SUPERSEDED/DISABLED/ARCHIVED`。

### 2.3 注入前最终去冲突

`MemorySlotSource` 在组装 Slot 前再做一次最终检查：

- 同一 preference key 只注入最新有效一条。
- 同一 fact key 只注入最高置信或最新有效一条。
- 如果冲突无法判断，降低两者分数，优先不注入，避免污染 Prompt。

## 3. 派生索引一致性与重建机制

PostgreSQL 是事实源，Qdrant、PG tsvector、Neo4j 都是派生索引。派生索引失败不能改变事实状态，但必须可观察、可重试、可重建。

### 3.1 状态字段建议

如果不想立即改表，可先放 metadata；后续正式化时再拆字段。

```json
{
  "vector_index_status": "PENDING|INDEXED|FAILED|DELETED",
  "vector_indexed_at": "2026-06-08T00:00:00Z",
  "vector_index_error": "timeout",
  "embedding_model": "mock-embedding-768",
  "embedding_dimension": 768,
  "graph_index_status": "PENDING|INDEXED|FAILED|SKIPPED",
  "keyword_index_status": "INDEXED"
}
```

RAG chunk 和 Memory 都应记录 embedding 模型版本，避免不同维度或不同模型混用。

### 3.2 写入顺序

Memory 写入：

```text
1. PostgreSQL 插入 memory，status=ACTIVE，vector_index_status=PENDING
2. 提交事务
3. 异步写 Qdrant / Neo4j / 其他派生索引
4. 成功后更新 metadata/status
5. 失败则记录 error，等待重试任务
```

RAG 入库：

```text
1. PostgreSQL 插入 document/chunks
2. chunk 标记 vector_index_status=PENDING
3. 异步或同步批量 embedding + upsert Qdrant
4. 更新 vector_index_status
5. 任一派生索引失败时，RAG search 仍可走 PostgreSQL fallback
```

删除/禁用：

```text
1. 先更新 PostgreSQL status=DISABLED/DELETED/ARCHIVED
2. 再删除 Qdrant point / Neo4j 节点边
3. 删除失败记录 Trace 和重试任务
4. 查询时以 PostgreSQL status 为准，避免已禁用内容被回表注入
```

### 3.3 Reindex / Backfill

需要补一个后台任务或管理接口能力：

- 按 source_type 重建：`memory` / `rag`。
- 按 scope 重建：tenant/application/profile/document。
- 按 embedding_model 重建：切换模型时新建 collection 或清空重建。
- 按失败状态重试：只处理 `FAILED/PENDING`。

最小实现可以先做服务方法和测试，不急着做前端按钮。

## 4. 并行检索与 Embedding 复用护栏

`CompletableFuture.supplyAsync` 不能直接使用默认 common pool。ContextBuilder 是核心链路，必须有独立线程池、超时、降级和 Trace 上下文传播。

### 4.1 独立线程池

建议新增配置：

```properties
agent.context.retrieval.pool-size=8
agent.context.retrieval.queue-capacity=100
agent.context.retrieval.timeout-ms=800
agent.context.retrieval.memory-timeout-ms=500
agent.context.retrieval.rag-timeout-ms=700
```

线程池要求：

- bounded queue，避免请求高峰拖垮 JVM。
- 拒绝策略降级为空结果，不阻塞主回答。
- 线程名包含 `context-retrieval-*`，方便排查。

### 4.2 超时与降级

并行检索应遵循：

- Embedding 失败：memory 和 RAG 都走关键词 fallback。
- Memory 超时：返回空记忆，Trace 标记 `fallbackMode=TIMEOUT_EMPTY`。
- RAG 超时：不注入 RAG Slot，Trace 标记 `fallbackMode=TIMEOUT_EMPTY`。
- 一路失败不影响另一路。
- join 必须使用 timeout，不能无界等待。

伪代码：

```java
CompletableFuture<List<MemoryDTO>> memoryFuture = supplyWithTrace(...)
        .completeOnTimeout(List.of(), memoryTimeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> List.of());

CompletableFuture<List<RagSearchResultDTO>> ragFuture = supplyWithTrace(...)
        .completeOnTimeout(List.of(), ragTimeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> List.of());
```

### 4.3 Trace 上下文传播

异步任务必须传递：

- traceId
- parentSpanId
- tenant/application/user/profile
- MDC 日志上下文

否则 Trace 工作台会看不到 `memory.embedding`、`rag.vector.search` 的父子关系。

建议封装：

```java
ContextAwareExecutor.submit(traceContext, () -> memoryRecallService.recall(...));
```

不要在各处手写 ThreadLocal 复制。

## 5. 检索效果评估与上线门槛

设计里已有 Hit@K / Recall@K / MRR，但实现前应固定一套最小评测集，避免“感觉更准”。

### 5.1 最小评测集

建议准备 20-50 条固定 query：

- 明确偏好召回：用户喜欢什么回答风格。
- 模糊指代召回：上次那个问题、之前说的端口。
- RAG 精确事实：某文档里的配置项。
- 中文同义词：登录/认证/鉴权，配置/设置。
- 冲突偏好：旧偏好被新偏好覆盖。
- 无答案问题：检索应该返回空，不应硬编。

### 5.2 指标

| 指标 | 说明 |
|---|---|
| Hit@5 | Top5 是否包含正确 memory/chunk |
| MRR | 正确结果排名越靠前越好 |
| citation accuracy | 最终回答引用是否真的来自命中 chunk |
| no-answer precision | 没有资料时是否拒绝乱答 |
| avg latency / P95 latency | 平均和长尾延迟 |
| fallback rate | Qdrant/Neo4j/embedding 失败降级比例 |
| cost per query | embedding、HyDE、MQE 额外成本 |

### 5.3 策略上线门槛

建议默认策略：

- Qdrant semantic recall：可默认开启。
- PG tsvector：完成后可默认开启。
- HyDE：默认关闭，评测证明提升后再按 Profile 开启。
- MQE：默认关闭，只给复杂检索 Profile 使用。
- Neo4j graph search：默认关闭，先用于 RAG 管理端实验。
- semantic cache：默认短 TTL，小心 tenant/profile scope。

## 6. RAG 引用约束

RAG 和 Memory 都会注入上下文，但语义不同：

- RAG 是外部知识依据，可以作为 citation。
- Memory 是用户长期状态，不能当公开事实引用，尤其不能在回答中泄露“我记得你的隐私信息”。

### 6.1 RAG chunk 注入格式

建议每条 RAG 注入都带最小来源信息：

```text
[RAG:documentId=..., chunkId=..., title=..., sourceUri=..., score=...]
chunk 摘要或片段内容
```

最终回答引用只能指向 RAG source，不引用 memoryId。

### 6.2 回答规则

- 使用 RAG 内容回答事实问题时，尽量带来源标题或文档名。
- 检索结果不足时说明“不确定/当前知识库没有找到依据”。
- Memory 只能用于个性化表达和上下文连续性，不作为外部事实证据。
- 引用展示不暴露 tenant/user/profile/internal chunk id 给普通用户，前端可用安全的 document title/sourceUri。

## 7. 建议补入现有文档的位置

- `单Agent记忆与RAG设计方案.md`：补第 5 章后增加“写入准入、隐私、冲突处理”；第 8 章 Trace 后增加“派生索引一致性”。
- `单Agent记忆与RAG融合方案.md`：修正数据边界图，明确 memory collection 和 rag collection 分离。
- `单Agent记忆与RAG-Embedding复用与并行优化方案.md`：补线程池、超时、Trace 上下文传播。
- `单Agent记忆与RAG后端冒烟测试指南.md`：新增冲突记忆、禁用记忆不召回、Qdrant 删除失败降级、HyDE 默认关闭等测试项。
