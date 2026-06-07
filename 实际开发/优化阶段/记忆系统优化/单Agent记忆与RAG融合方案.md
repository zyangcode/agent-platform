# 单 Agent 记忆系统与 RAG — 融合方案

> 本文融合三份前置文档（`单Agent记忆与RAG设计方案.md`、`单Agent记忆与RAG混合检索增强方案.md`、`单Agent记忆系统业界对照与优化建议.md`），形成一份完整、统一的设计与实施路线。
>
> 覆盖：记忆分层、RAG 知识库、三路混合检索（向量+倒排+图谱）、RRF 融合、记忆衰减、语义去重、查询增强。

## 文档结构速览

| 章节 | 内容 | 来源 |
|------|------|------|
| §1 总体架构 | 五层记忆 + RAG 数据边界 | 设计方案 + 业界对照 |
| §2 检索架构 | 当前状态 → 目标（三路并行） | 混合检索增强 |
| §3 三路检索详解 | 向量(Qdrant) + 倒排(PG tsvector→ES) + 图谱(Neo4j) | 混合检索增强 |
| §4 RRF 融合 | 公式 + 计算示例 + 降级策略 | 混合检索增强 |
| §5 记忆生命周期 | 衰减公式 + 语义去重 + Consolidation + 事件级记忆 | 业界对照 |
| §6 查询增强 | HyDE + MQE + 语义缓存 | 业界对照 |
| §7 上下文注入优化 | 消息折叠 LLM 摘要 + Preference 双通道 | 业界对照 |
| §8 反思与程序记忆 | 反思机制 + tool_failure 程序记忆 | 业界对照 |
| §9 实施路线 | 5A→5B→5C→5D→5E→5F 六步 | 三方案统一编排 |
| §10 关键决策 | 10 项核心决策汇总表 | 三方案统一 |
| §11 参考来源 | 论文 + 文章 + 参考项目 | — |

---

## 1. 总体架构

### 1.1 记忆分层

```
┌─────────────────────────────────────────────────────────┐
│ 工作记忆 (Working Memory)                                │
│   apiMessages 消息列表 + 上下文窗口（会话级，对话结束清除）  │
│   管理: compactHistory（短期压缩）+ MicroCompact（微压缩）  │
│   增强: 消息折叠 LLM 摘要（规划）                           │
├─────────────────────────────────────────────────────────┤
│ 情景记忆 (Episodic Memory)                               │
│   memories 表, category=summary/episodic                 │
│   按事件时间线存储，支持时间范围检索（建设中）               │
│   管理: 记忆衰减公式、语义去重、Consolidation              │
├─────────────────────────────────────────────────────────┤
│ 语义记忆 (Semantic Memory)                               │
│   memories 表, category=preference/fact                  │
│   + Neo4j 知识图谱（规划中）                               │
│   管理: PreferenceExtractor（规则）+ LLM 提取（规划）      │
├─────────────────────────────────────────────────────────┤
│ 程序记忆 (Procedural Memory)                             │
│   memories 表, category=tool_failure（规划中）            │
│   工具调用失败经验、工作流优化                               │
├─────────────────────────────────────────────────────────┤
│ 元记忆 (Meta Memory)                                     │
│   memories 表, category=reflection（规划中）               │
│   对话后异步反思生成                                       │
└─────────────────────────────────────────────────────────┘

RAG 知识库（独立于记忆系统）
  knowledge_documents + knowledge_chunks 表
  → TextSplitter → Embedding → Qdrant（向量索引）
  → Neo4j（知识图谱索引，规划中）
  → PG tsvector（倒排索引，规划中）
```

### 1.2 数据边界

Memory 和 RAG 只在 `core.context` 的 Slot 注入层汇合，存储、索引和召回范围必须分离。

```text
core.memory（用户长期状态）
  PostgreSQL: memories 表
  Qdrant collection: "memory"
  PG tsvector: memories.search_vector（规划）
  Neo4j: 用户事实/偏好图谱索引（规划，可选）
  categories:
    ├─ summary     对话摘要
    ├─ preference  用户偏好
    ├─ fact        用户/项目事实
    ├─ episodic    事件
    ├─ tool_failure 工具经验
    └─ reflection  反思

core.rag（外部知识依据）
  PostgreSQL: knowledge_documents / knowledge_chunks 表
  Qdrant collection: "rag"
  PG tsvector: knowledge_chunks.search_vector（规划）
  Neo4j: 文档实体关系图谱索引（规划，可选）

core.context（唯一汇合点）
  MemorySlotSource -> TASK_MEMORY Slot
  RagSlotSource    -> RAG_RECALL Slot
  不混用存储，不交叉检索，不把 memory 当 citation 来源。
```

---

## 2. 检索架构

### 2.1 当前状态（Stage 4 完成态）

```
用户查询
  ├── 记忆召回 → 优先向量(VectorStore "memory") → 降级关键词(PG LIKE)
  └── RAG 检索 → 优先向量(VectorStore "rag")     → 降级中文2-gram(PG LIKE)

问题:
  ✗ 关键词 LIKE 全表扫描，无 BM25 打分
  ✗ 向量+关键词串行（向量有结果就跳过关键词），非并行融合
  ✗ 缺少实体关系检索（多跳推理）
```

### 2.2 目标架构（Stage 5 完整态）

```
用户查询
  │
  ├─ 可选: HyDE 查询增强（模糊查询时 LLM 生成假设答案）
  ├─ 可选: MQE 多查询扩展（拆分为多个子查询并行搜索）
  │
  ├── 记忆召回
  │     ├─ 并行1: 向量语义召回  (Qdrant ANN, collection="memory")
  │     ├─ 并行2: 倒排索引召回  (PG tsvector → ES BM25)
  │     └─ 并行3: 知识图谱召回  (Neo4j, 从用户输入实体 2 跳游走)
  │     → RRF 融合 → 记忆衰减公式排序 → PG 回表 → TASK_MEMORY Slot
  │
  └── RAG 检索
        ├─ 并行1: 向量语义召回  (Qdrant ANN, collection="rag")
        ├─ 并行2: 倒排索引召回  (PG tsvector → ES BM25)
        └─ 并行3: 知识图谱召回  (Neo4j, 从用户输入实体 2 跳游走)
        → RRF 融合 → PG 回表 → RAG_RECALL Slot
```

---

## 3. 三路检索详解

### 3.1 向量语义检索（已完成）

- Qdrant ANN 检索
- payload filter: tenant/application/user/profile
- collection 隔离: `"memory"` vs `"rag"`
- 失败降级: 返回空列表，走其他路径

### 3.2 倒排索引检索

**Step A: PG tsvector（推荐先做）**

```sql
ALTER TABLE knowledge_chunks ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;
ALTER TABLE memories ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;
CREATE INDEX idx_chunks_search ON knowledge_chunks USING GIN(search_vector);
CREATE INDEX idx_memories_search ON memories USING GIN(search_vector);

-- 查询: ts_rank() BM25 级打分
SELECT *, ts_rank(search_vector, query) AS rank
FROM knowledge_chunks
WHERE search_vector @@ plainto_tsquery('simple', '北京 天气')
ORDER BY rank DESC;
```

**Step B: Elasticsearch（后续可选）** — 通过 `KeywordSearchService` 接口切换实现。

### 3.3 知识图谱检索

**直接落地 Neo4j**，不做 PG 中间过渡。

```
RAG Ingest 流程:
  文档 chunk 写入 PG → 异步 LLM 提取实体关系 → 写入 Neo4j

Neo4j 模型:
  (:Entity {name, type, tenant_id, application_id, document_id})
  -[:USES|IMPLEMENTS|DEPENDS_ON|ALTERNATIVE_TO {chunk_id, weight}]→
  (:Entity {...})

检索:
  MATCH (start:Entity) WHERE start.name IN $names
  MATCH path = (start)-[*1..2]-(related:Entity)
  RETURN path → 收集 chunk_id → PG 回表
```

**Docker Compose 部署**:

```yaml
neo4j:
  image: neo4j:5.20-community
  ports: ["7474:7474", "7687:7687"]
  environment: [NEO4J_AUTH=neo4j/password, NEO4J_PLUGINS=["apoc"]]
```

**降级**: Neo4j 不可用时图谱路返回空，不影响向量+倒排。

---

## 4. RRF 融合

三路检索返回不同量纲的分数（余弦相似度 / BM25 / 路径权重），用 RRF 融合：

```
RRF_score(d) = Σ 1 / (k + rank_i(d))
  k = 60（经典常数）
```

```
示例: "JWT 的加密算法有哪些替代方案"

向量 top5:   #1 chunk-42, #2 chunk-17, #3 chunk-55, #4 chunk-03, #5 chunk-88
倒排 top5:   #1 chunk-55, #2 chunk-42, #3 chunk-99, #4 chunk-03, #5 chunk-11
图谱 top5:   #1 chunk-11, #2 chunk-03, #3 chunk-42, #4 chunk-66, #5 chunk-77

RRF 融合:
  chunk-42: 向量#1 + 倒排#2 + 图谱#3 → 0.0164+0.0161+0.0159 = 0.0484 ← 最高
  chunk-03: 向量#4 + 倒排#4 + 图谱#2 → 0.0156+0.0156+0.0161 = 0.0473
  chunk-11: 未在向量top5 + 倒排#5 + 图谱#1 → 0+0.0154+0.0164 = 0.0318
  → 最终排序: chunk-42 > chunk-03 > chunk-55 > chunk-11 > chunk-17
```

三路并行执行，任一路失败不影响其他路：

```
向量异常 → RRF 只用倒排+图谱
倒排异常 → RRF 只用向量+图谱
图谱异常 → RRF 只用向量+倒排
全异常   → 返回空列表（上层兜底提示）
```

---

## 5. 记忆生命周期管理

### 5.1 记忆衰减公式

**当前**: 静态 `importance` + 简单 `keywordHits` 排序。

**优化**: 引入动态 Utility Score：

```
S(M) = α × e^(-λ × Δt)       ← 时间衰减（越久越不重要）
     + β × cosine(v, v_q)     ← 查询语义相关性
     + γ × importance         ← 用户手动标记重要性
     + δ × min(1, accessCount × 0.05)  ← 访问频率加成

推荐参数: α=0.3, β=0.4, γ=0.2, δ=0.1, λ=0.03（半衰期≈23天）
```

**落地点**: 替换 `DefaultMemoryRecallService.score()` 方法。不需改表（`importance`、`accessCount`、`lastAccessedAt` 已存在）。

### 5.2 语义去重

**当前**: 用户反复说"我喜欢简洁回答"，每次写入一条新记录。

**优化**: 写入前先检索是否有语义相似（cosine ≥ 0.85）的已有记忆：

```
有相似 → 提升已有记忆的 importance（+0.1），不新增
无相似 → 正常写入
```

**落地点**: `DefaultMemoryWriteService.record()` 方法。

### 5.3 Consolidation 三阶段

```
阶段1 重要性衰减: 定时任务 (每天凌晨)
  UPDATE memories SET importance = importance * 0.95
  WHERE updated_at < NOW() - INTERVAL '7 days'
    AND importance > 0.1

阶段2 去重合并: 定时任务 (每周)
  相似度 ≥ 0.9 的记忆 → 保留 importance 最高的一条, 软禁用其余

阶段3 过期淘汰: 定时任务 (每月)
  importance < 0.05 且 90天未访问 → STATUS='ARCHIVED'
```

### 5.4 事件级情景记忆

在现有 `memories` 表基础上扩展：

```sql
ALTER TABLE memories ADD COLUMN event_type VARCHAR(50);
-- conversation_turn, tool_call, user_feedback, preference_update, fact_learned
ALTER TABLE memories ADD COLUMN occurred_at TIMESTAMPTZ;
```

支持按时间线检索：`GET /api/memories/timeline?from=...&to=...&event_type=...`

---

## 6. 查询增强（可选）

### 6.1 HyDE — 假设文档嵌入

模糊查询时（向量 top result 分数 < 阈值），触发：

```
用户: "上次那个问题解决了吗？"
  → LLM 生成假设答案:
    "用户可能在询问之前讨论过的技术问题，可能是关于 Spring Security 配置或 JWT..."
  → 用假设答案的向量去检索 → 比原始模糊问题命中率高
```

### 6.2 MQE — 多查询扩展

```
用户: "Spring Security 和 JWT 的关系"
  → LLM 拆分子查询:
    ["Spring Security 认证流程", "JWT token 验证", "Spring Security JWT 集成配置"]
  → 并行检索 → 合并去重 → RRF 融合
```

### 6.3 语义缓存

```java
// Caffeine 缓存，5分钟过期
private final Cache<String, List<ScoredResult>> cache = Caffeine.newBuilder()
    .maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build();

// cacheKey = tenantId:profileId:queryHashCode
// 仅缓存完全相同的查询（不做模糊匹配缓存）
```

---

## 7. 上下文注入策略优化

### 7.1 消息折叠 — LLM 摘要

**当前**: `compactHistory()` 丢弃旧消息时只写 `[compact.auto] messages=18`，完全丢失语义。

**优化**: 调 LLM 生成 200 token 的真正摘要：

```
[compact.auto] Previously: The user discussed Spring Security JWT configuration,
asked about token expiration (decided: 24h with refresh rotation),
and mentioned using MyBatis-Plus instead of JPA. No unresolved issues.
```

策略可配置（默认关闭），让用户在"省 Token"和"保上下文"之间选择。

### 7.2 Preference 提取 — 规则 + LLM 双通道

**当前**: 仅规则驱动（`PreferenceExtractor` 关键词匹配）。

**优化**: 对长对话异步走 LLM NER 提取，规则兜底立即生效：

```
优先级1: 规则提取（"我喜欢/我不喜欢/我偏好..."） → 立即写入
优先级2: LLM NER 提取（异步） → 覆盖/补充规则结果
```

---

## 8. 反思与程序记忆

### 8.1 反思机制

每轮对话结束后，异步生成反思摘要：

```
LLM Prompt:
  Review the conversation and identify:
  1. What went well?
  2. What could be improved?
  3. Any new facts learned about the user?
  4. Any tool failures or unexpected results?
→ 写入 memories 表, category=reflection
```

### 8.2 程序记忆

工具调用失败时写入 `tool_failure` 类型记忆：

```
格式: "Tool {toolName} failed with {error}. Context: {input}. Suggestion: {fix}"
示例: "Tool weather.current failed with 400. Context: city='北'. Suggestion: verify city name spelling."
```

Agent 在后续对话中召回此类记忆，自主避免重复错误。

---

## 9. 实施路线

```
已完成 (Stage 1~4):
  ✓ Memory v2 (memories 表扩展 + MemoryRecallFilter)
  ✓ Schema-driven Context (5 Slot + ContextSchemaAssembler)
  ✓ RAG MVP (knowledge_documents/chunks + TextSplitter + PG keyword fallback)
  ✓ Qdrant 接入 (QdrantVectorStore + collection 懒初始化 + 降级)
  ✓ 记忆语义召回 (vectorStore.search("memory"))

当前阶段 (Stage 5 — 本方案):

  Step 5A: 倒排索引 (1-2h)
    ├─ knowledge_chunks + memories 加 tsvector 列 + GIN 索引
    ├─ 新增 PgTsvectorKeywordSearch
    ├─ 串行改为向量+倒排并行 → RRF 融合
    └─ 验证: 关键词查询质量、Trace 可观测

  Step 5B: Neo4j 知识图谱 (3-4h)
    ├─ docker-compose.dev.yml 新增 Neo4j 容器
    ├─ RAG ingest 后 LLM 提取实体关系 → 写入 Neo4j
    ├─ 新增 Neo4jGraphSearchService
    ├─ 三路并行 → RRF 融合完整闭环
    └─ 验证: 关系型查询命中、Neo4j 不可用时降级

  Step 5C: 记忆衰减公式 (1h)
    ├─ DefaultMemoryRecallService.score() → utilityScore()
    └─ 验证: 旧记忆自动降权、高热度记忆提升

  Step 5D: 语义去重 + Consolidation (1-2h)
    ├─ DefaultMemoryWriteService.record() 写前去重
    ├─ 定时任务: 衰减 + 合并 + 淘汰
    └─ 验证: 重复偏好不新增、过期记忆自动归档

  Step 5E: 事件级情景记忆 (1h)
    ├─ memories 表加 event_type + occurred_at
    ├─ 时间线检索 API
    └─ 验证: 按时间范围查询事件

  Step 5F: 反思机制 (1-2h)
    ├─ 对话结束后异步 LLM 反思
    ├─ tool_failure 程序记忆写入
    └─ 验证: 反思内容可召回、工具经验可复用

后续增强 (Stage 5+):
  ○ Elasticsearch 替换 PG tsvector（数据量 > 10万时）
  ○ HyDE + MQE 查询增强（作为可选开关）
  ○ 消息折叠 LLM 摘要（作为可配置策略）
  ○ 语义缓存
  ○ Preference LLM 双通道提取
```

---

## 10. 关键设计决策汇总

| 决策 | 选择 | 理由 |
|------|------|------|
| 向量库 | Qdrant | 轻量、API 清晰、已有完整接入 |
| 倒排索引 | PG tsvector → ES | 零新组件起步，ES 后续按需切换 |
| 知识图谱 | Neo4j 直接落地 | 图查询性能远超 PG CTE，Docker Compose 同级部署 |
| 融合算法 | RRF (k=60) | 无需调参、不关心原始分数量纲 |
| 记忆衰减 | 三维 Utility Score | 时间+相关+重要性，替换静态 score() |
| 实体提取 | LLM（异步 ingest 时） | 比规则 NER 准确，比本地模型轻量 |
| 语义去重 | 写前检索 + 阈值 0.85 | 减少存储膨胀，提高召回质量 |
| 反思机制 | 异步 LLM 反思 | 不阻塞对话响应，积累工具经验 |
| 查询增强 | 可配置开关（默认关闭） | 避免无谓的额外 LLM 调用 |
| 降级总原则 | 外部依赖可降级、可 Mock | 向量/倒排/图谱任一路失败不影响主链路 |

---

## 11. 参考来源

- 小林 Coding: `https://xiaolinnote.com/ai/agent/8_memory.html` (记忆系统)
- 小林 Coding: `https://xiaolinnote.com/ai/agent/9_memory_storage.html` (记忆存储)
- 小林 Coding: `https://xiaolinnote.com/ai/agent/12_memcompress.html` (记忆压缩)
- UCI Hybrid Memory: Intelligent Decay — Utility Score Formula (2025)
- Cornell Semantic Anchoring: Linguistic Structures for Persistent Conversational Context (2025)
- MMAG: Mixed Memory-Augmented Generation — Five Interacting Layers (2025)
- Adrian et al.: Reciprocal Rank Fusion (SIGIR 2009)
- Amazon Bedrock AgentCore: 语义去重 + 异步记忆演化 (2025)
- MUSE: Plan-Execute-Reflect-Memorize Loop
- Mem0 / Letta (MemGPT): OS-inspired tiered memory architecture
- AGI-saber (Go/Java): 记忆分层 + 混合 RAG 参考架构

