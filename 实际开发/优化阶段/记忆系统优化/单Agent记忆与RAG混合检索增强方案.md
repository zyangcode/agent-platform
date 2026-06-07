# 单 Agent 记忆与 RAG — 混合检索增强方案

> 本文是 `单Agent记忆与RAG设计方案.md` 的 Stage 5（Hybrid 与 Graph 增强）的详细展开。在现有向量检索 + 关键词降级基础上，新增倒排索引、知识图谱和 RRF 融合，形成三路并行检索架构。
>
> 前置依赖：Stage 1~4 已完成（Memory v2、Schema-driven Context、RAG MVP、Qdrant 接入）。

## 1. 现状与目标

### 1.1 当前检索架构（Stage 4 完成态）

```
用户查询
  │
  ├── 记忆召回 (DefaultMemoryRecallService)
  │     ├── 优先: Embedding → VectorStore.search("memory")
  │     └── 降级: 关键词提取 → PostgreSQL LIKE
  │
  └── RAG 检索 (DefaultPostgresRagEngine)
        ├── 优先: Embedding → VectorStore.search("rag")
        └── 降级: 中文2-gram 分词 → PostgreSQL LIKE
```

**当前问题**：
1. 关键词降级用的是 `LIKE '%keyword%'`，全表扫描，无相关性打分（BM25）
2. 向量和关键词是**串行降级**关系，不是**并行融合**——向量一有结果关键词就被跳过
3. 没有实体关系检索能力——"JWT 和 Spring Security 的关系"只能靠语义相似度碰运气

### 1.2 目标架构（Stage 5）

```
用户查询
  │
  ├── 记忆召回
  │     ├── 并行1: 向量语义召回 (Qdrant ANN)
  │     ├── 并行2: 倒排索引召回 (PG tsvector → ES BM25)
  │     └── 并行3: 知识图谱召回 (Neo4j)
  │     └── RRF 融合排序 → PostgreSQL 回表 → 注入 TASK_MEMORY Slot
  │
  └── RAG 检索
        ├── 并行1: 向量语义召回 (Qdrant ANN)
        ├── 并行2: 倒排索引召回 (PG tsvector → ES BM25)
        └── 并行3: 知识图谱召回 (Neo4j)
        └── RRF 融合排序 → PostgreSQL 回表 → 注入 RAG_RECALL Slot
```

---

## 2. 倒排索引增强

### 2.1 当前问题：LIKE 查询的三个硬伤

```sql
-- 当前的实现:
SELECT * FROM knowledge_chunks
WHERE content LIKE '%北京%' OR content LIKE '%天气%';
```

| 问题 | 说明 |
|------|------|
| 全表扫描 | 数据上 1000 条就明显变慢 |
| 无相关性打分 | 所有匹配行的权重相同，无法区分"恰好提到"和"主题就是北京天气" |
| 中文分词粗糙 | 2-gram 硬切，"机器学习" → `["机器","器学","学习"]`，噪声大且语义丢失 |

### 2.2 方案：PG tsvector 先行，ES 后续可选

**Step A：PostgreSQL 内置全文搜索（推荐先做）**

不需要新组件，PG 自带 `tsvector` + GIN 索引即可解决上述三个问题。

```sql
-- ① 加列（自动维护，不占业务代码）
ALTER TABLE knowledge_chunks
  ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

ALTER TABLE memories
  ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

-- ② 建 GIN 索引（查询加速）
CREATE INDEX idx_chunks_search ON knowledge_chunks USING GIN(search_vector);
CREATE INDEX idx_memories_search ON memories USING GIN(search_vector);

-- ③ 查询变成：
SELECT *, ts_rank(search_vector, query) AS rank
FROM knowledge_chunks
WHERE search_vector @@ plainto_tsquery('simple', '北京 天气')
  AND tenant_id = ? AND application_id = ? AND status = 'ACTIVE'
ORDER BY rank DESC
LIMIT ?;
```

**tsvector vs LIKE 对比**：

| 维度 | LIKE '%keyword%' | tsvector + GIN |
|------|-----------------|----------------|
| 索引 | 无（全表扫描） | GIN 倒排索引 |
| 相关性 | 无 | `ts_rank()` BM25 级打分 |
| 分词 | 2-gram 硬切 | PG 字典分词（可配中文词典） |
| 查询语法 | LIKE/OR | `@@` 全文搜索操作符 |
| 改动量 | 0 | 加 1 列 + 1 索引 + 改 Mapper SQL |

**Java 层改动**：

只需在 Mapper XML 中新增一个查询方法，`DefaultPostgresRagEngine` 和 `DefaultMemoryRecallService` 各加一条检索分支：

```java
// 新增 KeywordSearchService（与 VectorStore 同级）
public interface KeywordSearchService {
    List<ScoredResult> search(
            String sourceType,       // "rag" | "memory"
            Long tenantId, Long applicationId,
            Long userId, Long profileId,
            String query, int topK
    );
}

// PG tsvector 实现
public class PgTsvectorKeywordSearch implements KeywordSearchService {
    public List<ScoredResult> search(...) {
        // 调用 Mapper 的全文搜索 SQL
        return mapper.searchByTsvector(tenantId, ..., query, topK);
    }
}
```

**Step B：Elasticsearch（后续可选）**

当 PG tsvector 在数据量 > 10 万条时性能不够，再切到 Elasticsearch。接口 `KeywordSearchService` 已就位，切换只改实现类：

```java
// ES 实现（后续替换 PgTsvectorKeywordSearch）
public class EsKeywordSearchService implements KeywordSearchService {
    public List<ScoredResult> search(...) {
        // ES BM25 查询 + tenant/application/profile filter
    }
}
```

> 结论：PG tsvector 方案改动量小（1-2 小时）、效果立即可见、不引入新组件。ES 留给真正需要的场景。

---

## 3. 知识图谱增强

### 3.1 图谱能解决什么问题

| 查询类型 | 向量/关键词能解决？ | 图谱能解决？ |
|---------|-------------------|------------|
| "怎么配置 JWT" | ✓ 语义匹配 | ✗ 不需要 |
| "Spring Security 和 JWT 是什么关系" | △ 可能找到包含两者的文档 | ✓ 精确关系 |
| "JWT 的加密算法有哪些替代方案" | ✗ 很难 | ✓ 多跳游走 |
| "哪些模块依赖了 JWT" | ✗ 很难 | ✓ 反向关系查询 |

图谱的核心价值是**实体关系推理**——它补充了向量语义和关键词都做不到的多跳关联。

### 3.2 方案：LLM 提取 + Neo4j 存储

直接使用 Neo4j 作为图数据库，不做 PG 中间过渡。Neo4j 在 Docker Compose 中与 Qdrant 同级部署，通过 Spring Data Neo4j 或 Neo4j Java Driver 接入。

**Step A：在 RAG Ingest 时提取实体和关系**

在 `DefaultPostgresRagEngine.ingest()` 流程中，chunk 写入后异步调 LLM 提取结构化知识，写入 Neo4j：

```
文档 chunk: "Spring Security 默认使用 BCryptPasswordEncoder 加密密码，
            它实现了 PasswordEncoder 接口，推荐在生产环境使用。"

LLM 提取 → Neo4j:
  CREATE (:Entity {name: 'Spring Security',     type: 'FRAMEWORK', tenant_id: 1, app_id: 20001, doc_id: 42})
  CREATE (:Entity {name: 'BCryptPasswordEncoder', type: 'CLASS',   tenant_id: 1, app_id: 20001, doc_id: 42})
  CREATE (:Entity {name: 'PasswordEncoder',      type: 'INTERFACE',tenant_id: 1, app_id: 20001, doc_id: 42})
  CREATE (:Entity {name: '生产环境',              type: 'ENV',      tenant_id: 1, app_id: 20001, doc_id: 42})

  (Spring Security) -[:USES {chunk_id: 91001}]-> (BCryptPasswordEncoder)
  (BCryptPasswordEncoder) -[:IMPLEMENTS {chunk_id: 91001}]-> (PasswordEncoder)
  (BCryptPasswordEncoder) -[:RECOMMENDED_FOR {chunk_id: 91001}]-> (生产环境)
```

**Neo4j 节点与关系设计**：

```cypher
// 节点标签: Entity
// 属性: name, type, tenant_id, application_id, document_id, aliases, metadata
// 索引:
CREATE CONSTRAINT entity_name_uniq IF NOT EXISTS
  FOR (e:Entity) REQUIRE (e.tenant_id, e.application_id, e.name) IS UNIQUE;
CREATE INDEX entity_type_idx IF NOT EXISTS FOR (e:Entity) ON (e.type);
CREATE INDEX entity_doc_idx   IF NOT EXISTS FOR (e:Entity) ON (e.tenant_id, e.application_id, e.document_id);

// 关系: 动态标签 (USES, IMPLEMENTS, DEPENDS_ON, ALTERNATIVE_TO, ...)
// 关系属性: chunk_id, weight, metadata
```

**Java 层新增**：

```java
// 图谱检索服务接口
public interface GraphSearchService {
    List<GraphSearchResult> search(
            Long tenantId, Long applicationId,
            Long userId, Long profileId,
            String query,              // 用户输入
            int maxHops,               // 最大跳数 (建议2)
            int topK
    );
}

// Neo4j 实现
public class Neo4jGraphSearchService implements GraphSearchService {

    private final Neo4jClient neo4jClient;  // 或 Neo4jTemplate / Driver

    public List<GraphSearchResult> search(
            Long tenantId, Long applicationId,
            Long userId, Long profileId,
            String query, int maxHops, int topK) {

        // ① 从用户输入中提取实体名（调 LLM 或 NER 服务）
        List<String> entityNames = extractEntities(query);
        // "JWT 的加密算法有哪些替代方案" → ["JWT", "加密算法"]

        // ② Neo4j 图游走：从匹配的实体出发，展开 maxHops 跳
        List<GraphPath> paths = neo4jClient.query("""
                MATCH (start:Entity)
                WHERE start.tenant_id = $tenantId
                  AND start.application_id = $appId
                  AND start.name IN $names
                MATCH path = (start)-[*1..%d]-(related:Entity)
                WHERE related.tenant_id = $tenantId
                  AND related.application_id = $appId
                RETURN path, relationships(path) AS rels
                ORDER BY length(path) ASC
                LIMIT $limit
                """.formatted(maxHops))
                .bind(tenantId).bind(appId).bind(entityNames).bind(topK * 10)
                .fetchAs(GraphPath.class);

        // ③ 收集路径中关系的 chunk_id → PG 回表取原文
        Set<Long> chunkIds = paths.stream()
                .flatMap(path -> path.relationships().stream())
                .map(rel -> rel.chunkId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return chunkMapper.selectActiveChunksByIds(tenantId, applicationId,
                userId, profileId, new ArrayList<>(chunkIds))
                .stream()
                .limit(topK)
                .map(this::toGraphSearchResult)
                .toList();
    }
}
```

**Docker Compose 部署**：

```yaml
# docker-compose.dev.yml 新增 Neo4j:
neo4j:
  image: neo4j:5.20-community
  container_name: agent-neo4j
  ports:
    - "7474:7474"   # HTTP
    - "7687:7687"   # Bolt
  environment:
    - NEO4J_AUTH=neo4j/password
    - NEO4J_PLUGINS=["apoc"]
  volumes:
    - neo4j_data:/data
    - neo4j_logs:/logs
```

**配置项**：

```properties
# Neo4j 连接配置
agent.graph.neo4j.enabled=true
agent.graph.neo4j.uri=bolt://localhost:7687
agent.graph.neo4j.username=neo4j
agent.graph.neo4j.password=password
agent.graph.neo4j.max-hops=2
agent.graph.neo4j.timeout-ms=3000

# 降级策略：Neo4j 不可用时，图谱路返回空，不影响向量+倒排
```

---

## 4. RRF 融合

### 4.1 为什么需要 RRF

三路检索各自返回带分数的结果，但分数的量纲完全不同：

| 检索路径 | 分数类型 | 量纲 |
|---------|---------|------|
| 向量语义 | 余弦相似度 | [0, 1] 连续值 |
| 倒排索引 | BM25 / ts_rank | 无上限正值 |
| 知识图谱 | 路径权重 | 自定义 |

**不能直接加权求和**——因为量纲不同，无法比较。RRF（Reciprocal Rank Fusion）不关心原始分数，只关心排名位置。

### 4.2 RRF 公式

```
RRF_score(d) = Σ 1 / (k + rank_i(d))

其中:
  d        — 候选文档/chunk
  rank_i(d) — 文档 d 在第 i 路检索中的排名 (从1开始)
  k        — 平滑常数，经典值 60
```

### 4.3 计算示例

```
查询: "JWT 的加密算法有哪些替代方案"

向量检索 top5:
  #1 chunk-42 (score 0.89) "JWT 使用 RS256 算法签名..."
  #2 chunk-17 (score 0.76) "Spring Security JWT 配置..."
  #3 chunk-55 (score 0.71) "OAuth2 和 JWT 的关系..."
  #4 chunk-03 (score 0.65) "EdDSA 是 Ed25519 的实现..."
  #5 chunk-88 (score 0.58) "密码学基础：对称与非对称加密"

倒排索引 top5:
  #1 chunk-55 "OAuth2 和 JWT 的关系..."
  #2 chunk-42 "JWT 使用 RS256 算法签名..."
  #3 chunk-99 "JWT Token 的生成和验证..."
  #4 chunk-03 "EdDSA 是 Ed25519 的实现..."
  #5 chunk-11 "HS256 vs RS256 性能对比"

知识图谱 top5 (从 JWT 实体出发2跳):
  #1 chunk-11 "HS256 vs RS256 性能对比"       ← 通过 (JWT)-(RS256)-(HS256) 路径
  #2 chunk-03 "EdDSA 是 Ed25519 的实现..."    ← 通过 (JWT)-(RS256)-(EdDSA) 路径
  #3 chunk-42 "JWT 使用 RS256 算法签名..."
  #4 chunk-66 "密码学算法选型指南..."
  #5 chunk-77 "RFC 7518: JSON Web Algorithms"

RRF 融合计算:
  chunk-03: 向量#4 + 倒排#4 + 图谱#2
    RRF = 1/(60+4) + 1/(60+4) + 1/(60+2) = 0.0156 + 0.0156 + 0.0161 = 0.0473

  chunk-42: 向量#1 + 倒排#2 + 图谱#3
    RRF = 1/(60+1) + 1/(60+2) + 1/(60+3) = 0.0164 + 0.0161 + 0.0159 = 0.0484  ← 最高

  chunk-11: 未在向量top5 + 倒排#5 + 图谱#1
    RRF = 0 + 1/(60+5) + 1/(60+1) = 0 + 0.0154 + 0.0164 = 0.0318

  → 最终排序: chunk-42 > chunk-03 > chunk-55 > chunk-11 > chunk-17

关键观察: chunk-03 在向量和倒排都排#4，但图谱排#2（因为图谱发现了 EdDSA 关联），
         所以在 RRF 融合后排名上升。这就是图谱补充的价值。
```

### 4.4 Java 实现

```java
public class RrfFusion {

    private static final double K = 60.0;

    /**
     * @param rankedResults  各路检索结果，每路已按原始分数降序排列
     * @param topK           最终返回条数
     * @return 融合后的 chunk/reference ID 列表
     */
    public List<Long> fuse(List<List<ScoredResult>> rankedResults, int topK) {
        // key → RRF 累加分数
        Map<Long, Double> rrfScores = new LinkedHashMap<>();

        for (List<ScoredResult> singlePath : rankedResults) {
            for (int rank = 0; rank < singlePath.size(); rank++) {
                Long id = singlePath.get(rank).id();
                double contribution = 1.0 / (K + rank + 1);  // rank 从 0 开始 → +1
                rrfScores.merge(id, contribution, Double::sum);
            }
        }

        // 按 RRF 分数降序排列，返回 topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }
}
```

---

## 5. 与现有架构的集成

### 5.1 检索接口扩展

现有的 `RagSearchService` 和 `MemoryRecallService` 不变——新增的倒排和图谱检索在**实现类内部**并行执行：

```java
// 改造 DefaultPostgresRagEngine.search()：

public List<RagSearchResultDTO> search(
        Long tenantId, ..., String query, int topK, ...) {

    // 三路并行检索
    CompletableFuture<List<ScoredResult>> vectorFuture =
            CompletableFuture.supplyAsync(() -> searchByVector(...));

    CompletableFuture<List<ScoredResult>> keywordFuture =
            CompletableFuture.supplyAsync(() -> searchByKeyword(...));

    CompletableFuture<List<ScoredResult>> graphFuture =
            CompletableFuture.supplyAsync(() -> neo4jGraphSearchService.search(...));

    // 等待所有路径完成（或超时降级）
    List<List<ScoredResult>> allResults = new ArrayList<>();
    allResults.add(vectorFuture.get(2, SECONDS));   // 超时 → 空列表
    allResults.add(keywordFuture.get(2, SECONDS));
    allResults.add(graphFuture.get(3, SECONDS));    // 图谱稍慢

    // RRF 融合
    List<Long> fusedIds = rrfFusion.fuse(allResults, topK);

    // PostgreSQL 回表取完整原文
    return chunkMapper.selectActiveChunksByIds(tenantId, ..., fusedIds)
            .stream()
            .map(this::toSearchResult)
            .toList();
}
```

### 5.2 降级策略

三路并行，任一路失败不影响其他路：

```
向量路径异常 → RRF 只用倒排+图谱
倒排路径异常 → RRF 只用向量+图谱
图谱路径异常 → RRF 只用向量+倒排
三路全异常 → 返回空列表（上层有兜底提示）
```

与现有的降级逻辑完全兼容——外层 `RagSlotSource` 不关心内部是几路检索。

### 5.3 上下文注入层无需改动

`RagSlotSource` 和 `MemorySlotSource` 只依赖 `RagSearchService` / `MemoryRecallService` 接口。新增检索路径在实现内部消化，Slot 注入层代码零改动。

### 5.4 Trace 可观测

每路检索独立记录 Trace Span：

```
rag.search
  ├── rag.vector.search     (已有)
  ├── rag.keyword.tsvector  (新增)
  └── rag.graph.search      (新增)
       ├── rag.graph.ner      (实体提取)
       └── rag.graph.bfs      (图游走)
```

---

## 6. 实施顺序

```
当前状态 (Stage 4 完成):
  Qdrant 向量检索 + PG LIKE 关键词降级（串行）

建议路线 (逐步叠加，每步独立验证):

  ┌─ Step 5A: PG tsvector 倒排索引 (1-2h)
  │   ├─ knowledge_chunks + memories 加 tsvector 列 + GIN 索引
  │   ├─ 新增 PgTsvectorKeywordSearch 实现
  │   ├─ 把串行降级改为向量+倒排并行 → RRF 融合
  │   └─ 验证: 关键词查询质量提升、Trace 可观测
  │
  ├─ Step 5B: Neo4j 知识图谱 (3-4h)
  │   ├─ docker-compose.dev.yml 新增 Neo4j 容器
  │   ├─ RAG ingest 后 LLM 提取实体关系 → 写入 Neo4j
  │   ├─ 新增 Neo4jGraphSearchService
  │   ├─ 三路并行 → RRF 融合完整闭环
  │   └─ 验证: 关系型查询可命中图谱、Neo4j 不可用时降级
  │
  └─ Step 5C: Elasticsearch 替换 PG 全文搜索 (可选, 2h)
      └─ 如果 PG tsvector 在数据量 > 10 万条时性能不够
```

每步完成后跑 `单Agent记忆与RAG后端冒烟测试指南.md` 确认不破坏主链路。

---

## 7. Memory 端的对称改造

记忆召回 (`DefaultMemoryRecallService`) 也做同样的三路并行改造：

| 路径 | Memory 实现 | RAG 实现 |
|------|-----------|---------|
| 向量语义 | `VectorStore.search("memory")` | `VectorStore.search("rag")` |
| 倒排索引 | `PgTsvectorKeywordSearch.search("memory")` | `PgTsvectorKeywordSearch.search("rag")` |
| 知识图谱 | `Neo4jGraphSearchService`（复用同一 Neo4j 图数据库） | 同左 |

Memory 的图谱路径：从用户输入提取实体 → Neo4j 图游走 → 匹配到 `memories` 中提及相同实体的记录 → 图扩展召回。

---

## 8. 不在本方案中实现的内容

- Milvus 替代 Qdrant（Qdrant 已足够）
- Kafka 事件流
- 全文搜索引擎以外的实时索引
- 图神经网络（GNN）嵌入
- 用户手动编辑知识图谱的 UI

---

## 9. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 倒排索引首版用什么 | PG tsvector | 零新组件，GIN 索引解决 LIKE 全表扫描 |
| tsvector 分词字典 | `simple`（先不接中文词典） | 中文分词词典（zhparser）需要额外安装扩展，先跑通再优化 |
| 图谱用什么 | Neo4j 直接落地 | 图查询性能远超 PG 递归 CTE，Docker Compose 同级部署，Spring Data Neo4j 接入成本低 |
| 实体提取方式 | LLM（异步，ingest 时触发） | 比规则 NER 准确，比本地模型轻量 |
| 三路检索执行方式 | 并行 | 总延迟 = max(各路径延迟)，不是求和 |
| 融合算法 | RRF | 无需调参、不关心原始分数量纲 |
| RRF 的 k 值 | 60 | 经典值，Adrian 等人论文推荐 |

> Adrian 论文: Cormack, Clarke, Buettcher. "Reciprocal Rank Fusion outperforms Condorcet and individual rank learning methods." SIGIR 2009.
