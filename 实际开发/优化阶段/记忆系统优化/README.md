# 记忆系统优化文档目录

这个目录现在以 `00-单Agent记忆与RAG统一设计文档.md` 作为唯一主入口。后续开发、评审、实现排期优先阅读主文档；其他文档作为展开材料保留，不再作为第一阅读入口。

## 推荐阅读顺序

1. `00-单Agent记忆与RAG统一设计文档.md`
   - 统一设计入口，覆盖目标、边界、Memory、RAG、混合检索、Context、Trace、实施路线和验收。

2. `记忆系统工作流程.drawio`
   - 可视化流程图，用于快速理解工作流。

3. `记忆系统优化落地验收报告.md`
   - 当前分支已落地能力、验证命令、审查修复点和后续增强边界。

4. `单Agent记忆与RAG后端冒烟测试指南.md`
   - 开发完成后按这个跑自动化测试和 HTTP 冒烟。

5. `检索评测集.md`
   - 固定 Hit@K / MRR 评测口径、业务 corpus、业务 case 和 CI 门禁，用于后续 HyDE、MQE、Neo4j 等检索增强上线前对比。

## 展开材料

| 文档 | 用途 |
|---|---|
| `单Agent记忆与RAG设计方案.md` | 基础设计详细展开，包含早期阶段拆解和 RAG MVP 细节 |
| `单Agent记忆与RAG融合方案.md` | 原融合方案草稿，核心内容已并入主文档 |
| `单Agent记忆与RAG混合检索增强方案.md` | Stage 5 混合检索、PG tsvector、Neo4j、RRF 的详细方案 |
| `单Agent记忆系统业界对照与优化建议.md` | 业界对照、优化优先级和增强能力来源 |
| `单Agent记忆与RAG-Embedding复用与并行优化方案.md` | Embedding 复用、Memory/RAG 并行检索的详细方案 |
| `单Agent记忆与RAG实现护栏补充方案.md` | 隐私、冲突、一致性、线程池、评测、引用约束等工程护栏 |

## 当前口径

- Memory 和 RAG 分表、分 collection、分 source_type。
- PostgreSQL 是事实源；Qdrant、PG tsvector、Neo4j 是派生索引。
- Memory 不作为 citation 来源；RAG 才作为外部知识引用。
- 高级能力 HyDE、MQE、Reranker、Neo4j 默认关闭，必须通过评测后再开启。
- 检索评测已提供脚本入口 `scripts/run-retrieval-eval.ps1`、业务评测集和 GitHub Actions 工作流 `Retrieval Evaluation`；后续检索策略改动应先过本地/CI 门禁。
- 目录里的旧文档不删除，避免丢失细节；但实现时以 `00-单Agent记忆与RAG统一设计文档.md` 为准。
