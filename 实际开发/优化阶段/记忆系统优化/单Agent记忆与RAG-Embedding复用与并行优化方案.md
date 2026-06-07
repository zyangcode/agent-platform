# 记忆与RAG — Embedding 复用与并行优化方案

> 当前 `DefaultAgentContextBuilder.build()` 中，记忆召回和 RAG 检索各自独立调用 `embeddingService.embed(userInput)`，同一个输入被向量化了两次，且两处检索是串行执行的。本文提出优化方案。

## 1. 问题定位

### 1.1 当前时序

```
DefaultAgentContextBuilder.build()
  │
  ├─ 行110: recallMemories(command)
  │    └─ DefaultMemoryRecallService.recall()
  │         └─ recallByVector()
  │              └─ embeddingService.embed(userInput)    ← 第1次 Embedding（~200ms）
  │                   └─ vectorStore.search("memory")
  │
  ├─ 行111: resolveExperienceSkills()     ← 中间穿插其他操作
  ├─ 行112: listRecentMessages()
  ├─ 行121: ContextBudgetPolicy.from()
  │
  └─ 行130: buildContextBlocks()
       └─ RagSlotSource.fetch()
            └─ DefaultPostgresRagEngine.search()
                 └─ searchByVector()
                      └─ embeddingService.embed(userInput)  ← 第2次 Embedding（~200ms）
                           └─ vectorStore.search("rag")

总耗时 ≈ 200(embed1) + 100(search memory) + 50(中间操作) + 200(embed2) + 100(search rag) = 650ms
```

### 1.2 问题

| 问题 | 影响 |
|------|------|
| 同一个 `userInput` 调了两次 `embed()` | 每次 ~200ms 的网络 I/O 白白浪费 |
| 两处检索串行执行 | 总耗时 = 记忆耗时 + 中间操作 + RAG耗时 |
| 即使一侧禁用（如 `memoryStrategy=DISABLED`），RAG 仍要自己嵌一次 | 不能共享已计算的向量 |

## 2. 优化方案

### 2.1 核心思路

```
Embedding 提前计算一次 → 分发给记忆和 RAG 各自并行检索 → 各自独立后处理
```

**复用的是 Embedding**，**不复用的是后续的 collection 检索、过滤、排序、截断**——因为记忆和 RAG 的数据源、过滤规则、排序算法、Token 预算完全不同。

### 2.2 目标时序

```
DefaultAgentContextBuilder.build()
  │
  ├─ 提前: queryVector = embeddingService.embed(userInput)   ← 只调1次（~200ms）
  │
  ├─ 并行:
  │    ├─ search("memory", queryVector)    ← 记忆检索（~100ms）
  │    └─ search("rag", queryVector)       ← RAG 检索  （~100ms，与记忆同时进行）
  │
  ├─ 各自后处理（串行或并行均可，不涉及 I/O）:
  │    ├─ 记忆: category过滤 → 衰减排序 → memoryTokenBudget截断 → TASK_MEMORY Slot
  │    └─ RAG:  docScope过滤 → keyword排序 → ragTokenBudget截断 → RAG_RECALL Slot
  │
  └─ 总耗时 ≈ 200(embed) + 100(max search) = 300ms

节省 ≈ 350ms / 650ms = 约 54%
```

## 3. 实现方案

### 3.1 改动范围

| 文件 | 改动 | 说明 |
|------|------|------|
| `DefaultAgentContextBuilder.java` | 约20行 | 新增 `embedUserInput()` 调用点，传入下游 |
| `DefaultMemoryRecallService.java` | 约10行 | 新增 `recall()` 重载，接受 `EmbeddingVectorDTO` |
| `DefaultPostgresRagEngine.java` | 约10行 | 新增 `search()` 重载，接受 `EmbeddingVectorDTO` |
| `MemoryRecallService.java` (接口) | 约3行 | 新增 default 方法 |
| `RagSearchService.java` (接口) | 约3行 | 新增 default 方法 |

总共约 **50 行改动**，不动上下文装配层。

### 3.2 核心代码

**DefaultAgentContextBuilder.build()**:

```java
public AgentContextDTO build(BuildAgentContextCommand command) {
    // ... 前面的校验逻辑不变 ...

    // ★ 提前计算 query embedding（只调一次）
    EmbeddingVectorDTO queryVector = embedUserInput(command);

    // ★ 并行检索（使用 CompletableFuture）
    CompletableFuture<List<MemoryDTO>> memoryFuture = CompletableFuture.supplyAsync(
        () -> shouldRecallMemory(profile)
            ? recallMemories(command, queryVector)  // 传预计算向量
            : List.of()
    );

    CompletableFuture<List<RagSearchResultDTO>> ragFuture = CompletableFuture.supplyAsync(
        () -> ragSearchService.search(command, null, queryVector) // null = 临时先不调 search
    );

    // 等待记忆结果（阻塞，因为后面 buildContextBlocks 需要 memories 作为参数）
    List<MemoryDTO> memories = memoryFuture.join();
    List<ExperienceSkillDTO> experienceSkills = resolveExperienceSkills(command, profile);
    List<ConversationMessageDTO> history = messageHistoryService.listRecentMessages(...);

    // 预算分配
    ContextBudgetPolicy budgetPolicy = ContextBudgetPolicy.from(maxTokens);

    // buildContextBlocks 内部 RagSlotSource 直接使用已构建好的 ragFuture
    contextBlocks = buildContextBlocks(profile, skills, mcpTools, memories,
            experienceSkills, budgetPolicy, command, ragFuture);

    // ... 后续不变 ...
}
```

**MemoryRecallService 接口新增 default 方法**:

```java
public interface MemoryRecallService {

    // 原有方法保持不变
    List<MemoryDTO> recall(Long tenantId, ..., String query, MemoryRecallFilter filter);

    // 新增：接受预计算向量的重载（默认抛出不支持，实现类覆盖）
    default List<MemoryDTO> recall(Long tenantId, ..., String query,
            MemoryRecallFilter filter, EmbeddingVectorDTO queryVector) {
        List<MemoryDTO> results = new java.util.ArrayList<>();
        // 只做非向量路径（关键词匹配），实现类覆盖后加入向量路径
        return results;
    }
}
```

**DefaultMemoryRecallService 覆盖**:

```java
@Override
public List<MemoryDTO> recall(Long tenantId, ..., String query,
        MemoryRecallFilter filter, EmbeddingVectorDTO queryVector) {
    // ★ 直接使用传进来的向量，跳过 embeddingService.embed()
    List<MemoryEntity> vectorMemories = recallByVectorWithPrecomputed(
            tenantId, ..., query, filter, limit, queryVector);
    if (!vectorMemories.isEmpty()) {
        touchReturnedMemories(vectorMemories);
        return vectorMemories.stream().map(this::toDTO).toList();
    }
    // 向量路径无结果 → 走关键词降级
    // ... 与原有 recall() 的关键词路径完全一样 ...
}

private List<MemoryEntity> recallByVectorWithPrecomputed(
        Long tenantId, ..., String query, MemoryRecallFilter filter,
        int limit, EmbeddingVectorDTO queryVector) {
    // 直接从 queryVector 开始搜，不再调 embeddingService.embed()
    List<VectorSearchResultDTO> vectorResults = vectorStore.search(
            new VectorSearchQueryDTO("memory", tenantId, ..., queryVector, limit * FETCH_MULTIPLIER));
    // ... 之后的回表、过滤、排序逻辑与现有 recallByVector 后半段完全相同 ...
}
```

**RagSearchService 接口新增 default 方法**:

```java
public interface RagSearchService {

    List<RagSearchResultDTO> search(Long tenantId, ..., String query, int topK);

    // 新增：接受预计算向量
    default List<RagSearchResultDTO> search(Long tenantId, ..., String query,
            int topK, EmbeddingVectorDTO queryVector) {
        // 默认回退到原有方法（不传向量的实现类保持兼容）
        return search(tenantId, ..., query, topK);
    }
}
```

**DefaultPostgresRagEngine 覆盖**:

```java
@Override
public List<RagSearchResultDTO> search(Long tenantId, ..., String query,
        int topK, EmbeddingVectorDTO queryVector) {
    // ★ 直接使用传进来的向量，跳过 embeddingService.embed()
    List<RagSearchResultDTO> vectorResults = searchByVectorPrecomputed(
            tenantId, ..., query, topK, queryVector);
    if (!vectorResults.isEmpty()) {
        return vectorResults;
    }
    // 向量路径无结果 → 走关键词降级
    List<String> terms = keywords(query).stream().toList();
    return chunkMapper.searchActiveChunks(tenantId, ..., terms, topK);
}
```

### 3.3 向量不可用时的处理

如果 `embeddingService` 或 `vectorStore` 不可用（返回 `null` 或抛异常）：

```
embedUserInput() → null (无向量可用)
  → MemoryRecallService.recall(..., null)
    → recallByVectorWithPrecomputed 跳过向量路径
    → 走关键词 LIKE 降级
  → RagSearchService.search(..., null)
    → 跳过 searchByVector
    → 走关键词 LIKE 降级

行为与当前完全一致——embedding 不可用时自动降级到关键词。
```

## 4. 与现有降级策略的关系

本优化不影响降级体系，反而增强了它：

| 场景 | 行为 |
|------|------|
| Embedding 正常 | `embed()` 只调一次，向量复用到两路 search |
| Embedding 失败 | `queryVector = null`，两路各自走关键词降级 |
| Memory 侧向量检索失败 | 返回空 → 走 Memory 关键词降级 |
| RAG 侧向量检索失败 | 返回空 → 走 RAG 关键词降级 |
| Memory 关键词也失败 | 返回空列表 → 不注入 TASK_MEMORY（现有行为） |
| RAG 关键词也失败 | 返回空列表 → 不注入 RAG_RECALL（现有行为） |

## 5. 收益预估

| 指标 | 当前 | 优化后 |
|------|------|--------|
| Embedding 调用次数 | 2 次 | 1 次 |
| 检索执行方式 | 串行 | Embedding复用 + 检索可并行 |
| 典型延迟（mem+rag均启用） | ~650ms | ~300ms |
| API 调用成本 | 2x embedding tokens | 1x embedding tokens |
| 代码改动量 | — | ~50行 |
| 对上下文装配层的影响 | — | 零改动 |

## 6. 不做的事情

- 不合并 `memories` 和 `knowledge_chunks` 表（数据必须隔离）
- 不合并记忆和 RAG 的过滤/排序逻辑（规则不同）
- 不改变 Context Slot 注入方式（TASK_MEMORY 和 RAG_RECALL 保持独立）
- 不引入新的外部依赖（只用 JDK `CompletableFuture`）
