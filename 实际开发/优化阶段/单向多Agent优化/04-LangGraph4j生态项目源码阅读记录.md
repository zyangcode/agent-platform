# LangGraph4j 生态项目源码阅读记录

本文记录对 LangGraph4j README 中相关开源项目的第一轮源码阅读结论，目标是服务当前项目的 `LangGraphTeamRuntimeService` 方案，而不是照搬外部项目。

当前已读项目：

- Azure Samples: `agent-openai-java-banking-assistant-langgraph4j`
- Breezeware Dynamo: `dynamo-multi-ai-agent-langgraph4j-starter`

## 1. 阅读结论总览

两个项目的参考价值不同：

| 项目 | 价值判断 | 适合借鉴 | 不适合照搬 |
| --- | --- | --- | --- |
| Azure 银行多 Agent 样例 | 参考价值较高 | Spring Bean 形式编译 graph、threadId checkpoint、Supervisor 路由、AgentNode 包装、MCP 工具集成 | 单跳 Supervisor 路由、MemorySaver、按 toolName 映射 MCP client |
| Dynamo POC | 只适合作为最小 API 示例 | `StateGraph`、`Channel`、`node_async`、`edge_async`、条件边基础用法 | 业务流程太简单，不是成熟 Team Runtime |

对当前项目的核心判断：

```text
LangGraph4j 应该负责 Team 宏观流程编排。

模型调用、工具权限、MCP 执行、Trace、Token、SSE、记忆读写，仍应复用当前平台已有服务。
```

也就是说，不建议让 LangGraph4j 接管整个 Agent Runtime。更合理的是：

```text
DefaultAgentRuntimeService
  -> 单 Agent 链路继续保留

LangGraphTeamRuntimeService
  -> TEAM 模式内部使用 CompiledGraph
  -> graph node 调用现有 core 服务
```

## 2. Azure 银行多 Agent 样例

仓库：

```text
https://github.com/Azure-Samples/agent-openai-java-banking-assistant-langgraph4j
```

### 2.1 项目整体结构

核心模块集中在：

```text
app/copilot/langgraph4j-agents
app/copilot/langchain4j-agents
app/copilot/copilot-backend
app/business-api/account
app/business-api/payment
app/business-api/transactions-history
```

其中：

| 模块 | 作用 |
| --- | --- |
| `langgraph4j-agents` | 定义 LangGraph4j 的 state、node、Supervisor graph |
| `langchain4j-agents` | 定义领域 Agent、ReAct 循环、MCP Tool Agent |
| `copilot-backend` | Spring Boot 后端入口，注入 `CompiledGraph` 并处理 `/api/chat` |
| `business-api/*` | 暴露账户、交易、支付等业务 API，并通过 Spring AI MCP 暴露为工具 |

### 2.2 Graph 运行链路

它的 LangGraph4j 链路是典型的垂直多 Agent Supervisor：

```text
START
  -> Supervisor
  -> AccountAgent / TransactionHistoryAgent / PaymentAgent / END
  -> END
```

对应配置类：

```text
Langgraph4JAgentsConfiguration
```

核心做法：

```java
var graph = new StateGraph<>(AgentWorkflowState.SCHEMA, serializer)
    .addNode("Supervisor", SupervisorAgentNode.of(supervisorAgent))
    .addNode(Intent.AccountAgent.name(), AgentNode.of(accountAgent))
    .addNode(Intent.TransactionHistoryAgent.name(), AgentNode.of(transactionHistoryAgent))
    .addNode(Intent.PaymentAgent.name(), AgentNode.of(paymentAgent))
    .addEdge(START, "Supervisor")
    .addConditionalEdges("Supervisor", supervisorRoute, ...)
    .addEdge(Intent.AccountAgent.name(), END)
    .addEdge(Intent.TransactionHistoryAgent.name(), END)
    .addEdge(Intent.PaymentAgent.name(), END);
```

它把 `CompiledGraph<AgentWorkflowState>` 注册为 Spring Bean，然后 Controller 每次请求调用：

```java
RunnableConfig config = RunnableConfig.builder()
    .threadId(threadId)
    .build();

var state = langgraph4jWorkflow.invoke(Map.of("messages", chatHistory), config);
```

### 2.3 State 设计

它的 state 很薄：

```java
public class AgentWorkflowState extends MessagesState<ChatMessage> {
    public Optional<String> nextAgent() {
        return value("nextAgent");
    }
}
```

含义是：

```text
messages 作为主状态
nextAgent 作为 Supervisor 路由结果
```

这对当前项目有启发，但不能直接照搬。你的 Team 状态需要比它更完整：

```text
runId
tenantId
applicationId
userId
profileId
threadId
messagesSnapshot
teamContext
plan
validatedPlan
scheduledTasks
executionResults
reviewResult
reflectionCandidates
finalAnswer
usage
traceIds
sseEventSeq
```

同时需要遵守当前设计约束：

```text
LangGraphTeamState 只保存可序列化快照，不保存 Entity、Mapper、Service、SseEmitter 等运行时对象。
```

### 2.4 Node 设计

Azure 样例里 `AgentNode` 是一个通用包装器：

```java
public Map<String, Object> apply(AgentWorkflowState state) throws Exception {
    var messages = agent.invoke(state.messages());
    return Map.of("messages", messages);
}
```

这个模式值得借鉴。当前项目也可以把 Team 节点做薄：

```text
PlanNode
  -> 调 TeamPlannerService

ExecuteBatchNode
  -> 调 TeamTaskExecutor

GlobalReviewNode
  -> 调 TeamReviewerService

ReflectNode
  -> 调 MemoryWriteCandidateService
```

不要把所有模型调用、工具执行、Trace 写入逻辑堆进 LangGraph4j node 里。Node 应该是编排层适配器。

### 2.5 Supervisor 设计

Azure 样例的 `SupervisorAgent` 做法是：

```text
给模型一段 system prompt
列出所有 agent metadata
要求模型只返回 agent name
```

核心 prompt 思路：

```text
You are a banking customer support agent triaging conversation.
Use agents metadata to select the best agent.
Answer only with the agent name.
If not able to select, answer none.
```

然后 `SupervisorAgentNode` 将模型输出写入：

```text
nextAgent
```

再由条件边决定下一跳。

这个设计适合“用户意图路由到一个领域 Agent”，但不适合当前项目的 Planner/Executor/Reviewer Team。当前项目不应该让 Supervisor 直接决定最终执行 Agent，而应该让 Planner 生成结构化任务计划：

```text
用户问题
  -> Planner 输出 TaskPlan JSON
  -> ValidatePlanNode 校验 JSON Schema
  -> ScheduleNode 决定执行顺序和可并发任务
  -> ExecuteBatchNode 执行任务
  -> Reviewer 审查
```

### 2.6 MCP 工具集成方式

Azure 样例里领域 Agent 继承 `MCPToolAgent`。构造时会创建 MCP client，并调用：

```java
mcpClient.listTools()
```

然后保存：

```text
toolName -> McpClient
toolSpecifications
```

模型请求时，把工具规格通过 LangChain4j 的结构化 tool specifications 传给模型：

```java
ChatRequestParameters parameters = ChatRequestParameters.builder()
    .toolSpecifications(getToolSpecifications())
    .build();
```

模型返回 tool execution requests 后：

```java
for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
    var mcpClient = tool2ClientMap.get(toolExecutionRequest.name());
    result = mcpClient.executeTool(toolExecutionRequest);
}
```

这说明它走的是模型原生 tool calling 思路，而不是 `@mcp:name {...}` 文本协议。

### 2.7 ReAct 工具循环

`AbstractReActAgent` 的执行逻辑是：

```text
1. 构造内部 chat memory
2. 加 system prompt
3. 加工具规格
4. 调模型
5. 如果模型返回 tool execution requests
6. 执行工具
7. 把工具结果作为 ToolExecutionResultMessage 放回消息
8. 再调模型
9. 直到没有工具调用
10. 返回最终消息
```

伪流程：

```text
while aiMessage.hasToolExecutionRequests():
  execute tools
  append ai tool request
  append tool result
  call model again
```

这和当前项目已有 ReAct while 循环概念一致。区别在于：

```text
Azure 样例：模型原生 tool calling
当前项目：主链路是 system prompt 文本协议 @mcp:name {...}
```

如果当前项目后续改成 OpenAI tool calling，Azure 的结构很值得借鉴，但必须加上权限校验、tool id、防同名冲突和降级协议。

### 2.8 混合工具能力

`PaymentMCPAgent` 同时接入多个 MCP server：

```text
payment MCP server
transaction MCP server
account MCP server
```

并额外注入本地 Java 工具：

```text
scanInvoice
```

它的思路是：

```text
MCP 工具 -> tool2ClientMap
本地工具 -> extendedExecutorMap
```

执行时先查本地扩展工具，再查 MCP 工具：

```java
var toolExecutor = extendedExecutorMap.get(toolExecutionRequest.name());
if (toolExecutor != null) {
    result = toolExecutor.execute(...);
} else {
    var mcpClient = tool2ClientMap.get(toolExecutionRequest.name());
    result = mcpClient.executeTool(...);
}
```

这对当前项目有直接启发：

```text
Team Executor 不应只理解 MCP 工具。
它应该通过统一 ToolRuntime 分发：

BUILTIN_TOOL
SKILL_TOOL
MCP_TOOL
MODEL_TASK
```

但工具身份不能只用 `toolName`。

### 2.9 Azure 样例的风险点

Azure 样例是 demo 风格，放到当前平台不能照搬：

| 风险点 | 原因 | 当前项目建议 |
| --- | --- | --- |
| 只按 toolName 找 MCP client | 多 server 同名工具会冲突 | 使用 `toolId` 或 `(tenantId, serverId, toolName)` |
| 构造 Agent 时 `listTools()` | 工具变化无法运行时刷新 | 使用数据库工具元数据 + refresh 机制 |
| `MemorySaver` 内存 checkpoint | 多实例、重启后丢失 | MVP 可内存，生产应换 Redis/Postgres saver |
| Supervisor 只单跳路由 | 不支持复杂任务拆解和审查 | 当前项目保留 Planner/Executor/Reviewer |
| 工具执行缺少平台级权限校验 | demo 默认信任 agent | 执行前必须校验 Profile、用户、租户、风险等级 |
| SSE 支持不足 | 它主要 JSON 返回 | 当前项目必须推 Team SSE 阶段事件 |

## 3. Dynamo LangGraph4j POC

仓库：

```text
https://github.com/Breezeware-OS/dynamo-multi-ai-agent-langgraph4j-starter
```

### 3.1 项目结构

关键文件：

```text
src/main/java/net/breezeware/dynamo/ai/agent/agentExecutor/AgentExecutor.java
src/main/java/net/breezeware/dynamo/ai/agent/service/AgentService.java
src/main/java/net/breezeware/dynamo/ai/agent/service/FunctionConfiguration.java
src/main/java/net/breezeware/dynamo/ai/agent/service/WeatherService.java
```

### 3.2 Graph 流程

它的 graph 是一个串行演示：

```text
START
  -> weatherAgent
  -> 如果需要推荐，进入 travelAgent
  -> foodAgent
  -> END
```

核心代码：

```java
return new StateGraph<>(State.SCHEMA, State::new)
    .addEdge(START, "weatherAgent")
    .addNode("weatherAgent", node_async(AgentExecutor.this::callWeatherAgent))
    .addConditionalEdges("weatherAgent",
        edge_async(shouldContinue),
        Map.of("travelAgent", "travelAgent", "end", END))
    .addNode("travelAgent", node_async(AgentExecutor.this::callTravelAgent))
    .addEdge("travelAgent", "foodAgent")
    .addNode("foodAgent", node_async(AgentExecutor.this::callFoodAgent))
    .addEdge("foodAgent", END);
```

### 3.3 State 设计

它自定义了 `State extends AgentState`，用 `Channel` 声明 state schema：

```java
static Map<String, Channel<?>> SCHEMA = Map.of(
    INPUT, Channel.of(() -> new HashMap<>()),
    OUTPUT, Channel.of(() -> new HashMap<>()),
    MID, Channel.of(() -> new HashMap<>())
);
```

这个例子适合用来理解 LangGraph4j 的基础状态模型，但它没有复杂 Team 所需的任务计划、审查、重试、Trace、Token、SSE。

### 3.4 工具方式

Dynamo 通过 Spring AI function bean 暴露天气工具：

```java
@Bean
@Description("Get the current weather conditions for the given country.")
public Function<WeatherService.Request, WeatherService.Response> currentWeatherFunction() {
    return new WeatherService(props);
}
```

然后 ChatClient 默认使用：

```java
.defaultFunctions("currentWeatherFunction")
```

这更像 Spring AI function calling 示例，不是 MCP 平台化工具系统。

### 3.5 对当前项目的参考价值

Dynamo 适合借鉴：

```text
StateGraph 基础写法
node_async 包装方法引用
edge_async 条件边
Channel schema
START / END 常量
```

不适合借鉴：

```text
Team 架构
MCP 工具发现
权限校验
多 Agent 角色边界
流式事件
持久化 checkpoint
生产级错误处理
```

## 4. 对当前项目的落地建议

### 4.1 保持单 Agent 不变

当前项目已经有单 Agent 主链路：

```text
DefaultAgentRuntimeService
  -> context builder
  -> model provider
  -> ReAct loop
  -> skill / MCP executor
  -> memory
  -> trace
  -> token usage
```

不建议为了 LangGraph4j 改造单 Agent。

LangGraph4j 应只进入 TEAM 模式：

```text
agent_mode = agent
profile.teamEnabled = false
  -> DefaultAgentRuntimeService 单 Agent

agent_mode = agent
profile.teamEnabled = true
  -> LangGraphTeamRuntimeService
```

### 4.2 Team Graph 推荐形态

结合当前已有设计，推荐 graph 为：

```text
START
  -> build_team_context
  -> plan
  -> validate_plan
  -> schedule
  -> execute_batch
  -> evaluate_tasks
  -> global_review
  -> reflect
  -> final_answer
  -> END
```

其中：

| Node | 职责 |
| --- | --- |
| `build_team_context` | 读取 Profile、Memory、RAG、Experience、工具元数据快照 |
| `plan` | Planner 输出结构化 TaskPlan，不调工具 |
| `validate_plan` | JSON Schema 校验、任务数量上限、权限初筛 |
| `schedule` | 生成可执行批次，控制并发 |
| `execute_batch` | Executor 执行 `MODEL_TASK` / `TOOL_TASK` |
| `evaluate_tasks` | 判断任务是否缺结果、是否可重试 |
| `global_review` | Reviewer 审查 plan、executionResults、answerDraft |
| `reflect` | 生成长期记忆候选，不直接乱写 |
| `final_answer` | Orchestrator 汇总最终回答 |

### 4.3 Node 不直接拥有重逻辑

建议每个 Node 只做：

```text
读 state
调用已有 service
写 state delta
推 SSE event
写 trace span
```

不要在 Node 里直接写复杂业务逻辑。

示意：

```java
class PlanNode implements NodeAction<TeamGraphState> {
    public Map<String, Object> apply(TeamGraphState state) {
        var result = plannerService.plan(state.toPlanningCommand());
        return Map.of("plan", result.plan(), "usage", result.usage());
    }
}
```

### 4.4 工具调用建议

Azure 的 `MCPToolAgent` 给出的教训是：

```text
toolName -> McpClient
```

这个方案在 demo 里简单，在平台里有风险。

当前项目应该使用：

```text
toolId
或
(tenantId, profileId, serverId, toolName)
```

执行前必须校验：

```text
1. tool_call 中的工具是否属于本次允许工具集合
2. 所属 mcp_server 是否 active
3. tool status 是否 enabled
4. 用户/Profile 是否绑定
5. 参数是否是 JSON object
6. 高风险工具是否需要确认
7. 是否超过 maxToolCalls
```

### 4.5 结构化 tool calling 与文本协议

Azure 样例走的是结构化 tool calling：

```text
模型请求体包含 tools/toolSpecifications
模型响应包含 tool execution requests
```

当前项目现状是：

```text
ToolsSlotSource
  -> 把工具说明写进 system prompt
  -> 模型输出 @mcp:name {...}
  -> DefaultAgentRuntimeService 解析文本协议
```

优化方向建议：

```text
优先：模型支持 tool calling 时，使用结构化 tools 字段
兜底：模型不支持或兼容异常时，回退 @mcp:name {...} 文本协议
```

也就是：

```text
function calling = 平台给模型一张正式工具表
文本协议 = 平台在 prompt 里告诉模型“请按这个格式写工具调用命令”
```

当前项目迁移时不要一次性删除文本协议，因为部分 OpenAI 兼容模型可能不接受 `tools` 字段，或者返回格式不稳定。

### 4.6 Checkpoint 建议

Azure 样例使用：

```java
new MemorySaver()
```

这只适合 demo。

当前项目 MVP 阶段可以先不用引入复杂 checkpoint saver，因为已有 Trace 和 conversation/message 表承担运行记录。若后续需要恢复中断中的 Team 执行，再考虑：

```text
Postgres saver
Redis saver
或自研基于 trace_id / thread_id 的 TeamState snapshot 表
```

不要为了第一版 Team Demo 先引入新的持久化复杂度。

### 4.7 SSE 与 Trace 建议

Azure 样例 Controller 是普通 JSON 返回，不满足当前项目阶段 4 要求。

当前项目必须在 graph node 边界推事件：

```text
team_plan
team_task_start
team_tool_call
team_tool_result
team_task_result
team_review
team_reflect
message
done
error
```

Trace 建议按 node 建 span：

```text
team.build_context
team.plan
team.validate_plan
team.schedule
team.execute_batch
team.execute_task
team.tool_call
team.review
team.reflect
team.final_answer
```

### 4.8 当前最推荐的工程边界

建议包结构：

```text
agent-platform-core/src/main/java/com/ls/agent/core/team
  api
  application
  graph
    LangGraphTeamRuntimeService
    TeamGraphFactory
    TeamGraphState
    node
      BuildTeamContextNode
      PlanNode
      ValidatePlanNode
      ScheduleNode
      ExecuteBatchNode
      EvaluateTasksNode
      GlobalReviewNode
      ReflectNode
      FinalAnswerNode
  planner
  executor
  reviewer
  dto
```

关键原则：

```text
graph 包只做编排适配
planner/executor/reviewer 包做角色能力
工具执行仍走 core.skill / core.mcp 的 api 服务
记忆仍走 core.memory
上下文仍走 core.context
模型调用仍走 core.model
```

## 5. 最终判断

对当前项目而言，LangGraph4j 不是“替换整个平台 Agent Runtime”的框架，而是：

```text
TEAM 模式内部的状态机和流程编排器。
```

最稳的落地方式是：

```text
1. 单 Agent 保持现状
2. 新增 LangGraphTeamRuntimeService
3. Team graph 使用 Planner -> Executor -> Reviewer 宏观流程
4. 每个 node 调用现有平台服务
5. 工具执行继续由平台统一校验和分发
6. 第一版不引入 Team 表
7. Team 运行明细写 trace_spans.attributes
8. 后续再补 checkpoint 持久化和结构化 tool calling
```

Azure 样例可以作为“怎么把 LangGraph4j 嵌进 Spring Boot + Agent + MCP”的参考；Dynamo 只作为“怎么写最小 StateGraph”的参考。
