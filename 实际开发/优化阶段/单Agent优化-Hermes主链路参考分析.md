# 单 Agent 优化：Hermes 主链路参考分析

参考文件：`D:\study\蓝山最终考核项目\hermes-agent-main\hermes-dialog-main-flow.drawio`

本文只吸收 Hermes 对话主链路的设计思想，不照搬它的 Python 大文件实现和 CLI/TUI 细节。当前项目仍坚持 Spring Boot 多模块、`Web -> Gateway -> Core`、PostgreSQL、SSE、Trace、Token 配额和 Skill/MCP 的既有边界。

## 1. Hermes 主链路概括

Hermes 图中的主链路可以抽象为：

```text
CLI / TUI / Gateway
  -> AIAgent
  -> run_conversation()
  -> 拷贝历史 messages + 追加 user message
  -> 恢复 / 构建稳定 system prompt
  -> 上下文阈值判断与压缩
  -> 构造 API messages
  -> provider transport kwargs
  -> LLM streaming / fallback
  -> 解析 assistant_message
      -> 无 tool_calls：final_response
      -> 有 tool_calls：校验工具名和 JSON 参数
          -> 顺序或并发 executor
          -> tool registry dispatch
          -> tool result 回填 messages
          -> 继续下一轮
  -> SessionDB / hooks / usage / reasoning 持久化
  -> 回传入口层
```

对应当前项目，应该吸收的是：

- 多入口共用一个 Agent Runtime。
- API 调用消息和持久化消息分离。
- 稳定 system prompt 和临时上下文注入分离。
- provider adapter 只负责模型协议，不接管 Agent loop。
- 工具 registry / resolver / dispatcher 收口。
- 工具调用前做名称、参数、权限和并发安全校验。
- tool result 必须按协议回填，保证下一轮模型调用可恢复。
- 每轮结束记录 exit reason、usage、tool turn、最后消息角色等观测字段。

## 2. 最值得借鉴的设计点

### 2.1 单核心，多入口复用

Hermes 有 CLI、TUI、Gateway 三类入口，但核心都进入 `run_conversation()`。

当前项目可对应为：

```text
Web Chat
OpenAI Compatible API
后续 Feishu Bot
  -> Gateway / Web 入口治理
  -> Core AgentRuntimeService
```

落地原则：

- 入口层只处理鉴权、协议、SSE、平台消息和中断。
- Agent 状态、上下文、工具调用、模型调用、重试和恢复都在 Core。
- 不为每个入口复制一套 Agent loop。

### 2.2 API messages 和持久化 messages 分离

Hermes 图里明确有“复制历史 messages”“构造 API messages”“临时注入 memory/plugin context，不污染持久化历史”。

当前项目应该明确两类消息：

```text
conversation_message
  -> 持久化历史
  -> 干净、可恢复、可审计
  -> 保存用户消息、助手消息、必要的 tool observation 摘要

apiMessages
  -> 单次模型调用副本
  -> 可临时注入 memory、RAG、tool specs、压缩说明、provider 兼容字段
  -> 调用结束后不原样落库
```

落地价值：

- RAG 片段不会污染原始会话。
- provider hack 不会进入审计历史。
- 后续 resume、搜索、Trace 回放更稳定。

### 2.3 稳定 system prompt + 临时 user/context block

Hermes 复用 DB 快照里的稳定 system prompt，以提高 prompt cache 命中率。

当前项目可借鉴：

```text
system prompt
  -> 平台固定规则
  -> Profile 固定规则
  -> 尽量稳定

context block
  -> memory
  -> RAG refs
  -> tool specs
  -> 当前轮约束
  -> 放入 apiMessages 的临时上下文
```

不建议每轮动态拼一个完全不同的 system prompt。动态上下文应该优先进入 user/context block 或专门的 context message。

### 2.4 Provider transport adapter 独立

Hermes 的 `build_api_kwargs()` 按 `api_mode` 选择 transport。

当前项目对应：

```text
core.model
  -> ModelInvokeService
  -> Provider Adapter
  -> OpenAI compatible / Spring AI / mock-chat
```

落地原则：

- `core.model` 可以处理请求构造、响应解析、usage、streaming token adapter。
- `core.model` 不决定是否调用工具，不接管 ReAct loop。
- Agent Runtime 通过统一 `ModelInvokeCommand` / streaming callback 使用模型。

### 2.5 工具注册中心化

Hermes 使用 `tools.registry` 管理 schema、handler、check_fn、toolset。

当前项目可以升级为：

```text
AgentToolRegistry / AgentToolResolver
  -> name
  -> sourceType: SKILL / MCP / SUB_AGENT
  -> schema
  -> handlerRef
  -> riskLevel
  -> readOnly
  -> resourceKeys
  -> availableCheck
```

落地价值：

- 主循环只看到统一工具视图。
- 新增 Jar Skill / MCP / SubAgent 不改主循环。
- 工具授权、风险等级、参数 schema、并发安全都能统一治理。

### 2.6 工具调用防御

Hermes 在工具执行前做：

- 校验 tool 名称。
- 尝试修复模型幻觉工具名。
- 校验 JSON 参数。
- 空参数转 `{}`。
- 坏 JSON 回填 tool error。
- 保持 `assistant(tool_calls)` 和 `tool_result` 配对。

当前项目应吸收成：

```text
ToolCallValidator
  -> validateName
  -> validateAuthorized
  -> validateJsonArgs
  -> normalizeEmptyArgs
  -> buildToolErrorObservation
```

这样可以避免把内部异常、原始 JSON 或 task 执行清单直接吐给用户。

### 2.7 安全并发工具执行

Hermes 不并发所有工具，而是判断“只读工具或路径不重叠”后才进 `ThreadPoolExecutor`。

当前项目可泛化为：

```text
ToolExecutionPlanner
  -> readOnly 且 resourceKeys 不冲突：可并行
  -> 写操作 / 高危 / 同资源冲突：串行
  -> 超过 maxParallelTools：分批或排队
```

实现原则：

- 使用受控线程池，例如 Spring `ThreadPoolTaskExecutor`。
- 工具有单独 timeout。
- Agent run 有全局 timeout。
- 结果按原始 tool_call 顺序合并，而不是谁先返回谁先写入。
- Trace 需要绑定每个 tool call 的 span。

### 2.8 Tool Search 桥接

Hermes 用工具搜索/描述/调用桥接，避免把大量工具 schema 全塞进 prompt。

当前项目可以作为中后期增强：

```text
tool_search
tool_describe
tool_call
```

适用场景：

- MCP 工具多。
- Jar Skill 多。
- Profile 可用工具数量超过 prompt 预算。

短期不强制实现，但应在 ToolResolver 设计里预留“工具摘要视图”和“按需展开 schema”的能力。

### 2.9 SessionDB 与可恢复性

Hermes 保存 messages、tool_calls、reasoning、usage、平台消息 ID，并支持恢复 OpenAI conversation 格式。

当前项目已有 conversation、trace、token_usage，可以补强观测字段：

- `exitReason`
- `modelName`
- `apiCallCount`
- `toolTurnCount`
- `lastMessageRole`
- `compacted`
- `tokenBudgetSnapshot`
- `toolCalls`
- `reasoningSummary`

落地价值：

- 排查 Agent 卡住。
- 排查 tool call 后没有继续。
- 排查为什么没有最终回答。
- 支持前端 Trace Detail 展示完整回放。

### 2.10 Hook 机制

Hermes 有 pre/post tool call、post LLM call、memory sync 等 hook。

当前项目可轻量借鉴：

```text
preModelCall
postModelCall
preToolCall
postToolCall
postFinalAnswer
```

适合挂载：

- Trace span。
- Token usage。
- 敏感数据扫描。
- 告警。
- memory sync。
- 成本统计。

注意：hook 不应反向控制主流程太多，否则会让 Agent Runtime 难以理解。

## 3. 不建议照搬的地方

Hermes 很成熟，但复杂度也高。不建议直接复制：

- 大文件式主流程。
- CLI/TUI 特定逻辑。
- Python provider 特例恢复逻辑。
- 本地文件工具和路径权限模型。
- 文件型 memory。
- 过多入口层细节混进 Agent loop。

当前项目应保持模块边界：

```text
core.agent
  -> Agent Runtime / ReAct Loop

core.context
  -> API messages 构建 / token budget / compact / RAG context

core.model
  -> Provider adapter / streaming / usage

core.agent.tool
  -> ToolResolver / Registry / Validator / Dispatcher / ExecutionPlanner

core.skill
  -> Skill metadata / Jar hot reload / Skill executor

core.trace
  -> Trace root/span / observability
```

## 4. 映射到当前优化阶段的优先级

建议吸收顺序：

1. **token 级流式输出**
   - 借鉴 Hermes streaming 优先、fallback 到 non-streaming 的策略。
   - 先让 `core.model` 支持 token callback，再由 Agent Runtime 推 `message_delta`。

2. **API messages 与持久化 messages 分离**
   - 这是上下文压缩、RAG、provider 兼容的基础。
   - 先保证持久化历史干净。

3. **上下文预算与压缩**
   - 借鉴 Hermes 的上下文阈值判断和压缩恢复。
   - 当前项目要放在 `core.context`，不要放 Gateway。

4. **ToolResolver / ToolDispatcher / ToolCallValidator 收口**
   - 借鉴 Hermes 工具名校验、JSON 参数校验、tool error 回填。
   - 先解决工具调用稳定性，再做更多工具类型。

5. **安全并发工具执行**
   - 借鉴 Hermes “只读或资源不冲突才并发”。
   - 使用受控线程池和顺序合并 observation。

6. **Skill Jar 热加载**
   - 管理侧加载注册，运行时通过 ToolResolver 可见。
   - 不让 Runtime 操作 Jar 文件或 ClassLoader。

7. **RAG + Qdrant**
   - API messages 分离和 token budget 做好后再接入。
   - 检索结果作为临时 context block，不污染持久化历史。

8. **Tool Search 桥接**
   - 工具数量变多后再做。
   - 用于减少 tool schema token 压力。

## 5. 应更新到现有设计图和文档的点

当前 `single-agent-optimization-flow.drawio` 已覆盖：

- Gateway / Core / ReAct / Tool / RAG 分层。
- token 级 streaming。
- Micro Compact / Auto Compact。
- Qdrant RAG。
- Fork/Join 并行工具。
- Skill Jar 热加载旁路。

可继续补强但不急于改图：

- `API messages` 与 `conversation_message` 明确分离。
- `ToolCallValidator` 节点：工具名、JSON 参数、授权、空参数和 tool error。
- `Provider Adapter` 节点：streaming 优先、non-streaming fallback、usage 解析。
- `ToolExecutionPlanner` 节点：判断可并发、串行/并行分流。
- `Hook / Span` 节点：pre/post model/tool/final answer。

如果继续改图，建议不要把图画得过满。可以把这些补进后续 `优化阶段执行顺序.md`，再按 Step 落代码。
