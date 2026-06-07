# 记忆系统优化建议 — 对照业界最佳实践

> 本文对照小林 Coding、UCI 混合记忆论文、Mem0/Letta/Amazon Bedrock AgentCore 等业界方案，
> 审视当前 `agent-platform` 记忆系统的差距，给出可落地的优化建议。

## 1. 当前状态 vs 业界标准

### 1.1 记忆分层对比

| 记忆层 | 业界标准 | 当前实现 | 差距 |
|--------|---------|---------|------|
| **工作记忆** (Working) | 上下文窗口 + 滑动窗口截断 + 消息折叠 | ✓ 已实现：`apiMessages` + `compactHistory` + `MicroCompact` | 缺少**消息折叠**（将多条历史消息折叠为一条摘要注入，而非简单丢弃） |
| **情景记忆** (Episodic) | 向量库 + 时间戳 + 事件级检索 | △ 部分实现：`memories` 表 `memory_category=summary` | 缺少独立的**事件时间线**和**时序检索**能力；当前 summary 是对话级摘要，不是事件级 |
| **语义记忆** (Semantic) | 用户偏好 / 事实知识 / 知识图谱 | ✓ 已实现：`preference` + `fact` + `Neo4j`（规划中） | preference 提取目前是**规则驱动**（关键词匹配），不是 LLM 提取 |
| **程序记忆** (Procedural) | 工具使用经验 / 工作流 / 技能优化 | ✗ 未实现 | 可增加 `tool_failure` 类型的记忆，让 Agent 从工具调用失败中学习 |
| **元记忆** (Meta) | 反思日志 / 错误分析 / 自我改进 | ✗ 未实现 | 对话结束后可异步生成反思摘要 |

### 1.2 检索能力对比

| 能力 | 业界标准 | 当前实现 | 差距 |
|------|---------|---------|------|
| 向量语义检索 | Qdrant/Milvus ANN | ✓ 已实现 | — |
| 关键词/倒排索引 | BM25 / Elasticsearch | △ PG LIKE（全表扫描） | 已在混合检索方案中规划 PG tsvector |
| 知识图谱 | Neo4j GraphRAG | △ 规划中 | 已在混合检索方案中规划 |
| 多查询扩展 (MQE) | 自动拆分查询为多个子查询并行搜索 | ✗ 未实现 | **新增优化点** |
| HyDE (假设文档嵌入) | LLM 生成假设答案 → 用假答案向量检索 | ✗ 未实现 | **新增优化点** |
| Reranker 重排序 | 粗召回 → 精排模型 → topK | ✗ 未实现 | RRF 可视为轻量替代 |
| 语义缓存 | 缓存高频查询的检索结果 | ✗ 未实现 | **新增优化点** |

---

## 2. 新增优化建议

### 2.1 记忆衰减公式 — 重要性 + 时间 + 访问频率

**当前问题**：`importance` 字段存在但只是静态值（0~1），没有动态衰减机制。一条半年前的重要记忆和昨天的不重要记忆，排序上没有本质区别——只有 `accessCount` 和 `lastAccessedAt` 的简单更新。

**业界方案**（UCI 2025 论文的 Utility Score）：

```
S(M_i) = α × e^(-λ × Δt)     ← 时间衰减（越久越不重要）
       + β × cosine(v_i, v_q)  ← 查询相关性（语义匹配）
       + γ × U_i               ← 用户标记重要性（手动 pin/forget）
```

**建议实现**：

```java
// 在 DefaultMemoryRecallService 的排序逻辑中，替换当前的简单 keywordHits + importance：

public double utilityScore(MemoryEntity memory, String query, EmbeddingVectorDTO queryVector) {
    double recency = Math.exp(-LAMBDA * daysSince(memory.getUpdatedAt()));
    // LAMBDA 建议 0.01~0.05，对应半衰期约 14~70 天

    double relevance = cosineSimilarity(memory.getEmbedding(), queryVector);
    // 注意：这里需要 memories 表中有 embedding 字段（当前已支持 Qdrant 写入）

    double importance = memory.getImportance() == null ? 0.5 : memory.getImportance();
    // 用户可通过前端手动调整

    double accessBoost = Math.min(1.0, memory.getAccessCount() == null ? 0
            : memory.getAccessCount() * 0.05);
    // 频繁访问的记忆获得额外加成

    return ALPHA * recency + BETA * relevance + GAMMA * importance + accessBoost;
    // α=0.3, β=0.4, γ=0.2, accessBoost 叠加
}
```

**落地点**：替换 `DefaultMemoryRecallService` 中的 `score()` 方法。不需要改表结构（`importance`、`accessCount`、`lastAccessedAt` 字段已存在）。

---

### 2.2 事件级情景记忆 — 从"对话摘要"到"事件时间线"

**当前问题**：`memory_category=summary` 存的是整轮对话的摘要，粒度太粗。用户问"我上周三跟 Agent 聊了什么"，无法精确检索到那一天的具体内容。

**业界方案**：Mem0 / Letta 将记忆拆分为**事件级**，每条记忆有独立的时间戳和事件类型标签。

**建议实现**：

```sql
-- 在现有 memories 表基础上，增加事件类型和发生时间：
ALTER TABLE memories ADD COLUMN event_type VARCHAR(50);
-- 可选值: conversation_turn, tool_call, user_feedback, preference_update, fact_learned

ALTER TABLE memories ADD COLUMN occurred_at TIMESTAMPTZ;
-- 事件发生的时间（区别于 created_at 创建记录的时间）

-- 新增按时间范围检索的 API:
-- GET /api/memories/timeline?from=2026-06-01&to=2026-06-07&event_type=conversation_turn
```

**写入时机**：不需要改 `saveMemory()` 的主流程，只需在写入 `SUMMARY` 类记忆时额外填充 `event_type` 和 `occurred_at`。

---

### 2.3 语义去重 — 写前检查相似记忆

**当前问题**：用户反复说"我喜欢简洁回答"，每次对话结束都会写入一条新的 preference 记忆，不去重。导致 `memories` 表膨胀，且召回时可能出现多条内容几乎相同的记忆。

**业界方案**：Amazon Bedrock AgentCore 的核心能力之一就是"语义去重"——写入前先检查是否有相似内容，有则**更新重要性**而非新增。

**建议实现**：

```java
// 在 DefaultMemoryWriteService.record() 中，写入前加一层去重：

public void record(RecordMemoryCommand command) {
    // ① 先检查是否有相似记忆
    List<MemoryDTO> similar = memoryRecallService.recall(
            command.tenantId(), command.applicationId(),
            command.userId(), command.profileId(),
            command.content(),  // 用待写入的内容做检索
            MemoryRecallFilter.builder()
                    .categories(List.of(command.memoryCategory()))
                    .minScore(0.85)    // 相似度阈值
                    .topK(3)
                    .build()
    );

    // ② 有高度相似的 → 更新重要性 + 访问时间，不新增
    if (!similar.isEmpty() && similar.get(0).importance() > 0.0) {
        MemoryDTO existing = similar.get(0);
        memoryMapper.updateImportance(existing.id(),
                Math.min(1.0, existing.importance() + 0.1));  // 提升重要性
        return;  // 不重复写入
    }

    // ③ 没有相似 → 正常写入
    doInsert(command);
}
```

**收益**：减少存储膨胀，提高召回质量（不会返回多条重复内容）。

---

### 2.4 消息折叠 — 短期记忆的优雅降级

**当前问题**：`compactHistory()` 将旧消息压缩为 `[compact.auto] older history summarized; messages=18`，完全丢失了旧消息的**语义内容**。模型不知道那 18 条消息说了什么。

**业界方案**：
- **上下文缩减**：LLM 生成旧消息的摘要，保留核心信息
- **上下文卸载**：大内容卸到文件系统/外部存储，保留引用指针
- **上下文隔离**：子 Agent 独立上下文，只需最终结果

**建议实现**：在 `compactHistory()` 的摘要阶段，调用一次 LLM 生成真正的摘要（而非仅计数标记）：

```java
// 当前实现:
String summary = "[compact.auto] older history summarized; messages=18";

// 优化方案:
// ① 挑选旧消息中 importance_score 最高的 N 条保留原文
// ② 其余的用 LLM 生成一段 200 token 以内的摘要:
//    "Previously, the user discussed Spring Security JWT configuration,
//     asked about token expiration strategies, and mentioned using MyBatis-Plus.
//     Key decisions: JWT expiry set to 24h, using refresh token rotation."
// ③ 摘要 + 保留的原文 → 注入为新 system 消息
```

**注意**：这需要额外一次 LLM 调用，增加了延迟和 Token 消耗。建议作为**可配置策略**（默认关闭），让用户在"省 Token"和"保上下文"之间选择。

---

### 2.5 查询增强 — MQE + HyDE

**当前问题**：记忆和 RAG 检索直接使用用户的原始输入作为查询。用户说"上次那个问题解决了吗？"——这种模糊引用（coreference）在纯文本检索中很难命中。

**业界方案**：
- **MQE（多查询扩展）**：将用户问题拆为多个子查询并行搜索
- **HyDE（假设文档嵌入）**：先让 LLM 生成一个假设答案，用答案的向量去检索

**建议实现**（作为可选增强）：

```java
// 在 RagSlotSource.fetch() 和 MemoryRecallService.recall() 中可选开启：

// HyDE 模式:
String hypotheticalAnswer = llm.generate(
    "Based on your knowledge, answer: " + userQuery
);
// "上次那个问题解决了吗？" → LLM 猜测:
//   "用户可能在询问之前讨论过的某个技术问题，可能是关于 Spring Security 或 JWT 配置的..."
// 用这个猜测答案去检索，比用模糊原始问题效果好得多

// MQE 模式:
List<String> subQueries = llm.generate(
    "Break down this query into 3 specific search queries: " + userQuery
);
// "上次那个问题解决了吗？" → ["Spring Security JWT 配置问题", "token 过期策略", "MyBatis-Plus 配置"]
// 3 个子查询并行检索 → 合并结果 → RRF 融合
```

**启用条件**：仅在向量检索返回空或 top result 分数 < 阈值时触发（避免每次都额外调 LLM）。

---

### 2.6 语义缓存 — 高频查询加速

**当前问题**：同一用户在同一会话中可能反复问类似问题，每次都重新调 embedding + 向量检索。

**建议实现**：

```java
// 在 RagSlotSource 和 MemoryRecallService 外层加缓存：

// 使用 Caffeine 或简单的 LRU Map:
private final Cache<String, List<ScoredResult>> searchCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

public List<ScoredResult> search(String query, ...) {
    String cacheKey = tenantId + ":" + profileId + ":" + query.hashCode();
    return searchCache.get(cacheKey, k -> doActualSearch(query, ...));
}
```

**注意**：缓存 key 需要包含 tenant/profile 范围。仅缓存查询文本哈希完全相同的请求（不做模糊匹配缓存，避免语义漂移）。

---

### 2.7 反思机制 — 元记忆

**当前问题**：Agent 做完一轮对话后只写 SUMMARY（对话原文拼接），不反思"这次哪里做得好/不好"。

**业界方案**：MUSE 的 Plan-Execute-Reflect-Memorize 循环，每轮结束后生成反思。

**建议实现**：

在 `saveMemory()` 中异步追加一条反思：

```java
// 对话结束后，异步调 LLM 生成反思：
String reflection = llm.generate("""
    Review the following conversation and identify:
    1. What went well?
    2. What could be improved?
    3. Any new facts learned about the user?
    4. Any tool failures or unexpected results?

    User: %s
    Assistant: %s
    Tool calls: %s
    """.formatted(userInput, assistantMessage, toolEvents));

// 写入 memories 表:
memoryWriteService.record(new RecordMemoryCommand(
    ..., "REFLECTION", reflection, conversationId
));
```

**收益**：Agent 能积累"工具调用经验"——例如"`weather.current` 对中文城市名不敏感，下次应该先翻译成英文"。

---

## 3. 优化优先级排序

| 优先级 | 优化项 | 改动量 | 收益 | 风险 |
|--------|--------|--------|------|------|
| **P0** | §2.1 记忆衰减公式 | 小（改排序算法） | 高（召回质量显著提升） | 低 |
| **P0** | §2.3 语义去重 | 中（改写入流程） | 高（减少存储膨胀） | 低 |
| **P1** | §2.5 查询增强 (HyDE) | 中（加 LLM 调用） | 高（模糊查询命中率） | 中（额外延迟和成本） |
| **P1** | §2.2 事件级情景记忆 | 中（加字段 + API） | 中（时间线检索） | 低 |
| **P2** | §2.4 消息折叠 LLM 摘要 | 中（加 LLM 调用） | 中（保留旧消息语义） | 中（额外延迟和成本） |
| **P2** | §2.6 语义缓存 | 小（加缓存层） | 中（减少重复检索） | 低 |
| **P3** | §2.7 反思机制 | 中（加异步 LLM） | 中（工具经验积累） | 中（额外成本） |

---

## 4. 与当前架构的兼容性

所有优化都在现有模块边界内落地，不破坏已有接口：

| 优化项 | 落地点 | 影响的文件 |
|--------|--------|-----------|
| 记忆衰减公式 | `DefaultMemoryRecallService.score()` | 1 个文件 |
| 语义去重 | `DefaultMemoryWriteService.record()` | 1 个文件 |
| 事件级情景记忆 | `memories` 表 + `RecordMemoryCommand` | 3~4 个文件 |
| 查询增强 | `RagSlotSource` / `DefaultMemoryRecallService` | 2 个文件 |
| 消息折叠 LLM 摘要 | `DefaultAgentContextBuilder.compactHistory()` | 1 个文件 |
| 语义缓存 | 新增 `SearchCacheManager` | 1 个文件 |
| 反思机制 | `DefaultAgentRuntimeService.saveMemory()` | 1 个文件 |

`RagSlotSource`、`MemorySlotSource`、`AgentContextBuilder` 等上下文注入层代码**不需要改动**。

---

## 5. 参考来源

- 小林 Coding: `https://xiaolinnote.com/ai/agent/8_memory.html` (记忆系统设计)
- 小林 Coding: `https://xiaolinnote.com/ai/agent/9_memory_storage.html` (记忆存储)
- 小林 Coding: `https://xiaolinnote.com/ai/agent/12_memcompress.html` (记忆压缩)
- UCI Hybrid Memory: "Intelligent Decay — Utility Score Formula" (2025)
- Cornell Semantic Anchoring: "Linguistic Structures for Persistent Conversational Context" (2025)
- MMAG: "Mixed Memory-Augmented Generation — Five Interacting Layers" (2025)
- Amazon Bedrock AgentCore: 语义去重 + 异步记忆演化 (2025)
- MUSE: Plan-Execute-Reflect-Memorize Loop
- Mem0 / Letta (MemGPT): OS-inspired tiered memory architecture
