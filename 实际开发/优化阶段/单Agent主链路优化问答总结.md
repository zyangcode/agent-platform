# 单 Agent 主链路优化问答总结

本文沉淀围绕 `单Agent主链路优化亮点设计.md` 和 `docs/single-agent-optimization-flow.drawio` 讨论过的关键问题、结论和后续落地影响。

## 1. 现在是否要按 `single-agent-optimization-flow.drawio` 优化

重点结论：

- 是，大方向是把当前“能跑通的基础单 Agent”优化成图里的工程化 Agent Runtime。
- 这张图不是替换 `Web -> Gateway -> Core` 主架构，而是在 Core 的 Agent Runtime 内部补强上下文、RAG、ReAct 循环、工具执行和流式输出。
- 实现时不应一次性全改，应按步骤推进：token 级流式输出、上下文压缩、工具链收口、Skill Jar 热加载、RAG + Qdrant、前端高级化、飞书机器人扩展。

落地影响：

- 后续开发前需要补正式的优化阶段执行文档，把图中的目标拆成可验证 Step。
- 当前图和说明文档是目标设计，不代表所有能力已经实现。

## 2. `pgvector` 是什么

重点结论：

- `pgvector` 是 PostgreSQL 的向量扩展，不是独立数据库。
- 它让 PostgreSQL 可以存储 embedding 向量，并按相似度检索相近文本片段。
- 优点是部署简单，可以复用现有 PostgreSQL。
- 缺点是简历表达上更像“PostgreSQL 扩展能力”，不如独立向量数据库有辨识度。

落地影响：

- 如果只追求考核 Demo 稳定，`PostgreSQL + pgvector` 是轻量方案。
- 如果要作为长期项目和简历亮点，优先考虑独立向量数据库。

## 3. 是否有直接就是向量数据库的方案

重点结论：

- 有，典型选择包括 Qdrant、Milvus、Weaviate、Chroma。
- Qdrant：轻量、部署简单、API 清晰，适合当前 Spring Boot Agent 平台。
- Milvus：适合大规模向量检索和生产集群，但当前项目接入偏重。
- Weaviate：功能完整，支持对象模型和混合检索，但产品体系也较重。
- Chroma：适合 Python RAG 原型，不适合作为当前 Java/Spring Boot 主项目的核心依赖。

落地影响：

- 本项目优化目标调整为 Qdrant 独立向量数据库。
- PostgreSQL 继续负责业务元数据、租户、权限、文档原文、Trace 和 Token Usage。

## 4. 为什么从 pgvector 改成 Qdrant

重点结论：

- 项目不能只按考核最省事方案设计，还要考虑后续简历表达和长期演进。
- Qdrant 更能体现独立向量检索服务、RAG 工程化、多租户 payload filter、向量召回链路等能力。
- Qdrant 部署和接入成本低于 Milvus，更适合当前项目复杂度。

优化后的职责划分：

```text
PostgreSQL
  -> knowledge_document
  -> knowledge_chunk 原文
  -> 租户 / 应用 / Profile / 权限关系
  -> Trace / Token Usage

Qdrant
  -> chunk embedding
  -> payload: tenantId/applicationId/profileId/documentId/chunkId
  -> topK 语义召回
```

落地影响：

- `docs/single-agent-optimization-flow.drawio` 已从 `RAG/pgvector` 改成 `RAG/Qdrant`。
- `实际开发/单Agent主链路优化亮点设计.md` 已同步成 Qdrant 版本。

## 5. 两份设计文档是否对应

重点结论：

- 是，对应同一套目标链路。
- `实际开发/单Agent主链路优化亮点设计.md` 是文字版设计说明。
- `docs/single-agent-optimization-flow.drawio` 是图形版主链路流程图。

它们共同表达的目标链路：

```text
Web/Gateway
  -> Core Context/RAG
  -> ReAct Loop
  -> 并行 Tool 执行
  -> observation 回写
  -> 下一轮循环 / 最终答案
```

落地影响：

- 后续改图时也要同步改文字说明。
- 后续改文字设计时，也要检查图中是否有冲突。

## 6. 相比之前版本主要优化什么

重点结论：

| 维度 | 之前版本 | 优化目标版本 |
|---|---|---|
| 输出方式 | SSE 事件级输出，`message` 常一次性返回 | token 级 `message_delta` 实时输出 |
| Agent 循环 | 有 ReAct 雏形 | 显式 Round Start / Parse / Tool / Observation / Next Round |
| 上下文管理 | 基础裁剪 | Micro Compact / Auto Compact / Fallback Trim |
| 工具选择 | 单 Agent 里仍有硬编码工具预选痕迹 | ToolResolver 统一生成工具声明 |
| 工具执行 | Skill/MCP 入口相对分散 | ToolDispatcher 统一分发 |
| 多工具调用 | 更偏单工具串行 | 支持 Fork/Join 并行工具执行 |
| 工具安全 | 基础权限概念 | Risk Guard 和高危工具确认 |
| RAG | 未进入主链路 | Query Embedding -> Qdrant -> RAG Context Composer |
| 结果清洗 | 可能泄漏内部执行清单或 JSON | Final Answer Builder 清洗用户可读答案 |
| 可观测性 | Trace 基础记录 | context/rag/compact/model/tool 等关键 Span |

一句话总结：

```text
之前版本是能跑通的基础单 Agent。
优化目标版本是有流式、有预算、有工具治理、有 RAG、有并行工具、有降级清洗的工程化 Agent Runtime。
```

## 7. Skill Jar 热加载在图里怎么体现

重点结论：

- Skill Jar 热加载属于 Skill 子系统增强，不发生在每一轮 ReAct 中。
- 它应作为管理侧旁路链路接入 `ToolResolver / ToolDispatcher`。
- 运行时不直接处理 Jar 文件，也不直接操作 ClassLoader。

管理侧链路：

```text
Jar Upload
  -> Manifest / Interface Validator
  -> URLClassLoader 按版本隔离加载
  -> Skill Registry 注册 SkillDefinition
  -> currentVersion 切换 / 旧版本可回滚
  -> ToolResolver 下一次构建工具声明时可见
  -> ToolDispatcher 执行时通过 Registry / ClassLoader 调用 Jar Skill
```

落地影响：

- `single-agent-optimization-flow.drawio` 已新增 `E. Skill Jar 热加载与注册` 旁路区。
- 旁路区通过虚线连接回 `ToolResolver` 和 `21a Skill 调用`，表示“管理侧先加载注册，运行时自然可见”。

## 8. 工具并行执行是否会开启多个线程

重点结论：

- 会使用多个工作线程并行执行工具，但不能无脑为每个工具 `new Thread`。
- 应通过受控线程池执行，例如 Spring `ThreadPoolTaskExecutor` 或 bounded `ExecutorService`。
- 并行只适用于互不依赖、无共享可变状态冲突、未超过并发上限的工具请求。

推荐模型：

```text
Tool Request Batch
  -> 判断哪些工具互不依赖
  -> 提交到 bounded ExecutorService
  -> CompletableFuture.allOf 等待
  -> 按原请求顺序合并 observation
  -> 写回 messages
```

关键约束：

- `maxParallelTools` 控制最大并发工具数。
- `maxToolCalls` 控制单轮或单次请求的工具总数。
- 单工具要有 timeout。
- 全局 Agent run 要有 timeout。
- 高危工具先过 Risk Guard，确认后再执行。
- 工具结果不能谁先返回谁先写入上下文，应按模型原始请求顺序合并。
- 单个工具失败只写失败 observation，不应拖垮整个 Agent 请求。

落地影响：

- 后续实现 Fork/Join 时要引入受控线程池、超时、Trace 绑定、顺序合并和失败隔离。
- 并行执行是性能优化和工程能力亮点，但必须先保证可控性和确定性。

## 9. drawio 类型 Skill、工具类型 Skill、经验型 Skill 怎么区分

重点结论：

- Codex 本地的 `drawio-skill` 是开发助手侧能力，用来帮我们生成或修改 `.drawio` 文件，不属于平台运行时的 Skill。
- 平台运行时的配置型 Skill / Jar Skill 是工具能力，会进入模型可调用 tools 列表，并通过 `ToolResolver -> ToolDispatcher` 执行。
- 经验型 Skill / Prompt Skill 是上下文能力，只提供领域方法论、步骤、规范、示例和常见错误，不执行代码，不进入 ToolDispatcher。
- 如果以后平台要做类似 Codex 本地 skill 的能力，应该单独创建“经验型 Skill”功能，不要和 Jar 热加载混在一起。

三类平台能力边界：

| 类型 | 是否执行代码 | 是否进入 tools | 主要链路 |
|---|---|---|---|
| 配置型 Skill | 否或弱执行 | 是 | ToolResolver / ToolDispatcher |
| Jar Skill | 是 | 是 | Jar 热加载 / Skill Registry / ToolDispatcher |
| 经验型 Skill | 否 | 否 | ExperienceSkillResolver / API Messages Composer |

经验型 Skill 运行时链路：

```text
用户请求
  -> ExperienceSkillResolver 按 profile/domain/关键词召回
  -> Prompt/Context Composer 压缩为 experience refs
  -> 注入 apiMessages 的 context block
  -> 模型基于经验生成回答或决定后续工具调用
```

落地影响：

- `single-agent-optimization-flow.drawio` 已新增 `F. 经验型 Skill 管理` 旁路区。
- 经验型 Skill 应建立自己的编辑、存储、召回、token 预算和作用域控制。
- Jar 热加载只负责 Java 工具扩展，不承载经验文档和 Prompt 方法论。

## 10. 当前图和文档已经同步的改动

已同步：

- 从 `PostgreSQL + pgvector` 改为 `Qdrant 独立向量数据库 + PostgreSQL 元数据`。
- 图中新增 `E. Skill Jar 热加载与注册`。
- 图中新增 `F. 经验型 Skill 管理`，明确它注入 `apiMessages`，不走 ToolDispatcher。
- 文档中补充热加载管理链路与边界说明。
- 文档中补充经验型 Skill / Prompt Skill 的结构、运行链路和边界说明。
- 工具并行执行明确为 Fork/Join 模型，后续实现要走受控线程池。
- 新增 Hermes 主链路参考分析文档：`实际开发/单Agent优化-Hermes主链路参考分析.md`，用于沉淀可借鉴点和不照搬边界。

待后续补充：

- 正式优化阶段目录和执行顺序文档。
- token 级流式输出接口设计。
- ToolResolver / ToolDispatcher 收口的代码级 Step。
- 经验型 Skill 的数据表、召回策略、优先级、token budget 和前端管理页设计。
- Qdrant RAG 的数据表、collection、payload、embedding 模型维度和检索接口设计。

## 11. Hermes 主链路哪些地方值得参考

参考文件：

`D:\study\蓝山最终考核项目\hermes-agent-main\hermes-dialog-main-flow.drawio`

重点结论：

- 借鉴 Hermes 的结构思想，不照搬 Python 大文件式实现。
- 最值得吸收的是：单核心多入口复用、API messages 与持久化 messages 分离、稳定 system prompt、provider adapter 独立、工具 registry、工具调用防御、安全并发、SessionDB 可恢复性和 hook 机制。
- 当前项目应保持 Spring Boot 多模块边界，把这些能力拆到 `core.agent`、`core.context`、`core.model`、`core.agent.tool`、`core.skill`、`core.trace`。

建议落地顺序：

```text
token 级流式输出
  -> API messages 与持久化 messages 分离
  -> 上下文预算与压缩
  -> ToolResolver / ToolDispatcher / ToolCallValidator 收口
  -> 安全并发工具执行
  -> Skill Jar 热加载
  -> RAG + Qdrant
  -> Tool Search 桥接
```
