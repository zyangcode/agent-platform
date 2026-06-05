# 单 Agent 主链路优化亮点设计

## 目标

在不改变当前 `Web -> Gateway -> Core AgentRuntime` 主架构的前提下，把后续优化亮点收敛到**单 Agent 主链路**里。单 Agent 的核心不是预设固定步骤，而是让模型在受控 Harness 中循环决策：

```text
构建上下文 -> 模型生成 -> 判断最终答案或工具调用
  -> 工具执行 -> observation 写回上下文 -> 下一轮模型生成
```

本文只同步设计和图，不修改当前代码。

配套图见 [single-agent-optimization-flow.drawio](../docs/single-agent-optimization-flow.drawio)。

## 当前状态与边界

当前项目已经有基础链路：

- `Web -> Gateway -> Core AgentRuntime` 路由成立。
- Gateway 已承担鉴权、Trace、Token 配额、敏感数据治理、SSE 输出。
- Core 已有 Profile、Skill、MCP、Memory、模型调用、对话落库。
- Agent Runtime 已有 ReAct 雏形：模型输出工具调用标记后，Runtime 执行 Skill/MCP，并把结果写回上下文。

当前仍需优化：

- SSE 还不是 token 级实时输出。
- 单 Agent Runtime 中仍有 `calculator/weather/search` 这类硬编码工具预选。
- 上下文管理偏硬裁剪，缺少 micro compact、auto compact 和失败降级。
- API 调用消息和持久化消息尚未明确分离，RAG、memory、provider 兼容字段容易污染历史。
- ToolResolver/ToolDispatcher 尚未完全成为 Runtime 唯一工具入口。
- 工具调用前缺少统一 ToolCallValidator，模型幻觉工具名、坏 JSON 参数、空参数等场景还不够稳。
- 工具并发还缺少 ToolExecutionPlanner，不能只按“多个工具”就盲目并行。
- Codex 本地开发 Skill 和平台运行时 Skill 容易混淆，尚未区分“工具型 Skill”和“经验型 Skill”。
- RAG/向量库、Jar Skill 热加载、高危工具确认还没有进入单 Agent 主链路闭环。

能力边界：

- Gateway 只做入口、治理、协议转换和 SSE，不拼 Prompt、不决定工具、不执行 Skill/MCP。
- Core AgentRuntime 负责上下文、模型循环、工具执行调度、最终答案清洗。
- `conversation_message` 是干净的持久化历史；`apiMessages` 是本轮模型调用副本，可临时注入 memory、RAG、tool specs 和 provider 兼容字段。
- System Prompt 应尽量稳定，动态上下文优先进入临时 context block，以便后续提升 prompt cache 命中率。
- 经验型 Skill / Prompt Skill 是上下文能力，不是工具调用能力；它由 Context Builder 注入 `apiMessages`，不进入 ToolDispatcher。
- SubAgent/Task 工具属于可选增强，不等同于 Team Planner/Executor/Reviewer。
- 向量检索建议采用 Qdrant 独立向量数据库，PostgreSQL 继续保存业务元数据、文档元信息、chunk 文本、租户权限关系和 Trace。

## 精细流程

### A. 入口与 Gateway 治理

1. 用户在 Web Chat 输入需求，前端通过 SSE 接收事件。
2. Web Controller 带 JWT 转发到 Gateway。
3. Gateway 创建 SSE 输出流。
4. Gateway 治理链执行：
   - 创建 `traceId`
   - Token 配额预扣
   - 请求敏感数据扫描
   - 告警兜底

关键箭头语义：

- `用户消息 + profileId + skill/mcp 选择`：前端传给 Web。
- `内部请求，保持 SSE`：Web 转发给 Gateway。
- `AgentRunCommand`：Gateway 治理后交给 Core 的运行命令。
- `message_delta`：模型 token 增量实时从 Core/Gateway 推给 Web。

### B. Core 上下文与 RAG

5. `AgentRuntimeService.run` 进入单 Agent 总控。
6. `AgentContextBuilder` 组装：
   - Platform System Prompt
   - Profile Prompt
   - History
   - Memory
   - 干净的 `conversation_message` 快照
   - 稳定的 System Prompt 快照
7. `ExperienceSkillResolver` 召回经验型 Skill：
   - 领域经验
   - 操作步骤
   - 注意事项
   - 输出格式规范
   - 常见错误
8. `ToolResolver` 生成当前可用工具声明：
   - Skill
   - MCP
   - 参数 Schema
   - 风险等级
9. `Query Embedding` 把用户问题转成查询向量。
10. `Qdrant` 作为独立向量数据库：
   - Qdrant collection 存文档分段 embedding 和 payload
   - payload 保存 `tenantId`、`applicationId`、`profileId`、`documentId`、`chunkId` 等过滤字段
   - 用向量相似度 + payload filter 检索 topK
   - PostgreSQL 保存 `knowledge_document`、`knowledge_chunk`、权限关系和 chunk 原文
11. `RAG Context Composer` 把 topK 知识片段压成参考资料，并进入 token 预算。
12. 输出 `Context Budget Snapshot`：
   - `messages`
   - `tool specs`
   - `experience refs`
   - `rag refs`
   - `tokenEstimate`
   - `apiMessages`

注意：RAG 检索结果不能无限拼接，必须和历史、工具声明一起进入上下文预算。

API 调用消息和持久化消息必须分离：

```text
conversation_message
  -> 持久化历史
  -> 保存用户消息、助手消息、必要的 tool observation 摘要
  -> 不直接写入 RAG 原文、provider hack、临时压缩说明

apiMessages
  -> 本轮模型调用副本
  -> 可临时注入 memory、RAG refs、tool specs、token budget 提示、provider 兼容字段
  -> 调用结束后不原样落库
```

这样可以避免上下文增强污染会话历史，也方便后续 resume、审计、Trace 回放和 prompt cache 优化。

### 经验型 Skill / Prompt Skill

经验型 Skill 用于承载类似 Codex 本地 Skill 的可复用方法论，但它属于平台运行时的上下文增强能力，不是工具调用能力。

三类 Skill 应明确区分：

| 类型 | 是否执行代码 | 作用 | 进入链路 |
|---|---|---|---|
| 配置型 Skill | 否或弱执行 | HTTP API、搜索、天气等配置化工具 | ToolResolver / ToolDispatcher |
| Jar Skill | 是 | Java 代码工具、业务计算、复杂集成 | ToolResolver / ToolDispatcher |
| 经验型 Skill | 否 | 领域经验、步骤、规范、注意事项、示例 Prompt | ExperienceSkillResolver / API Messages Composer |

经验型 Skill 示例结构：

```text
experience_skill
  id
  name
  description
  domain
  content_md
  trigger_keywords
  profile_scope
  user_scope
  enabled
```

运行时链路：

```text
用户请求
  -> ExperienceSkillResolver 按 profile/domain/关键词召回经验 Skill
  -> Prompt/Context Composer 压缩成 experience refs
  -> 注入 apiMessages 的 context block
  -> 模型基于经验回答
```

边界：

- 经验型 Skill 不出现在模型可调用 tools 数组中。
- 经验型 Skill 不走 ToolDispatcher，不产生 tool_call。
- 经验型 Skill 受 token budget 限制，过长时必须摘要或截断。
- 经验型 Skill 可以和 RAG 同时存在，但一个是经验方法论，一个是知识检索资料。

## 主模型 ReAct 循环

显式循环如下：

```text
Round Start
  -> Micro Compact
  -> Token Budget Decision
      -> 超阈值：Auto Compact / Fallback Trim
      -> 未超阈值：直接继续
  -> Main Model Streaming Call
  -> Parse Assistant Output
      -> 最终答案：Final Answer Builder -> message/done
      -> 工具调用：Tool Request Batch -> 并行工具执行
  -> Tool observations 写回 messages
  -> Loop Limit Decision
      -> 未超限：回到 Round Start
      -> 超限：降级为当前最优答案/风险说明
```

### 12. Round Start

每一轮先检查整体约束：

- `maxRounds`
- `maxToolCalls`
- `timeout`
- `token danger ratio`

### 13. Micro Compact

作用：每轮模型调用前做低成本清理，避免脏上下文进入模型。

处理对象：

- 过长 tool observation
- 过长 RAG 片段
- 异常输出
- 大 JSON 结果

策略：保留关键前缀和截断说明，不直接整段删除。

### 14-15. Token Budget 与 Auto Compact

Token 预算计算范围：

- System Prompt
- Profile Prompt
- Memory
- History
- Tool Specs
- Experience refs
- RAG refs
- 当前用户输入
- 上轮 observation

超过阈值时：

- 旧历史摘要化
- 保留最近 N 条原文
- 摘要失败则 fallback trim，并注入系统提示说明上下文已降级

### 16. Main Model Streaming Call

作用：

- 调用主模型。
- token chunk 通过回调形成 `message_delta` SSE。
- 同时聚合完整 assistant message，供后续解析、落库和记忆使用。

模型调用不直接写在 Runtime 里，应经过 `core.model` 的 Provider Adapter：

```text
ModelInvokeCommand / apiMessages
  -> Provider Adapter
      -> OpenAI Compatible
      -> Spring AI ChatClient
      -> mock-chat
  -> Streaming 优先
      -> 失败时按策略 fallback 到 non-streaming 或降级响应
  -> usage / finishReason / raw response 统一解析
```

边界：

- `core.model` 负责 provider 参数、streaming token callback、usage 解析。
- `core.model` 不决定是否调用工具，不接管 ReAct 循环。
- Agent Runtime 负责聚合 assistant message、解析工具调用和推进下一轮。

### 17. Parse Assistant Output

模型输出分三类：

- 最终答案：进入 Final Answer Builder。
- 单个工具调用：进入 Tool Request Batch。
- 多个工具调用：进入 Tool Request Batch，并可并行执行。

非法输出应进入修正或降级路径，不能把内部草稿直接返回用户。

工具调用解析后必须先进入 `ToolCallValidator`：

```text
ToolCallValidator
  -> 校验工具名是否存在
  -> 校验工具是否已授权给当前上下文
  -> 校验 JSON 参数是否符合 schema
  -> 空参数归一化为 {}
  -> 坏 JSON / 未知工具 / 未授权工具回填 tool error observation
```

这样模型有机会在下一轮自我修正，而不是把异常栈、内部 JSON 或执行清单直接返回用户。

### 18. Final Answer Builder

作用：只返回用户可读答案。

必须过滤：

- `User request`
- `Goal`
- `Execution results`
- `task-*`
- 工具原始 JSON
- mock search 结果
- Reviewer/Executor 内部错误

当工具链失败时，返回简洁降级答案，而不是返回内部执行清单。

## 并行工具执行

工具调用不应画成单一路径。模型一次响应可能返回多个互不依赖的工具调用，应走 Fork/Join。

```text
Tool Request Batch
  -> ToolCallValidator
  -> ToolExecutionPlanner
  -> Fork
      -> Skill 调用
      -> MCP 调用
      -> Task/SubAgent 调用（可选增强）
  -> Risk Guard
  -> ToolDispatcher
  -> Tool Result Normalizer
  -> Join
  -> observation 列表写回 messages
```

### Fork

作用：把同一次模型响应中的多个独立工具请求拆开执行。

并行前提：

- 工具之间没有依赖关系。
- 没有共享可变状态冲突。
- 没有超过 `maxToolCalls`。
- 工具被 `ToolExecutionPlanner` 判定为可安全并行。

### ToolExecutionPlanner

作用：决定工具请求是串行、并行还是分批执行。

判断依据：

- `readOnly=true` 的工具更容易并行。
- `resourceKeys` 不重叠时可以并行，例如不同文档、不同外部资源。
- 写操作、高危工具、同资源冲突工具必须串行。
- 超过 `maxParallelTools` 时分批或排队。

执行方式：

```text
Tool Request Batch
  -> ToolExecutionPlanner
  -> sequential executor 或 bounded parallel executor
  -> CompletableFuture.allOf / ThreadPoolTaskExecutor 等待
  -> 按原 tool_call 顺序合并 observation
```

注意：不能每个工具调用都 `new Thread`。必须使用受控线程池、单工具 timeout、全局 run timeout、Trace span 绑定和失败隔离。

### Skill 调用

作用：执行配置型 Skill 或 Jar Skill。

Jar Skill 热加载属于 Skill 子系统：

- Developer/Admin 上传 Jar。
- 校验通过后进入 ENABLED/LOADED。
- URLClassLoader 隔离加载。
- 新版本加载成功后切换 `currentVersion`。
- Runtime 下一次构建上下文时自然看到新 Skill。

热加载发生在管理链路，不发生在每一轮 ReAct 中：

```text
Jar Upload
  -> Manifest / Interface Validator
  -> URLClassLoader 按版本隔离加载
  -> Skill Registry 注册 SkillDefinition
  -> currentVersion 切换 / 旧版本可回滚
  -> ToolResolver 下一次构建工具声明时可见
  -> ToolDispatcher 执行时通过 Registry / ClassLoader 调用 Jar Skill
```

边界：

- 加载失败只把对应版本标记为 FAILED，不影响旧版本继续运行。
- Runtime 不直接处理 Jar 文件，也不直接操作 ClassLoader。
- Runtime 只看 `ToolResolver` 输出的工具声明，并通过 `ToolDispatcher` 执行。
- Jar 热加载是 Skill 市场和工具执行能力的增强，不改变单 Agent ReAct 主循环结构。

### MCP 调用

作用：调用外部 MCP 工具。Runtime 不直接感知外部协议细节，只通过 `ToolDispatcher` 收到统一结果。

### Task/SubAgent 调用

这是可选增强，不是当前必须项，也不是 Team 模式。

作用：

- 把复杂子任务委托给独立上下文的小 Agent。
- 子 Agent 使用裁剪后的工具集。
- 防递归：子 Agent 不暴露 task 工具。

边界：

- SubAgent 仍服务于单 Agent 的一个工具调用。
- Team 模式才是 Planner/Executor/Reviewer 三角色协作。

### Risk Guard

按风险等级处理：

- LOW：直接执行。
- MEDIUM：记录 Trace，可配置是否确认。
- HIGH：必须确认或策略阻断。

Web 场景不能照搬 CLI 的 y/n/a，应通过 SSE 发 `tool_confirm_required`，前端确认后继续。

### ToolDispatcher

目标：Runtime 不再直接依赖 `SkillExecutor` / `McpToolExecutor`。

职责：

- 统一分发 Skill/MCP/SubAgent。
- 校验工具名是否存在。
- 捕获工具异常。
- 输出统一 `ToolDispatchResult`。
- 通过 Registry / handlerRef 执行具体工具，Runtime 不感知 Skill、MCP、Jar ClassLoader 细节。

### Tool Result Normalizer

作用：把工具结果统一成 observation。

规则：

- 成功：写入精简后的结果。
- 空结果：写 `[empty]`。
- 失败：写错误摘要。
- 工具崩溃：写 `[tool crash]`，让模型换策略。

### Join

作用：

- 等待并行工具全部完成。
- 按原工具请求顺序合并 observation。
- 写回 `messages`。
- 进入下一轮 Round Start。

## 存储与治理观测

存储侧：

- `conversation_message`：用户消息、助手消息、工具 observation。
- `memory`：长期记忆和会话摘要候选。
- `experience_skill`：经验型 Skill 的 Markdown 内容、触发词、作用域和启用状态。
- `knowledge_document` / `knowledge_chunk`：RAG 文档元信息、分段文本、权限关系和 Qdrant point id。
- `Qdrant collection`：RAG 分段 embedding 和 payload，用于语义召回。
- `token_usage`：模型 usage、估算 usage、结算状态。

Trace Span 建议覆盖：

- `context.build`
- `experience.resolve`
- `api.messages.compose`
- `rag.search`
- `compact.micro`
- `compact.auto`
- `model.invoke`
- `model.provider.adapter`
- `tool.validate`
- `tool.plan`
- `tool.execute`
- `final.answer.build`

Hook 建议保持轻量：

- `preModelCall`
- `postModelCall`
- `preToolCall`
- `postToolCall`
- `postFinalAnswer`

Hook 可挂 Trace、Token usage、敏感数据扫描、告警、memory sync 和成本统计，但不应反向控制太多主流程，避免 Agent Runtime 变得难以理解。

敏感数据扫描范围：

- 用户请求
- 工具入参
- Skill/MCP 返回
- RAG 片段
- 模型最终响应
- 记忆写入
- Trace/日志摘要

## 推荐落地顺序

1. Token 级流式输出。
2. API messages 与持久化 messages 分离。
3. 上下文压缩和 Token 预算。
4. ToolResolver + ToolDispatcher + ToolCallValidator 收口，删除硬编码工具预选。
5. ToolExecutionPlanner + 受控并发工具执行。
6. 经验型 Skill / Prompt Skill。
7. Skill Jar 热加载。
8. RAG + Qdrant 独立向量数据库最小闭环。
9. 高危工具确认。
10. 可选 Task/SubAgent 工具。
11. 前端高级化。
12. 飞书机器人扩展。

这个顺序的原因：

- 流式和上下文预算先保证主链路体验与稳定性。
- API messages 分离是上下文压缩、RAG、provider 兼容和 prompt cache 的基础。
- 工具链收口后，Jar Skill、MCP、SubAgent 才能作为统一工具能力接入。
- 安全并发必须在工具校验和资源冲突判断之后做，不能盲目并行。
- 经验型 Skill 应先于 RAG/Jar 的复杂实现落地，因为它主要影响 Context Builder 和 Prompt Composer，能快速改善领域回答质量。
- RAG 必须受 token 预算约束，否则会把上下文撑爆。
- 前端和飞书不应早于核心 Agent 运行质量。
