# 单 Agent 记忆系统与 RAG 设计方案

> 本文用于承接单 Agent 优化路线中的 Step 12：Memory v2 + RAG / VectorStore。当前先定设计和阶段边界，后续再按小步实现。

## 1. 参考资料与取舍结论

### 1.1 已参考资料

- 本地参考项目：`D:\study\蓝山最终考核项目\AGI-saber-main`
  - 重点文件：`internal/memory/memory.go`、`internal/memory/graph_memory.go`、`internal/rag/rag.go`、`internal/rag/hybrid.go`、`internal/runtime/schema.go`、`internal/runtime/assembler.go`。
- 本地参考项目：`D:\study\蓝山最终考核项目\AGI-saber`
  - 重点文件：`service/memory/*`、`service/rag/*`、`infrastructure/InfrastructureService.java`。
- 小林 Coding 资料：
  - `https://xiaolinnote.com/ai/agent/8_memory.html`
  - `https://xiaolinnote.com/ai/agent/9_memory_storage.html`
  - `https://xiaolinnote.com/ai/agent/12_memcompress.html`
  - `https://xiaolinnote.com/ai/rag/rag_info.html`
- 语雀 AGI-saber 协作链接：
  - 当前命令行抓取不稳定，且该类协作链接可能需要登录或授权；本文先不依赖它作为唯一依据，后续如有导出文档或截图再补充。
- 本地架构截图：`C:\Users\WYZ\Pictures\AGI项目`
  - 重点参考其中的 RAG 三路检索时序、三层记忆时序、Preference 提取、记忆写入去重、Consolidation 三阶段和基础设施降级路径。

### 1.2 不能原样照搬 AGI-saber 的原因

AGI-saber 的记忆和 RAG 设计方向值得借鉴，但实现不能直接搬到 `agent-platform`：

- `AGI-saber` Java 版长期记忆存在内存 List 实现，不适合多租户平台。
- Go 版 RAG 使用 Milvus + Elasticsearch + Neo4j + PostgreSQL 的混合基础设施，当前项目路线是 Qdrant 优先，且外部服务必须可 Mock、可降级。
- AGI-saber 没有当前项目严格的 `tenant_id / application_id / user_id / profile_id` 隔离边界。
- AGI-saber 没有 `web / gateway / core` 分层约束，直接搬会破坏 `CLAUDE.md` 的模块边界。
- `InfrastructureService` 式大杂烩不适合放入当前 `core`，当前项目必须按 `core.memory`、`core.rag`、`core.context`、`core.model` 分包归属。

### 1.3 应该借鉴的能力切片

可以平台化吸收这些能力：

- 记忆分层：短期窗口、长期记忆、偏好记忆。
- 记忆元数据：`category`、`tags`、`importance`、`slotHint`、`lastAccessedAt`。
- 召回过滤：按分类、标签、最小分、TopK、时间窗口过滤。
- 记忆压缩：滑动窗口、旧历史摘要、重要性衰减、去重、合并、过期淘汰。
- Schema-driven Context：把 profile、history、memory、tools、experience、rag 分成不同 Slot，并各自受 token budget 控制。
- RAG 流程：文档入库、分块、embedding、向量召回、引用注入、Trace 可观测。
- 混合检索增强：关键词检索、向量检索、RRF 融合、知识图谱扩展作为后续增强，而不是首版强依赖。

### 1.4 架构截图补充结论

截图中的能力可以拆成“当前可落地”和“后续增强”两类。

当前可落地：

- RAG 查询时序：query embedding -> 检索候选 -> RRF 或排序 -> PostgreSQL 回表取 chunk 原文 -> LLM 合成答案。
- 记忆读取时序：短期窗口 + 长期记忆召回 + 偏好上下文一起进入模型上下文。
- 记忆写入时序：对话结束后写短期记忆，同时对高价值用户消息写长期记忆。
- 去重策略：新记忆与已有记忆计算相似度，超过去重阈值时更新已有条目的重要性和访问时间，未命中时新增。
- 降级策略：embedding 不可用时，用 TF/关键词召回兜底；RAG 无检索结果时不注入空上下文。
- Preference 双通道：LLM NER 提取偏好 + 规则兜底立即生效。
- Consolidation 三阶段：重要性衰减、去重合并、过期淘汰。

后续增强：

- Milvus / Elasticsearch / Neo4j 三路并行检索。
- RRF 融合排序。
- GraphMemory 图扩展召回。
- Kafka 事件流。
- 稳定执行与快照恢复。

对当前 `agent-platform` 的取舍：

- Milvus 用 Qdrant 替代。
- Elasticsearch 首版用 PostgreSQL keyword fallback 替代。
- Neo4j / RRF / GraphMemory 放 Stage 5。
- Kafka 不进入单 Agent Memory/RAG MVP。
- Router 只作为能力分流概念参考，不能替代 `core.context` 的上下文装配职责。

## 2. 当前项目现状

### 2.1 已完成

当前 `agent-platform` 已完成 Memory v2 第一层：

- `memories` 已扩展：
  - `memory_category`
  - `tags`
  - `importance`
  - `slot_hint`
  - `metadata`
- `MemoryRecallFilter` 已支持：
  - `categories`
  - `requireTags`
  - `minScore`
  - `topK`
  - `maxAge`
- `DefaultMemoryRecallService` 已从纯时间倒序升级为：
  - 关键词粗召回
  - category/tag 过滤
  - importance 加权排序
- `DefaultAgentContextBuilder` 已按 `summary / preference / fact` 过滤长期记忆，并纳入上下文预算。
- `ContextBudgetSnapshotDTO` 已预留 `ragTokens` 字段，但当前仍为 0。

### 2.2 未完成

还缺少以下能力：

- 偏好提取器：从对话中提取用户偏好、事实、长期约束。
- 记忆生命周期：`last_accessed_at`、`access_count`、衰减、去重、合并、过期淘汰。
- 语义召回：embedding 生成、VectorStore 抽象、Qdrant 接入。
- Schema-driven Context：当前仍是手工拼接 blocks，不是正式 Slot 装配。
- RAG：知识文档表、chunk 表、TextSplitter、VectorStore、RagEngine、RagSlotSource 都未开始。
- RAG Trace：`rag.ingest`、`rag.search`、`rag.compose` span 未落地。

## 3. 总体设计目标

本阶段目标不是一次性做完整企业级 RAG，而是把单 Agent 的长期状态和知识增强做成可演示、可扩展、可降级的主链路能力。

核心目标：

- 用户长期记忆和知识库 RAG 分开建模。
- 记忆服务优先服务“这个用户是谁、偏好是什么、以前说过什么”。
- RAG 服务优先服务“外部文档里有什么知识依据”。
- 两者最终都通过 `core.context` 注入 `apiMessages`，不污染持久化对话历史。
- 所有读取和写入都保留 `tenant/application/user/profile` 边界。
- 外部向量库不可用时，主链路仍能通过 PostgreSQL 关键词召回降级运行。

## 4. 推荐架构

### 4.1 包边界

建议新增或扩展以下 core 包：

- `core.memory`
  - 用户长期记忆、偏好、事实、摘要、记忆召回、记忆写入、记忆合并。
- `core.rag`
  - 知识文档、chunk、RAG 入库、RAG 检索、VectorStore 适配。
- `core.embedding`
  - embedding 生成接口，可复用 `core.model` 的 provider 配置，但不要让 RAG 直接访问模型 provider 内部类。
- `core.context`
  - Context Slot、Schema、SlotSource、预算裁剪、注入顺序。
- `core.trace`
  - 记录 memory/rag/context 相关 span。

Gateway 和 Web 不拥有 memory/rag 表，也不组装 RAG 上下文。

### 4.2 数据边界

Memory 与 RAG 不共用业务表：

- `memories`
  - 保存用户长期状态。
  - 归属 `core.memory`。
  - 典型内容：偏好、事实、摘要、工具失败经验、长期约束。
- `knowledge_documents`
  - 保存知识文档元数据。
  - 归属 `core.rag`。
  - 典型内容：文件名、来源、作用域、状态、hash。
- `knowledge_chunks`
  - 保存 chunk 原文、chunk index、token 估算、metadata、向量引用。
  - 归属 `core.rag`。
  - embedding 可存 Qdrant；PostgreSQL 保存原文和可审计元数据。

## 5. Memory v2 详细设计

### 5.1 记忆分类

建议统一使用以下 `memory_category`：

- `summary`：对话摘要。
- `preference`：用户偏好，例如输出风格、技术栈偏好、常用城市。
- `fact`：稳定事实，例如用户项目名、身份、环境约束。
- `episodic`：一次性事件或历史片段。
- `tool_failure`：工具调用失败经验。
- `policy`：长期约束或安全策略。
- `general`：无法归类的普通记忆。

`memory_type` 保留兼容，后续以 `memory_category` 作为主要召回过滤字段。

### 5.2 建议新增字段

在已扩展字段基础上，后续建议继续增加：

- `last_accessed_at TIMESTAMP NULL`
- `access_count INT DEFAULT 0`
- `confidence NUMERIC(4,3) DEFAULT 0.8`
- `expires_at TIMESTAMP NULL`

说明：

- `importance` 表示重要性。
- `confidence` 表示模型或规则提取的可信度。
- `last_accessed_at` 和 `access_count` 用于召回热度与压缩保护。
- `expires_at` 用于临时偏好或短期事实自动过期。

### 5.3 写入策略

记忆写入分三类：

- 对话结束写摘要：已有能力继续保留，但需要分类为 `summary`。
- 用户明确表达偏好或事实时写入：由 `PreferenceExtractor` / `MemoryFactExtractor` 识别。
- 工具失败或高价值经验写入：由 Runtime 在工具失败、降级、用户纠正时写入 `tool_failure` 或 `policy`。

MVP 不建议一开始用 LLM 提取所有记忆。推荐顺序：

1. 规则版提取器：覆盖“我喜欢/我不喜欢/以后都/默认用/我的城市是”等明显表达。
2. LLM 提取器：后续开启，输出 JSON Schema，失败则跳过，不阻塞主链路。
3. 人工编辑：前端允许用户查看、禁用、删除、改写记忆。

建议写入时序：

```text
模型生成最终回复
  -> 保存 assistant message
  -> 写入短期窗口或 conversation_messages
  -> 判断用户消息/模型回复是否值得长期记忆
  -> PreferenceExtractor 规则提取偏好
  -> 可选 LLM extractor 提取 preference/fact/policy
  -> MemoryWriteService.record
  -> 如 embedding 可用：生成 memory embedding
  -> 与同 tenant/user/app/profile 下候选记忆比对相似度
  -> sim >= dedupThreshold：更新已有条目的 importance / last_accessed_at / access_count / tags
  -> sim < dedupThreshold：新增 memory
  -> 记录 memory.write span
```

首版没有 embedding 时，去重相似度可继续使用关键词/Jaccard/TF 词袋近似，不能因为 embedding 不可用跳过记忆写入。

### 5.4 召回策略

首版继续保留 PostgreSQL 关键词召回：

```text
候选集 = tenant/user/app/profile/status 过滤
       + query keywords LIKE 粗筛
       + category/tag/maxAge 过滤

排序分 = keywordScore * 0.6
       + importance * 0.25
       + freshness/accessHeat * 0.15
```

后续接 VectorStore 后：

```text
语义分 = cosine(queryEmbedding, memoryEmbedding)
综合分 = semanticScore * 0.65
       + keywordScore * 0.15
       + importance * 0.15
       + freshness/accessHeat * 0.05
```

如果 embedding 或 Qdrant 不可用，直接回退 PostgreSQL 关键词召回。

建议读取时序：

```text
收到用户输入
  -> 短期历史由 conversation_messages 提供最近窗口
  -> MemoryRecallService.recall(filter)
  -> 如 embedding 可用：Embed(query)
  -> 语义召回并按 score >= threshold 过滤
  -> 如 embedding 不可用：关键词 / TF 召回并按 threshold 过滤
  -> 更新命中 memory 的 last_accessed_at / access_count
  -> MemorySlotSource 按 tokenBudget 裁剪
  -> 注入 preference / summary / fact / task_memory Slot
  -> 记录 memory.recall span
```

召回结果必须过滤低分噪声。截图中的 `score < 0.4` 不注入是合理设计；当前项目可以把默认阈值作为 `MemoryRecallFilter.minScore` 或策略配置。

### 5.5 压缩与合并

参考 AGI-saber 的 `ConsolidationConfig`，但实现必须数据库化：

- `similarityThreshold = 0.80`：相似但不重复，合并。
- `dedupThreshold = 0.95`：高度重复，保留重要性更高的条目。
- `ttlDays = 30`：低重要性临时记忆过期。
- `decayRate = 0.995`：重要性按时间缓慢衰减。
- `minImportance = 0.3`：低于该值且过期可淘汰。
- `triggerInterval = 5`：写入若干条后触发一次轻量合并。

合并不能删除用户显式偏好和高置信事实，除非它们被新事实覆盖。

建议 Consolidation 时序：

```text
NeedConsolidation?
  -> Phase 1：重要性衰减
       importance = importance * decayRate ^ days
  -> Phase 2：去重 + 合并
       sim >= dedupThreshold：保留 importance 更高的条目，删除另一条
       sim >= similarityThreshold：合并内容，更新主条目，删除被合并条目
  -> Phase 3：过期淘汰
       days > ttlDays && importance < minImportance：删除或 DISABLED
  -> 重建关键词/向量索引引用
  -> 同步 PostgreSQL 更新和删除结果
  -> 记录 memory.consolidate span
```

图增强记忆如果后续接 Neo4j，需要在合并/删除时同步图节点和边；当前 Stage 1 只记录 metadata，不引入 Neo4j。

## 6. Schema-driven Context 设计

当前 `DefaultAgentContextBuilder` 已经能做预算裁剪，但还是手工拼接。后续建议引入 Slot：

### 6.1 Slot 类型

- `PROFILE`
- `HISTORY`
- `TASK_MEMORY`
- `PREFERENCE`
- `TOOL_STATE`
- `EXPERIENCE`
- `RAG_RECALL`
- `CONSTRAINTS`

### 6.2 Schema 预设

- `ChatSchema`
  - profile + history + preference + summary。
- `ToolSchema`
  - profile + tools + tool_state + task_memory。
- `ReactSchema`
  - profile + history + tools + memory + experience。
- `RagSchema`
  - profile + history + rag_recall + constraints。

### 6.3 SlotSource

建议新增接口：

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

这样后续 RAG 不需要硬塞进 `DefaultAgentContextBuilder`，只要作为一个 SlotSource 接入。

## 7. RAG MVP 设计

### 7.1 RAG 与 Memory 的区别

| 项目 | Memory | RAG |
|---|---|---|
| 解决问题 | 记住用户长期状态 | 从知识文档找依据 |
| 数据来源 | 对话、偏好、用户纠正、工具经验 | 上传文档、URL、手工知识库 |
| 主表 | `memories` | `knowledge_documents` / `knowledge_chunks` |
| 召回依据 | 用户 + Profile + 当前 query | 文档作用域 + query |
| 注入方式 | Long-term memories / preferences | Retrieved references / citations |
| 生命周期 | 可被压缩、衰减、合并 | 文档版本化、删除、重建索引 |

### 7.2 数据表建议

`knowledge_documents`：

- `id`
- `tenant_id`
- `application_id`
- `profile_id NULL`
- `owner_user_id`
- `title`
- `source_type`
- `source_uri`
- `doc_hash`
- `status`
- `metadata jsonb`
- `created_at`
- `updated_at`

`knowledge_chunks`：

- `id`
- `tenant_id`
- `application_id`
- `document_id`
- `chunk_index`
- `content`
- `content_hash`
- `token_count`
- `vector_id`
- `metadata jsonb`
- `status`
- `created_at`
- `updated_at`

### 7.3 服务设计

建议新增：

- `TextSplitter`
  - 输入文档原文，输出 chunk。
  - 参数：`chunkSize`、`overlap`。
  - 中文场景优先按段落和句子边界切分，兜底按字符窗口切分。
- `EmbeddingService`
  - 输入文本，输出向量。
  - Mock 模式返回稳定伪向量，保证测试不依赖外网。
- `VectorStore`
  - `upsert`
  - `search`
  - `deleteByDocument`
  - `available`
- `MockVectorStore`
  - 用于本地开发和测试。
- `QdrantVectorStore`
  - 默认真实向量库实现。
- `RagEngine`
  - `ingest`
  - `search`
  - `delete`
- `RagSlotSource`
  - 把 RAG 检索结果压缩后注入 Context Slot。

### 7.4 文档切割策略

RAG 的文档切割不采用单一策略。首版采用“固定窗口兜底 + 结构化文档优先语义切割”的混合方案，后续再增强父子切割和代码 AST 切割。

#### 7.4.1 默认策略：固定大小 + 重叠

适用场景：

- 纯文本。
- 无明显标题结构的资料。
- 从网页、PDF、复制文本中抽出的普通段落。

默认参数：

- `chunkSize = 800 tokens`
- `overlap = 120 tokens`
- `minChunkTokens = 120`
- `maxChunkTokens = 1_000`

切割示例：

```text
chunk 0: 0 - 800
chunk 1: 680 - 1480
chunk 2: 1360 - 2160
chunk 3: 2040 - 2840
```

保留 overlap 的原因是避免答案刚好跨 chunk 边界时丢失上下文，例如定义在上一段、解释在下一段。

#### 7.4.2 结构化文档：标题层级 + 语义边界

适用场景：

- Markdown 文档。
- HTML 文档。
- 有清晰标题、章节、列表结构的资料。

处理顺序：

1. 先按标题层级切 section，例如 Markdown 的 `# / ## / ###`，HTML 的 `h1 / h2 / h3`。
2. section 不超过 `maxChunkTokens` 时，整体作为一个 chunk。
3. section 超长时，优先按段落切。
4. 段落仍超长时，按句子切。
5. 句子仍超长时，回退固定窗口切割。

每个 chunk 必须保存结构 metadata：

- `documentTitle`
- `headingPath`
- `sectionTitle`
- `chunkIndex`
- `tokenCount`
- `contentHash`
- `sourceUri`
- `pageNo`，如果来源能提供页码。

这样召回结果不只是“某段文本”，还能告诉模型“这段来自哪篇文档、哪个章节”，方便生成引用和减少断章取义。

#### 7.4.3 父子切割：Stage 4/5 增强

父子切割用于兼顾检索精度和上下文完整性，不作为 Stage 3 MVP 的强依赖。

建议参数：

- `parentChunkSize = 2_000 ~ 3_000 tokens`
- `childChunkSize = 300 ~ 600 tokens`
- `childOverlap = 80 ~ 120 tokens`

检索方式：

```text
文档 -> parent chunks
parent chunk -> child chunks
child chunk 生成 embedding
query 命中 child chunk
回表加载 child 所属 parent chunk 或相邻 chunks
按 tokenBudget 注入上下文
```

优势：

- 向量检索用小 chunk，语义更精确。
- 注入模型时可回填 parent，上下文更完整。

代价：

- chunk 数量增加。
- 存储和索引构建更复杂。
- 需要维护 `parent_chunk_id` 或 `parent_index`。

#### 7.4.4 代码文档切割：后续增强

代码文件不适合直接按自然语言段落切。后续如要支持代码知识库，优先按语言结构切：

- Java：class / method / interface。
- JavaScript / TypeScript：function / class / export。
- Python：class / def。

Stage 3 暂不引入 AST 解析。首版只做简单兜底：

- 按文件保存 document。
- 按函数或类的正则边界粗切。
- 无法识别结构时回退固定窗口。

#### 7.4.5 暂不采用 Late Chunking

Late Chunking 可以让 chunk 向量保留全文上下文，但依赖长上下文 embedding 模型和更复杂的 embedding 流程。当前考核项目优先保证可演示、可降级、可测试，因此暂不作为 MVP 能力。

Late Chunking 放到 Stage 5 后作为可选增强评估。

#### 7.4.6 选型表

| 策略 | 适用文档类型 | 优点 | 缺点 | 当前阶段 |
|---|---|---|---|---|
| 固定大小 + 重叠 | 纯文本、无明显结构 | 实现简单，chunk 大小可控 | 可能在语义中间截断 | Stage 3 必做 |
| 语义边界切割 | 段落分明的文章 | 语义完整，召回质量更好 | chunk 大小不完全均匀 | Stage 3 必做 |
| 标题层级切割 | Markdown、HTML | 保留章节结构和 metadata | 依赖文档结构清晰 | Stage 3 必做 |
| 父子切割 | 高质量知识库 | 检索精准，上下文完整 | 存储和索引复杂度更高 | Stage 4/5 |
| 代码结构切割 | 代码文件 | 保留代码逻辑完整性 | 需要 AST 或语言规则 | Stage 5 |
| Late Chunking | 各类长文档 | chunk 向量保留全文上下文 | 依赖长上下文 embedding | Stage 5 可选 |

实际落地选择：

```text
Stage 3:
  固定大小 + overlap 兜底
  Markdown/HTML/段落文档优先语义边界切割

Stage 4:
  Qdrant 语义召回稳定后，加入 child chunk 检索 + parent chunk 回填

Stage 5:
  代码 AST 切割、RRF、Neo4j、Late Chunking 按展示价值选择
```

### 7.5 Embedding 模型选型

RAG 场景只考虑第三代句向量 / 文本向量模型，不使用 Word2Vec、GloVe、BERT 这类早期方案作为主检索 embedding。

选型理由：

- 第一代静态词向量只能表达词级语义，不适合句子、段落和知识 chunk 检索。
- 第二代上下文向量适合理解 token 上下文，但直接做大规模实时检索时通常需要两两拼接或额外处理，成本高。
- 第三代 sentence embedding / bi-encoder 可以让 query 和 chunk 独立编码，提前把 chunk 向量写入向量库，查询时只算 query 向量并做相似度搜索，是 RAG 的主流方案。

当前项目的选型原则：

- 中文知识库优先考虑中文效果好的开源 embedding。
- 本地开发和测试必须有 Mock，不依赖外网和 API Key。
- API 方案作为可选配置，不作为 MVP 硬依赖。
- 不只看公开排行榜，最终要用自己的业务数据跑 Hit@K / Recall@K / MRR 评估。

建议选型表：

| 模型 | 维度 | 是否开源 | 中文效果 | 适用场景 | 项目定位 |
|---|---:|---|---|---|---|
| `MockEmbeddingService` | 768 | 本地伪实现 | 不评价 | 单元测试、本地演示、无外网降级 | Stage 3 默认 |
| `bge-large-zh-v1.5` | 1024 | 是 | 很好 | 中文知识库经典选择 | Stage 4 本地开源候选 |
| `bge-m3` | 1024 | 是 | 好 | 中英混合、多语言、多粒度检索 | Stage 4 多语言候选 |
| `Qwen3-Embedding` | 按模型配置 | 是/开放模型 | 很好 | 中文场景新一代强力选择 | Stage 4/5 候选 |
| `text-embedding-3-small` | 1536，可降维 | 否，API | 一般到可用 | 快速接入、英文为主、API 方案 | Stage 4 API 候选 |
| `text-embedding-3-large` | 3072，可降维 | 否，API | 一般到可用 | 英文高精度、预算充足 | Stage 5 API 候选 |

默认路线：

```text
Stage 3 RAG MVP:
  EmbeddingService = MockEmbeddingService
  vectorDimension = 768
  VectorStore = MockVectorStore
  fallback = PostgreSQL keyword search

Stage 4 本地/开源优先:
  embeddingModel = bge-large-zh-v1.5 或 bge-m3
  vectorDimension = 1024
  vectorStore = Qdrant
  distanceMetric = COSINE

Stage 4 API 快速接入:
  embeddingModel = text-embedding-3-small
  vectorDimension = 1536
  vectorStore = Qdrant
  distanceMetric = COSINE

Stage 5 评估增强:
  在业务数据上比较 bge-large-zh-v1.5 / bge-m3 / Qwen3-Embedding / text-embedding-3-small
  指标使用 Hit@5、Recall@5、MRR、人工答案引用准确率
```

配置上必须把 embedding 模型和聊天模型分开：

```text
chat_model_provider      -> 用于生成回答
chat_model_name

embedding_provider       -> 用于生成 query/chunk 向量
embedding_model_name
embedding_dimension
embedding_normalize
vector_collection
distance_metric
```

Qdrant collection 的向量维度固定。切换 embedding 模型时，如果维度不同，不能直接复用旧 collection，必须：

1. 新建 collection，例如 `rag_chunks_bge_1024`、`rag_chunks_openai_1536`。
2. 或重建旧 collection 的全部 chunk 向量索引。

因此 `knowledge_chunks.vector_id` 和 document metadata 中要记录 embedding 模型版本，避免后续混用不同维度或不同模型生成的向量。

### 7.6 检索流程

```text
用户问题
  -> ContextBuilder 选择 RagSchema 或 ReactSchema 中的 RAG Slot
  -> RagSlotSource 调 RagEngine.search
  -> EmbeddingService 生成 query embedding
  -> VectorStore.search 按 tenant/application/profile 过滤
  -> PostgreSQL 加载 chunk 原文和 metadata
  -> 按 score/topK/tokenBudget 裁剪
  -> 注入 apiMessages
  -> Trace 记录 rag.search / rag.compose
```

截图中的完整三路检索时序可以作为 Stage 5 目标形态：

```text
用户问题
  -> Embed(query)
  -> 并行检索：
       QdrantSearch(queryEmbedding, topK)       // 语义召回，替代 Milvus
       KeywordSearch(query, topK)               // PostgreSQL/后续 ES BM25
       GraphSearch(entities, maxHops=2)         // 后续 Neo4j
  -> RRF 融合排序：
       score = sum(1 / (k + rank_i)) * weight_i
  -> 得到 top chunk ids
  -> PostgreSQL LoadChunksByIds
  -> 组装检索上下文和引用来源
  -> LLM Chat(systemPrompt + 检索上下文 + 用户问题)
  -> 返回 answer + citations
```

Stage 3 的 MVP 不做三路并行，只做：

```text
Embedding 可用：
  Qdrant/MockVectorStore search -> PostgreSQL 回表 -> 注入

Embedding 或 Qdrant 不可用：
  PostgreSQL keyword fallback -> 注入
```

这样可以保留目标架构的扩展点，同时不把首版 RAG 绑死在多个外部服务上。

当前实现进度：

- 已完成 `EmbeddingService` / `VectorStore` 抽象。
- 已完成 `MockEmbeddingService` / `MockVectorStore`，用于本地演示和无外部服务降级。
- 已完成 `DefaultPostgresRagEngine` 的可插拔召回：ingest 后可写入 VectorStore，search 优先走 VectorStore 命中再 PostgreSQL 回表取 chunk 原文，向量不可用或无结果时回退 PostgreSQL keyword fallback。
- 已完成 `QdrantVectorStore` 前两段骨架：通过配置显式启用，支持 collection 懒初始化、upsert/search/deleteByDocument 的 Qdrant HTTP 请求映射、payload filter 和失败降级；默认仍使用 `MockVectorStore`。
- 已完成开发环境 `docker-compose.dev.yml` 的 Qdrant 服务和持久化卷配置。
- 已完成 `OpenAiCompatibleEmbeddingService` 骨架：通过配置显式启用，按 OpenAI-compatible `/embeddings` 请求生成 query/chunk 向量；失败或空输入返回空向量并触发 RAG fallback。
- 已完成 RAG 内部 Trace 透传：`RagSlotSource` 把 `rag.search` span id 传给 `DefaultPostgresRagEngine`，向量召回路径记录 `rag.embedding` 和 `rag.vector.search` 子 span。
- 本地开源 embedding provider、HyDE、RRF 和 citations 前端展示仍未完成。

#### 7.6.1 ANN 向量检索与多路召回

Qdrant 接入后，RAG 的主召回能力使用 ANN 向量检索：

```text
query
  -> EmbeddingService.embed(query)
  -> Qdrant ANN search
  -> topK vector hits
  -> PostgreSQL LoadChunksByIds
  -> score filter
  -> Context Slot 注入
```

ANN 检索用于在大规模 chunk 向量中快速找近似最近邻，适合语义相似召回。它不是精确字符串匹配，因此必须与关键词召回互补。

多路召回路线：

```text
Stage 3:
  PostgreSQL keyword fallback
  MockVectorStore 单路向量召回骨架

Stage 4:
  Qdrant ANN semantic recall
  PostgreSQL keyword recall
  两路结果合并去重

Stage 5:
  Qdrant ANN semantic recall
  BM25 keyword recall
  optional graph recall
  RRF fusion
  optional rerank
```

推荐最终形态：

```text
用户问题
  -> semantic recall: Qdrant ANN
  -> keyword recall: PostgreSQL / Elasticsearch BM25
  -> optional graph recall: Neo4j entity expansion
  -> RRF fusion
  -> rerank / score filter
  -> PostgreSQL 回表取 chunk 原文
  -> RagSlotSource 按 tokenBudget 注入
```

多路召回的互补关系：

- 向量召回擅长语义相近、同义表达、问题改写后的召回。
- 关键词召回擅长专有名词、代码名、错误码、配置项、精确短语。
- 图召回擅长实体关系扩展，例如项目、模块、接口、人物、组织之间的关系。

结果融合首选 RRF：

```text
rrfScore = sum(weight_i / (k + rank_i))
```

其中：

- `rank_i` 是该 chunk 在某一路召回中的排名。
- `weight_i` 是该召回源的权重。
- `k` 默认可取 60，避免第一名权重过度放大。

Stage 4 可以先做简单合并：

```text
mergedScore = semanticScore * 0.7 + keywordScore * 0.3
```

Stage 5 再替换成正式 RRF。

#### 7.6.2 HyDE Query Rewrite

HyDE（Hypothetical Document Embeddings）不作为 MVP 默认能力，作为 Stage 5 可选增强。

流程：

```text
用户问题
  -> LLM 生成一个“假设答案 / 假设文档”
  -> EmbeddingService.embed(hypotheticalDocument)
  -> Qdrant ANN search
  -> PostgreSQL 回表取 chunk 原文
  -> 正常 RAG 注入与回答
```

适用场景：

- 用户问题很短，例如“这个怎么鉴权？”
- 问题很口语化，和知识库文档表达差异较大。
- query 缺少关键词，但可以通过 LLM 生成更像文档内容的描述。

风险：

- 多一次 LLM 调用，增加延迟和成本。
- 假设文档可能跑偏，导致召回偏离真实问题。
- 对中文业务知识库是否提升，需要用业务数据评估，不能默认开启。

配置建议：

```text
rag.queryRewriteMode = NONE | HYDE
rag.hydeModelConfigId = optional
rag.hydeMaxTokens = 256
rag.hydeTimeoutMs = 3000
```

Trace 必须记录：

```text
rag.query_rewrite
  mode = HYDE
  inputQueryHash
  hypotheticalDocHash
  modelName
  latencyMs
  status
```

HyDE 输出不能直接展示给用户，也不能写入知识库；它只是一次检索用的临时 query rewrite。

落地顺序：

```text
Stage 3:
  不做 HyDE

Stage 4:
  普通 query embedding + Qdrant ANN

Stage 5:
  在业务测试集上比较 NONE vs HYDE
  指标使用 Hit@5、Recall@5、MRR、引用准确率、平均延迟
  只有收益明确时才允许按 Profile 开启
```

### 7.7 入库流程

```text
上传或录入知识文档
  -> RagEngine.ingest
  -> 计算 docHash，去重或创建新版本
  -> TextSplitter 分块
  -> PostgreSQL 保存 document/chunks
  -> EmbeddingService 生成 chunk embedding
  -> VectorStore.upsert
  -> Trace 记录 rag.ingest
```

入库时必须保证 PostgreSQL 是可审计事实源：

- Qdrant 只保存 embedding 和检索 payload，不保存唯一业务真相。
- chunk 原文、document 作用域、状态、hash、版本都在 PostgreSQL。
- 删除文档时先更新 PostgreSQL 状态，再删除向量索引；向量删除失败时记录 Trace 和重试任务，不影响文档状态查询。

### 7.8 降级策略

- Qdrant 不可用：RAG search 降级 PostgreSQL keyword search。
- Embedding 失败：跳过向量召回，保留关键词召回。
- RAG 无结果：不注入空标题，不告诉模型“有知识库但为空”，避免 prompt 噪声。
- RAG 入库失败：document 标记 `FAILED`，chunk 不进入可检索状态。

降级路径应在 Trace 中明确展示：

- `fallbackMode = NONE`：正常向量召回。
- `fallbackMode = KEYWORD`：向量不可用，使用关键词召回。
- `fallbackMode = EMPTY`：无可用结果，不注入 RAG Slot。
- `fallbackMode = DISABLED`：Profile 或用户未启用知识库。

### 7.9 Web 手工联调入口

RAG 知识文档的录入、检索和删除属于管理/配置/查询类请求，应走 `Web -> Core`，不经过 Gateway。Gateway 只负责 AI 执行治理链，不能为了知识库管理接口引入额外的 Trace/Quota/SSE 适配复杂度。

当前后端已提供最小联调接口：

- `POST /api/rag/documents`：录入知识文档，Web 从当前 JWT 用户注入 `tenantId/userId`，请求体只传 `applicationId/profileId/title/source/content/chunk` 等业务参数。
- `GET /api/rag/search`：按 `applicationId/profileId/query/topK` 手工检索 RAG 结果，用于验证 chunk、embedding、Qdrant/PG fallback 是否生效。
- `DELETE /api/rag/documents/{documentId}`：按 scope 删除知识文档。

这些接口不直接暴露 `core.rag.mapper/entity`，只依赖 `RagEngine` API；后续前端知识库页面也应调用 Web 接口，而不是绕到 Gateway 或直接访问 Core 内部表结构。

当前前端已在 Tools 工作台增加 `知识库 RAG` Tab，提供最小手工联调闭环：

- 左侧录入知识文档，支持 application/profile scope、source、chunkTokenBudget 和 overlapTokens。
- 右侧按 query/topK 手工检索召回 chunk，展示 score、documentId、chunkId、sourceUri 和 chunk 摘要。
- 命中结果可触发文档删除，用于清理测试数据。

## 8. Trace 设计

Memory/RAG 必须在 Trace 工作台可见，否则后续调试很困难。

建议新增 Span：

- `memory.recall`
  - attributes：`filter.categories`、`topK`、`candidateCount`、`returnedCount`、`fallbackMode`。
- `memory.write`
  - attributes：`category`、`importance`、`deduped`、`merged`。
- `memory.consolidate`
  - attributes：`dedupedCount`、`mergedCount`、`expiredCount`。
- `rag.ingest`
  - attributes：`documentId`、`chunkCount`、`vectorStore`、`status`。
- `rag.search`
  - attributes：`queryHash`、`topK`、`returnedCount`、`vectorStoreAvailable`、`fallbackMode`。
- `rag.compose`
  - attributes：`ragTokens`、`chunkCount`、`truncated`。
- `context.slot.compose`
  - attributes：`slotKind`、`tokenBudget`、`usedTokens`。

所有 attributes 必须脱敏，不保存完整用户问题、完整 chunk 原文或敏感数据。

## 9. 分阶段落地顺序

### Stage 1：Memory v2 稳定化

目标：不接外部向量库，先把记忆质量和生命周期做稳。

实现内容：

- 新增 `last_accessed_at / access_count / confidence / expires_at`。
- 新增 `MemoryConsolidationConfig`。
- 新增 `PreferenceExtractor` 规则版。
- 写入策略支持 `summary/preference/fact/tool_failure/policy/general`。
- `memory.recall`、`memory.write`、`memory.consolidate` span。
- 前端记忆列表可查看分类、重要性、来源、状态。

### Stage 2：Schema-driven Context

目标：让 memory、experience、tools、rag 后续都通过统一 Slot 注入。

实现内容：

- `ContextSlotKind`
- `ContextSlot`
- `ContextSlotFilter`
- `ContextSchema`
- `ContextSlotSource`
- 改造 `DefaultAgentContextBuilder` 为 Slot Orchestrator。
- `ContextBudgetSnapshotDTO` 增加 slot 明细。

### Stage 3：RAG MVP

目标：先做可演示、可降级的知识库问答。

实现内容：

- `knowledge_documents`
- `knowledge_chunks`
- `TextSplitter`
- `EmbeddingService` / `MockEmbeddingService`
- `VectorStore` / `MockVectorStore`
- PostgreSQL keyword fallback。
- `RagEngine.ingest/search/delete`
- `RagSlotSource`
- Trace span：`rag.ingest/search/compose`

### Stage 4：Qdrant 接入

目标：把 Mock/PG fallback 换成真实语义召回。

实现内容：

- Qdrant Docker Compose 配置。已完成开发环境配置：`qdrant/qdrant:v1.12.6`，端口 `6333/6334`，独立持久化卷。
- `QdrantVectorStore`。已完成前两段骨架：请求映射、payload filter、配置切换、collection 懒初始化和失败降级。
- collection 初始化。已完成懒初始化：操作前先查 `/collections/{name}/exists`，不存在再 `PUT /collections/{name}` 创建，失败时本次操作降级。
- payload filter：tenant/application/ownerUser/profile/document。已完成第一段。
- embedding provider 配置。已完成 API 快速接入骨架：`agent.rag.embedding-provider=openai-compatible` 显式启用，默认仍使用 `MockEmbeddingService`。
- 失败降级到 PostgreSQL keyword fallback。已完成第一段：Qdrant 调用失败时 VectorStore 返回空结果，PG-backed RAG 继续走 keyword fallback。
- RAG Trace 子步骤。已完成第一段：`rag.search` 下挂 `rag.embedding`、`rag.vector.search`，attributes 只记录脱敏摘要和数量信息。

### Stage 5：Hybrid 与 Graph 增强

目标：作为高分项，不阻塞主链路。

实现内容：

- BM25 关键词检索增强。
- RRF 融合。
- 可选 Neo4j knowledge graph。
- 可选 GraphMemory。
- 中心度保护和图扩展召回。

## 10. 本阶段不做的内容

暂不实现：

- 一开始就上 Milvus + ES + Neo4j 全套混合 RAG。
- 把 AGI-saber 的 `InfrastructureService` 搬进项目。
- 让 Gateway 直接查询 memory/rag 表。
- 把 RAG chunk 原文写入 `conversation_messages`。
- 让 Memory 和 RAG 共用同一张表。
- 用 LLM 无约束抽取所有记忆。
- 用户不可控的自动长期记忆无限写入。

## 11. 下一步建议

当前已经推进到 Stage 4 的一部分：RAG 与 Memory 都可以通过统一 `EmbeddingService` / `VectorStore` 走语义召回骨架，但两者仍保持业务数据隔离。

已补充完成：

- 长期记忆写入时，如果 embedding 和 VectorStore 可用，会把 memory 内容写入向量索引。
- 长期记忆召回时，优先使用 query embedding + VectorStore 语义召回，再回表读取 `memories`，无结果或失败时回退 PostgreSQL 关键词召回。
- VectorStore payload 增加 `source_type`，RAG 默认使用 `rag`，长期记忆使用 `memory`，避免知识库 chunk 和用户记忆互相串召回。
- `memory.recall` 下增加 `memory.embedding` 和 `memory.vector.search` 子 span，Trace 失败不影响主链路。
- PostgreSQL 仍是长期记忆事实源；Qdrant / MockVectorStore 只是可替换的向量索引层。
- `VectorStore.deleteByDocument` 已支持 source-aware 删除：旧签名默认删除 `source_type=rag`，长期记忆合并/过期删除时使用 `source_type=memory` 清理向量索引。
- 已补长期记忆手动管理后端闭环：`MemoryManagementService` 提供按当前用户作用域 list/update/disable，Web 暴露 `GET /api/memories`、`PATCH /api/memories/{memoryId}`、`DELETE /api/memories/{memoryId}`；手动禁用采用软禁用 `DISABLED`，并清理 `source_type=memory` 的向量索引，向量清理失败不影响 PostgreSQL 事实状态。

下一步建议：

1. 按 `实际开发/优化阶段/单Agent优化/单Agent记忆与RAG后端冒烟测试指南.md` 跑 core / web / gateway 自动化测试和 HTTP 主链路冒烟，确认记忆手动管理接口、语义召回、RAG、Trace span 和 Gateway contextLoad 都没有破坏主链路。
2. 再做前端长期记忆管理页：列表、分类筛选、改写、重要性调整、禁用。
3. 再做前端 Trace 可视化细化，让 `memory.embedding`、`memory.vector.search`、`rag.embedding`、`rag.vector.search` 在工作台上更容易区分。
4. Planner `needsRag/ragQuery`、HyDE、RRF、Neo4j 继续后置，不提前拉回单 Agent 主链路。
