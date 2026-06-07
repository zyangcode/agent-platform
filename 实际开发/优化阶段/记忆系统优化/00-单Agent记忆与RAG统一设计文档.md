# 单 Agent 记忆系统与 RAG 统一设计文档

> 本文是 `实际开发/优化阶段/记忆系统优化` 目录的统一入口文档。它把原先分散在基础设计、融合方案、混合检索、Embedding 并行优化、实现护栏、冒烟测试中的内容按工程落地顺序整合到一份设计里。旧文档保留为展开材料，后续开发优先看本文。

## 0. 文档定位

本设计服务于单 Agent 主链路的记忆与知识增强，不包含多 Agent Team 的运行时编排。目标是让 Agent 在保持安全、可控、可降级的前提下，具备：

- 会话内工作记忆：保留近期对话和必要上下文。
- 用户长期记忆：记住偏好、事实、摘要、工具经验和反思。
- RAG 外部知识：接入用户/应用/Profile 范围内的知识文档。
- Schema-driven Context：用 Slot 统一注入 profile/history/memory/tools/experience/rag。
- 混合检索：向量、倒排、图谱多路召回后融合排序。
- Trace 可观测：能看到 memory/rag/context 的召回、降级、预算和索引状态。
- 工程护栏：隐私准入、冲突处理、索引一致性、线程池超时、评测门槛。

## 1. 设计原则

1. PostgreSQL 是事实源，Qdrant、PG tsvector、Neo4j 都是派生索引。
2. Memory 和 RAG 存储隔离、索引隔离、召回隔离，只在 `core.context` Slot 注入层汇合。
3. Memory 用于用户长期状态和个性化，不作为外部事实 citation。
4. RAG 用于外部知识依据，可以作为 citation。
5. 所有能力必须支持本地 mock、失败降级和无外网测试。
6. 敏感数据不进入 Trace attributes，不默认进入长期记忆。
7. 复杂增强默认关闭，先通过评测再按 Profile/配置启用。

## 2. 当前状态

### 2.1 已完成或已有骨架

- `memories` 表已有长期记忆基础能力。
- `DefaultMemoryRecallService` 支持长期记忆召回，已按 category/filter 做基础过滤。
- `DefaultAgentContextBuilder` 已能把记忆纳入上下文预算。
- `ContextBudgetSnapshotDTO` 已有 budget 观测基础。
- RAG MVP 已有 `knowledge_documents` / `knowledge_chunks`、TextSplitter、RagEngine、RagSlotSource 方向。
- `MockEmbeddingService` / `MockVectorStore` 用于本地演示和测试。
- `QdrantVectorStore` 已有配置切换、collection 懒初始化、upsert/search/deleteByDocument、payload filter 和失败降级方向。
- RAG 和 Memory 的向量召回都已明确通过 `source_type=rag|memory` 隔离。
- 后端冒烟指南已覆盖 Memory 写入召回、RAG 入库检索、Trace span、Qdrant 联调条件。

### 2.2 仍需补齐

- Memory 写入准入、敏感信息策略、用户显式记忆控制。
- 冲突记忆处理：新旧偏好覆盖、事实 supersede、注入前去冲突。
- 派生索引状态、重试、reindex/backfill。
- Embedding 一次计算、Memory/RAG 并行检索、独立线程池与超时。
- PG tsvector 倒排检索与 RRF 融合。
- Neo4j 图谱检索，默认作为后续增强。
- 检索评测集与策略上线门槛。
- RAG citation 前端展示和引用约束。

## 3. 总体架构

```text
用户请求
  -> Gateway 治理链：鉴权 / Trace / Token / 脱敏 / 告警
  -> Core Agent Runtime
  -> DefaultAgentContextBuilder
       -> ContextSchemaAssembler
       -> SlotSources 并行/按需拉取
          -> ProfileSlotSource
          -> HistorySlotSource
          -> MemorySlotSource
          -> ToolSlotSource
          -> ExperienceSlotSource
          -> RagSlotSource
       -> ContextBudgetPolicy 裁剪
       -> AgentContextDTO
  -> Agent ReAct / Model 调用
  -> 对话结束后异步 Memory Write / Summary / Reflection
```

Memory/RAG 数据边界：

```text
core.memory（用户长期状态）
  PostgreSQL: memories 表
  Qdrant collection: "memory"
  PG tsvector: memories.search_vector（Stage 5A）
  Neo4j: 用户事实/偏好图谱索引（可选）
  categories:
    summary / preference / fact / episodic / tool_failure / reflection

core.rag（外部知识依据）
  PostgreSQL: knowledge_documents / knowledge_chunks 表
  Qdrant collection: "rag"
  PG tsvector: knowledge_chunks.search_vector（Stage 5A）
  Neo4j: 文档实体关系图谱索引（Stage 5B，可选）

core.context（唯一汇合点）
  MemorySlotSource -> TASK_MEMORY Slot
  RagSlotSource    -> RAG_RECALL Slot
```

推荐包边界：

```text
core.memory
  用户长期记忆、偏好、事实、摘要、召回、写入、合并、手动管理

core.rag
  知识文档、chunk、embedding、vector store、RAG 检索、RAG 删除

core.context
  Context Slot、Schema、SlotSource、预算裁剪、注入顺序

core.experience
  经验型 Skill、反思摘要、工具失败经验复用
```

## 4. Memory 设计

### 4.1 记忆分层

| 层次 | 当前落点 | 说明 |
|---|---|---|
| Working Memory | `apiMessages` + compactHistory + MicroCompact | 会话内短期上下文，会话结束清除 |
| Episodic Memory | `memories.category=summary/episodic` | 对话摘要、事件时间线 |
| Semantic Memory | `memories.category=preference/fact` | 用户偏好、用户/项目事实 |
| Procedural Memory | `memories.category=tool_failure` | 工具失败经验、工作流经验 |
| Meta Memory | `memories.category=reflection` | 对话后反思、执行教训 |

### 4.2 记忆分类

- `summary`：对话摘要。
- `preference`：用户偏好，例如回答风格、技术栈偏好。
- `fact`：用户、项目、环境事实。
- `episodic`：事件级记忆，例如某天解决了某个问题。
- `tool_failure`：工具调用失败经验和修复建议。
- `reflection`：Agent 执行后的反思经验。

### 4.3 字段与 metadata

核心字段建议：

- `tenant_id`
- `application_id`
- `owner_user_id`
- `profile_id`
- `memory_category`
- `content`
- `summary`
- `tags`
- `importance`
- `confidence`
- `last_accessed_at`
- `access_count`
- `expires_at`
- `status`
- `metadata`

metadata 建议承载尚未正式字段化的状态：

```json
{
  "source": "auto|explicit|manual|reflection|tool_failure",
  "sensitivity": "none|redacted|blocked",
  "pinned": false,
  "write_reason": "preference_extractor",
  "extract_confidence": 0.92,
  "supersedes_memory_id": 123,
  "vector_index_status": "PENDING|INDEXED|FAILED|DELETED",
  "embedding_model": "mock-embedding-768",
  "embedding_dimension": 768
}
```

### 4.4 写入准入

允许自动写入：

| 类型 | 默认策略 |
|---|---|
| preference | `READ_WRITE` 下允许写入 |
| fact | 高置信才写入 |
| summary | 对话结束后异步写入 |
| tool_failure | 仅记录脱敏输入和错误类别 |
| reflection | 异步写入，不阻塞主链路 |

禁止自动写入：

- 身份证、银行卡、手机号、邮箱、精确住址、API Key、密码、Token。
- 用户明确说“不要记住”“别保存”“仅本次有效”的内容。
- 未经确认的医学、法律、金融结论。
- 工具返回的大段原文、网页全文、RAG chunk 全文。
- 低置信推测，例如“用户可能喜欢 xxx”。

需要降级处理：

- 含敏感信息但有长期价值：先脱敏，再保存摘要。
- 临时偏好：设置 `expires_at`，不进入永久偏好。
- 来源不稳定的项目上下文：写为低置信 `fact`，召回排序靠后。

### 4.5 Memory Strategy

| 策略 | 读取长期记忆 | 写入长期记忆 | 会话内短期上下文 | 说明 |
|---|---|---|---|---|
| DISABLED | 否 | 否 | 仅当前请求必要上下文 | 运行时完全不用长期记忆 |
| READ_ONLY | 是 | 否 | 是 | 可召回，不自动新增 |
| READ_WRITE | 是 | 是 | 是 | 完整记忆能力 |
| SESSION_ONLY | 否 | 否 | 是 | 会话结束清除，不产生长期 memory span 是正确行为 |

实现要求：

- `MemoryWriteService` 和 `MemoryRecallService` 都必须显式检查策略。
- Trace 记录 `memoryStrategy`，但不记录敏感原文。
- 管理接口可以查看/禁用已有记忆，但运行时是否使用由策略控制。

### 4.6 用户显式控制

优先级：

1. “忘掉/删除/不要再记住 X”：查找相关记忆并软禁用。
2. “记住 X”：敏感扫描通过后写入，importance 高于自动提取。
3. “这只是临时的”：只进入 session context，不写长期记忆。
4. 手动 pin：不被自动衰减、合并、淘汰覆盖。

### 4.7 语义去重与冲突处理

去重：

```text
新记忆 -> 同 scope 检索相似记忆
  -> sim >= dedupThreshold: 更新已有 importance / last_accessed_at / access_count / tags
  -> sim < threshold: 新增
```

冲突：

- 同一 preference key 只保留最新有效或用户显式指定的一条。
- 同一 fact key 只保留最高置信或最新有效的一条。
- 新偏好覆盖旧偏好时，旧记忆标记 `SUPERSEDED` 或写入 metadata 替代关系。
- pinned 记忆不能被自动覆盖，除非用户显式要求。
- 注入前再做最终去冲突，避免互相矛盾的记忆同时进入 Prompt。

### 4.8 召回排序

建议从简单 score 演进为 Utility Score：

```text
utility = alpha * recency
        + beta  * relevance
        + gamma * importance
        + delta * accessBoost
```

其中：

- `recency`：越近越高。
- `relevance`：向量相似度 / keyword hits / RRF score。
- `importance`：用户显式重要性或自动评估重要性。
- `accessBoost`：访问频率加成，但要设置上限，避免老记忆垄断。

### 4.9 Consolidation

定时任务三阶段：

```text
阶段 1：重要性衰减
  低访问、低重要性记忆缓慢降权

阶段 2：去重合并
  相似度 >= 0.9 的记忆保留更重要的一条，软禁用其余

阶段 3：过期淘汰
  importance 很低且长期未访问的记忆归档或禁用
```

注意：用户显式偏好、高置信事实、pinned 记忆不能被自动删除。

## 5. RAG 设计

### 5.1 RAG 与 Memory 区别

| 维度 | Memory | RAG |
|---|---|---|
| 本质 | 用户长期状态 | 外部知识依据 |
| 典型内容 | 偏好、事实、摘要、工具经验 | 文档、教程、接口说明、项目资料 |
| 生命周期 | 衰减、合并、过期、手动管理 | 文档版本化、删除、重建索引 |
| 注入方式 | TASK_MEMORY Slot | RAG_RECALL Slot |
| citation | 不作为外部引用 | 可以作为引用来源 |

### 5.2 数据表

`knowledge_documents`：

- `tenant_id`
- `application_id`
- `profile_id`
- `owner_user_id`
- `title`
- `source_uri`
- `doc_hash`
- `status`
- `metadata`

`knowledge_chunks`：

- `tenant_id`
- `application_id`
- `profile_id`
- `document_id`
- `chunk_index`
- `content`
- `token_count`
- `vector_id`
- `metadata`
- `status`

### 5.3 入库流程

```text
POST /api/rag/documents
  -> Web 从 JWT 注入 tenantId/userId
  -> RAG Service 计算 docHash，去重或新版本
  -> TextSplitter 生成 chunks
  -> PostgreSQL 写 document/chunks
  -> EmbeddingService 生成 chunk embedding
  -> VectorStore.upsert(source_type=rag, collection=rag)
  -> Trace: rag.ingest / rag.embedding / rag.vector.upsert
```

删除流程：

```text
DELETE /api/rag/documents/{documentId}
  -> 先更新 PostgreSQL status
  -> 再删除 Qdrant point / Neo4j 节点边
  -> 删除失败记录 Trace 和重试任务
  -> 查询以 PostgreSQL status 为准
```

### 5.4 文档切割策略

首版：固定大小 + overlap。

- 默认 chunk token：约 400-800。
- overlap token：约 50-100。
- 保留 `titlePath/sourceUri/chunkIndex`，方便 citation。

增强：

- 标题层级 + 语义边界：适合 Markdown、文档站。
- 父子切割：child chunk 做 embedding，parent chunk 回填上下文。
- 代码文档切割：按类、函数、代码块切分。
- Late Chunking 暂不作为 MVP，依赖更复杂 embedding 流程。

### 5.5 Embedding 模型

原则：

- 聊天模型和 embedding 模型必须分开配置。
- 本地开发默认 `MockEmbeddingService`，不依赖外网。
- Qdrant collection 维度固定，切换模型必须新建 collection 或重建索引。
- chunk metadata 记录 embedding model/dimension，避免混用。

推荐阶段：

```text
Stage 3/4 默认:
  MockEmbeddingService, dimension=768
  MockVectorStore 或 Qdrant

Stage 4 API 快速接入:
  OpenAI-compatible embedding
  Qdrant

Stage 5 评估:
  bge-large-zh-v1.5 / bge-m3 / Qwen3-Embedding / text-embedding-3-small
  用业务评测集比较 Hit@5 / MRR / latency / cost
```

### 5.6 RAG 引用约束

RAG chunk 注入格式：

```text
[RAG:documentId=..., chunkId=..., title=..., sourceUri=..., score=...]
chunk 摘要或片段内容
```

回答规则：

- 使用 RAG 内容回答事实问题时，尽量带来源标题或文档名。
- 检索结果不足时说明当前知识库没有找到依据。
- Memory 不作为 citation 来源。
- 前端引用展示不暴露 tenant/user/profile/internal id。

## 6. 混合检索设计

### 6.1 当前状态

```text
Memory:
  向量召回 source_type=memory -> PG 回表
  失败 -> PostgreSQL keyword fallback

RAG:
  向量召回 source_type=rag -> PG 回表
  失败 -> PostgreSQL keyword fallback
```

### 6.2 目标状态

```text
用户 query
  -> query embedding 只计算一次
  -> Memory 与 RAG 并行召回

MemoryRecallService:
  并行 1: Qdrant ANN collection=memory
  并行 2: PG tsvector / ES BM25
  并行 3: Neo4j 用户事实图谱（可选）
  -> RRF 融合
  -> Utility Score 排序
  -> PG 回表
  -> TASK_MEMORY Slot

RagSearchService:
  并行 1: Qdrant ANN collection=rag
  并行 2: PG tsvector / ES BM25
  并行 3: Neo4j 文档图谱（可选）
  -> RRF 融合
  -> PG 回表
  -> RAG_RECALL Slot
```

### 6.3 PG tsvector

先用 PostgreSQL tsvector 替代 LIKE 全表扫描：

- 零新组件。
- GIN 索引加速。
- 后续可通过接口切换 Elasticsearch。

建议改动：

- `knowledge_chunks.search_vector`
- `memories.search_vector`
- GIN index
- `PgTsvectorKeywordSearch.search("rag"|"memory")`

### 6.4 Neo4j 图谱

默认后续增强，不阻塞主链路。

用途：

- 关系型问题，例如“Spring Security 和 PasswordEncoder 的关系”。
- 实体邻接查询，例如从用户输入实体做 2 跳游走。
- 文档实体关系补充向量召回盲区。

降级：Neo4j 不可用时返回空列表，不影响向量 + 倒排。

### 6.5 RRF 融合

不同召回路径分数量纲不同，使用 RRF：

```text
RRF_score(d) = sum(1 / (k + rank_i(d)))
```

默认 `k=60`。优点：

- 不依赖原始分数归一化。
- 某一路失败时天然降级。
- 多路 topK 容易合并。

### 6.6 HyDE / MQE / Semantic Cache

默认关闭，评测通过后再按 Profile 启用。

- HyDE：模糊问题先生成假设答案再 embedding，提升模糊指代命中率，但增加 LLM 成本和延迟。
- MQE：把 query 改写为多个子查询并行检索，适合复杂问题。
- Semantic Cache：缓存相同 query 的检索结果，key 必须包含 tenant/profile/source_type/queryHash，TTL 短。

## 7. Embedding 复用与并行检索

### 7.1 问题

当前如果 Memory 和 RAG 各自调用 embedding，会出现：

```text
DefaultAgentContextBuilder.build()
  -> MemoryRecallService.embed(userInput)
  -> RagSearchService.embed(userInput)
```

同一个输入被向量化两次，且两边检索串行，增加延迟和成本。

### 7.2 目标

```text
DefaultAgentContextBuilder.build()
  -> queryVector = embeddingService.embed(userInput)  // 一次
  -> memoryFuture = recall(queryVector)
  -> ragFuture = search(queryVector)
  -> 各自过滤、排序、截断
  -> Slot 注入仍保持 TASK_MEMORY / RAG_RECALL 分离
```

### 7.3 线程池与超时

不能直接使用 `CompletableFuture.supplyAsync` 默认 common pool。

建议配置：

```properties
agent.context.retrieval.pool-size=8
agent.context.retrieval.queue-capacity=100
agent.context.retrieval.timeout-ms=800
agent.context.retrieval.memory-timeout-ms=500
agent.context.retrieval.rag-timeout-ms=700
```

规则：

- 独立 bounded executor。
- 一个失败不影响另一路。
- join 必须有 timeout。
- 拒绝/超时降级为空结果或关键词 fallback。
- Trace/MDC/thread context 必须传播。

伪代码：

```java
CompletableFuture<List<MemoryDTO>> memoryFuture = supplyWithTrace(...)
        .completeOnTimeout(List.of(), memoryTimeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> List.of());

CompletableFuture<List<RagSearchResultDTO>> ragFuture = supplyWithTrace(...)
        .completeOnTimeout(List.of(), ragTimeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> List.of());
```

## 8. Schema-driven Context

### 8.1 Slot 类型

建议 Slot：

- PROFILE_SYSTEM
- CONVERSATION_HISTORY
- TASK_MEMORY
- TOOL_CONTEXT
- EXPERIENCE_SKILL
- RAG_RECALL
- CONSTRAINT

### 8.2 SlotSource

```java
interface ContextSlotSource {
    boolean supports(ContextSlotKind kind);
    ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command);
}
```

实现：

- `ProfileSlotSource`
- `HistorySlotSource`
- `MemorySlotSource`
- `ToolSlotSource`
- `ExperienceSlotSource`
- `RagSlotSource`
- `ConstraintSlotSource`

这样 RAG 不需要硬塞进 `DefaultAgentContextBuilder`，只作为一个 SlotSource 接入。

### 8.3 Token 预算

ContextBuilder 负责总预算，SlotSource 负责内容候选。

```text
ContextBudgetPolicy
  -> profileBudget
  -> historyBudget
  -> memoryBudget
  -> ragBudget
  -> toolBudget
  -> experienceBudget
```

Trace 记录：

- `context.slot.compose.slotKind`
- `tokenBudget`
- `usedTokens`
- `truncated`

## 9. 派生索引一致性

### 9.1 状态模型

派生索引状态建议先放 metadata，后续字段化：

```json
{
  "vector_index_status": "PENDING|INDEXED|FAILED|DELETED",
  "vector_indexed_at": "2026-06-08T00:00:00Z",
  "vector_index_error": "timeout",
  "graph_index_status": "PENDING|INDEXED|FAILED|SKIPPED",
  "keyword_index_status": "INDEXED"
}
```

### 9.2 写入顺序

Memory：

```text
1. PostgreSQL 插入 memory，status=ACTIVE，vector_index_status=PENDING
2. 提交事务
3. 异步写 Qdrant / Neo4j / 其他派生索引
4. 成功后更新 metadata/status
5. 失败记录 error，等待重试
```

RAG：

```text
1. PostgreSQL 插入 document/chunks
2. chunk 标记 vector_index_status=PENDING
3. embedding + upsert Qdrant
4. 更新 vector_index_status
5. 失败时仍可走 PostgreSQL fallback
```

删除/禁用：

```text
1. 先更新 PostgreSQL status
2. 再删除 Qdrant point / Neo4j 节点边
3. 删除失败记录 Trace 和重试任务
4. 查询以 PostgreSQL status 为准
```

### 9.3 Reindex / Backfill

后台能力：

- 按 source_type 重建：memory / rag。
- 按 scope 重建：tenant/application/profile/document。
- 按 embedding_model 重建：切换模型时新建 collection 或清空重建。
- 按失败状态重试：FAILED/PENDING。

## 10. Trace 设计

### 10.1 Span 列表

```text
memory.recall
  -> memory.embedding
  -> memory.vector.search
  -> memory.keyword.search
  -> memory.graph.search

memory.write
memory.consolidate

rag.ingest
  -> rag.embedding
  -> rag.vector.upsert

rag.search
  -> rag.embedding
  -> rag.vector.search
  -> rag.keyword.tsvector
  -> rag.graph.search
       -> rag.graph.ner
       -> rag.graph.bfs

rag.compose
context.slot.compose
```

### 10.2 Attributes

Memory：

- `filter.categories`
- `topK`
- `candidateCount`
- `returnedCount`
- `fallbackMode`
- `memoryStrategy`
- `deduped`
- `merged`

RAG：

- `queryHash`
- `topK`
- `returnedCount`
- `vectorStore`
- `fallbackMode`
- `documentId`
- `chunkCount`
- `ragTokens`
- `truncated`

安全规则：所有 attributes 必须脱敏，不保存完整用户问题、完整 chunk 原文或敏感数据。

## 11. 实施路线

### Stage 1：Memory v2 稳定化

目标：长期记忆可写、可召回、可管理、可禁用。

任务：

- Memory category/filter 稳定。
- 手动管理接口：list/update/disable。
- 写入准入和 Memory Strategy 显式校验。
- Trace: `memory.recall` / `memory.write`。

验收：

- READ_WRITE 写入偏好，下一轮可召回。
- SESSION_ONLY 不产生长期记忆 span。
- 禁用记忆后不再召回。

### Stage 2：Schema-driven Context

目标：用 Slot 统一注入上下文。

任务：

- `ContextSlotKind`
- `ContextSlot`
- `ContextSlotSource`
- `ContextSchemaAssembler`
- `DefaultAgentContextBuilder` 改为 Slot Orchestrator。
- `ContextBudgetSnapshotDTO` 增加 slot 明细。

验收：

- memory、tools、experience、rag 可独立预算。
- Trace 能看到 slot 预算和截断。

### Stage 3：RAG MVP

目标：知识文档入库、chunk、检索、注入。

任务：

- `knowledge_documents` / `knowledge_chunks`。
- TextSplitter。
- RagEngine ingest/search/delete。
- MockEmbedding/MockVectorStore。
- RagSlotSource。
- Web 管理入口。

验收：

- 插入测试知识文档。
- 管理接口能检索。
- Chat 能触发 RAG 注入。
- 删除文档后不再召回。

### Stage 4：Qdrant 接入

目标：真实向量索引可用，但保留 fallback。

任务：

- docker-compose.dev.yml 增加 Qdrant。
- QdrantVectorStore。
- collection 懒初始化。
- payload filter：tenant/application/user/profile/document/source_type。
- OpenAI-compatible embedding 骨架。
- RAG/Memory vector trace。

验收：

- Qdrant collection 可访问。
- `rag.vector.search.attributes.vectorStore=QdrantVectorStore`。
- RAG chunk 写入 Qdrant，payload.vector_id 保留业务 ID。
- 删除文档后 PostgreSQL 和 Qdrant 都清理。
- Qdrant 关闭后 PG fallback 可用。

### Stage 4.5：Embedding 复用与并行检索

目标：同一用户输入只 embedding 一次，Memory/RAG 并行召回。

任务：

- Context retrieval executor。
- `MemoryRecallService.recall(..., queryVector)`。
- `RagSearchService.search(..., queryVector)`。
- Trace context propagation。
- 超时和降级。

验收：

- Memory/RAG 均启用时 embedding 调用次数从 2 降为 1。
- 一路超时不影响另一路。
- P95 latency 不劣化。

### Stage 5A：PG tsvector + RRF

目标：替代 LIKE fallback，形成向量 + 倒排双路融合。

任务：

- `knowledge_chunks.search_vector` + GIN。
- `memories.search_vector` + GIN。
- `PgTsvectorKeywordSearch`。
- RRF 融合。
- Trace: `rag.keyword.tsvector` / `memory.keyword.tsvector`。

验收：

- 中文/英文关键词检索优于 LIKE。
- 向量失败时 tsvector 可独立返回。
- RRF 去重排序正确。

### Stage 5B：Neo4j 图谱增强

目标：补充关系型问题召回，默认关闭。

任务：

- docker-compose.dev.yml 增加 Neo4j。
- 文档 ingest 后异步 LLM 提取实体关系。
- Neo4jGraphSearchService。
- 图谱结果进入 RRF。
- 降级为空结果。

验收：

- 关系型查询能命中相关 chunk。
- Neo4j 不可用时主链路不失败。

### Stage 5C：记忆生命周期增强

目标：减少记忆膨胀和冲突。

任务：

- Utility Score。
- 写前语义去重。
- 冲突记忆 supersede。
- Consolidation 定时任务。
- 事件级情景记忆。

验收：

- 重复偏好不新增。
- 新偏好覆盖旧偏好。
- pinned 记忆不被自动淘汰。
- 低价值过期记忆归档。

## 12. 测试与验收

### 12.1 自动化测试

重点测试类：

- `PreferenceExtractorTest`
- `DefaultAgentRuntimeServiceTest`
- `DefaultMemoryManagementServiceTest`
- `DefaultMemoryRecallServiceTest`
- `DefaultPostgresRagEngineTest`
- `RagSearchServiceConfigurationTest`
- `RagSlotSourceTest`
- `RagKnowledgeControllerTest`
- `MemoryControllerTest`
- `WebApplicationTest`

### 12.2 HTTP 冒烟

覆盖：

1. 登录并选择 READ_WRITE Profile。
2. 首轮写入偏好。
3. 管理接口查询长期记忆。
4. 第二轮召回记忆。
5. 查询 Memory Trace Span。
6. 插入测试知识文档。
7. 管理接口检索知识库。
8. Chat 触发 RAG 注入。
9. 查询 RAG Trace Span。
10. 删除测试文档。
11. Qdrant 联调：真实 collection、point、payload.vector_id、删除清理、fallback。

### 12.3 新增护栏测试

- 敏感信息不自动写入长期记忆。
- 用户说“不要记住”后不写入。
- 用户说“忘掉 X”后相关记忆被软禁用。
- READ_ONLY 不写入。
- SESSION_ONLY 不读写长期记忆。
- 冲突偏好只注入最新有效一条。
- 禁用/删除记忆后向量索引失败也不影响 PG 事实状态。
- Memory/RAG 并行检索一路超时不影响另一路。
- HyDE/MQE 默认关闭。

### 12.4 检索评测集

准备 20-50 条固定 query：

- 明确偏好召回。
- 模糊指代召回。
- RAG 精确事实。
- 中文同义词。
- 冲突偏好。
- 无答案问题。

指标：

- Hit@5
- MRR
- citation accuracy
- no-answer precision
- avg latency / P95 latency
- fallback rate
- cost per query

策略上线门槛：

- Qdrant semantic recall：可默认开启。
- PG tsvector：完成后可默认开启。
- HyDE：默认关闭，评测证明提升后再开启。
- MQE：默认关闭，只给复杂检索 Profile 使用。
- Neo4j：默认关闭，先用于管理端实验。

## 13. LangGraph4j 多 Agent 复用方式

这套 Memory/RAG/Context Slot 能力可以作为 LangGraph4j 多 Agent 的底座，但多 Agent 不能让每个角色直接读写长期记忆。推荐接入方式：

```text
LangGraphTeamRuntimeService
  -> TeamRunContextBuilder
  -> core.context 组装 Team 专用 Slot
       -> MemorySlotSource
       -> RagSlotSource
       -> ExperienceSlotSource
  -> LangGraph4j StateGraph
       Planner / Executor / Reviewer / Reflector
  -> ReflectNode 汇总经验
  -> MemoryWriteService 准入/脱敏/去重/冲突处理后写入
```

角色边界：

| 角色 | 读 Memory | 写 Memory | 读 RAG | 说明 |
|---|---:|---:|---:|---|
| Planner | 可读摘要级/偏好级/项目事实 | 否 | 可读 RAG 摘要 | 用于任务拆分和依赖编排 |
| Executor | 可读当前任务相关记忆 | 否 | 可读 RAG，可调用工具 | 执行子任务，产生候选经验 |
| Reviewer | 可读计划、结果和必要上下文 | 否 | 可读引用来源 | 只审查，不修改记忆 |
| Reflector | 可读完整执行轨迹 | 可写候选，经 MemoryWriteService 准入 | 可读 | 唯一负责长期经验沉淀 |
| Orchestrator | 可读必要上下文 | 控制写入时机 | 可读 | 收口最终答案和写入策略 |

关键约束：

- Planner、Executor、Reviewer 不直接调用 `MemoryWriteService`。
- Reflector 只生成 `MemoryWriteCandidate`，实际写入必须经过准入、脱敏、去重和冲突处理。
- LangGraphTeamState 只保存 memory/rag 快照，不保存数据库 Entity、完整敏感原文或 Spring Bean。
- RAG citation 只能来自 `RagSnippet`，不能来自 `MemorySnippet`。
- Team 模式需要专用 Context Schema：`TEAM_PLANNER`、`TEAM_EXECUTOR`、`TEAM_REVIEWER`、`TEAM_REFLECTION`。

详细设计见：`../单向多Agent优化/03-LangGraph4j多Agent记忆系统接入设计.md`。
## 14. 不做或暂缓

- 不把 Memory 和 RAG 合并成一张表。
- 不让 Memory 成为 citation 来源。
- 不默认开启 HyDE/MQE/Neo4j。
- 不引入 Milvus 替代 Qdrant。
- 不在 MVP 做 Late Chunking。
- 不让外部依赖失败阻断主对话。
- 不把知识库管理接口放进 Gateway；知识库管理走 Web -> Core。

## 15. 关键设计决策

| 决策 | 结论 | 原因 |
|---|---|---|
| 事实源 | PostgreSQL | 事务、管理、删除、回表可靠 |
| 向量库 | Qdrant | 轻量、API 清晰、已有接入 |
| 本地测试 | MockEmbedding + MockVectorStore | 不依赖外网和 API Key |
| Memory/RAG 边界 | 分表、分 collection、分 source_type | 防止用户记忆和知识库互串 |
| Context 注入 | SlotSource | 解耦 memory/rag/tools/experience |
| 混合检索 | RRF | 不依赖不同召回分数归一化 |
| 倒排首版 | PG tsvector | 零新组件，替代 LIKE 全表扫描 |
| 图谱 | Neo4j 可选 | 关系查询强，但不是 MVP 必需 |
| 并行检索 | 独立 bounded executor | 避免 common pool 拖垮主链路 |
| 高级查询增强 | 默认关闭 | 成本和延迟需要评测证明 |

## 16. 原文档归档关系

| 原文档 | 现在定位 |
|---|---|
| `单Agent记忆与RAG设计方案.md` | 基础设计展开材料 |
| `单Agent记忆与RAG融合方案.md` | 融合方案草稿，核心内容已并入本文 |
| `单Agent记忆与RAG混合检索增强方案.md` | Stage 5 混合检索详细材料 |
| `单Agent记忆系统业界对照与优化建议.md` | 业界对照和优先级来源 |
| `单Agent记忆与RAG-Embedding复用与并行优化方案.md` | Stage 4.5 性能优化详细材料 |
| `单Agent记忆与RAG实现护栏补充方案.md` | 工程护栏详细材料 |
| `单Agent记忆与RAG后端冒烟测试指南.md` | 测试操作手册 |
| `记忆系统工作流程.drawio` | 可视化流程图 |

后续开发和评审优先阅读本文；需要具体 SQL、代码片段、冒烟命令时，再跳到对应展开材料。
