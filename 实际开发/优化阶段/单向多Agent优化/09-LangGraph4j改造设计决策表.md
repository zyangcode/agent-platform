# LangGraph4j 改造设计决策表

本文把前面 04-08 的阅读结论收敛成决策表，方便后续真正进入实现阶段时按边界执行。

本文不包含代码实现，只做设计判断。

## 1. 参考依据

本轮重点参考：

- LangGraph4j Overview
  https://langgraph4j.github.io/langgraph4j/
- Core Library
  https://langgraph4j.github.io/langgraph4j/core/core-library/
- Time Travel / Pause before tools / Resume
  https://langgraph4j.github.io/langgraph4j/how-tos/time-travel/
- Checkpoint Postgres
  https://langgraph4j.github.io/langgraph4j/core/checkpoint-postgres/
- Multi-agent supervisor
  https://langgraph4j.github.io/langgraph4j/how-tos/multi-agent-supervisor/
- Agent Executor + MCP
  https://langgraph4j.github.io/langgraph4j/how-tos/agentexecutor-mcp/
- Spring AI Integration
  https://langgraph4j.github.io/langgraph4j/integrations/spring-ai/

官方资料说明 LangGraph4j 支持：

```text
StateGraph
普通边 / 条件边
state schema / channel / reducer
async / streaming
checkpoint / replay / resume
interruptBefore / human approval
PlantUML / Mermaid graph visualization
subgraph / parallel node
LangChain4j / Spring AI integration
```

但本项目不应该“看到能力就全用”。应按当前架构边界选择。

## 2. 总原则

最终原则：

```text
LangGraph4j 是 Team Runtime 的编排内核，不是平台框架。
```

也就是：

```text
保留：
  Profile
  Context
  Memory / RAG
  Skill / MCP
  Trace / Token / Gateway
  SSE
  Planner / Executor / Reviewer

替换：
  DefaultTeamRuntimeService 内部的手写状态流转

不替换：
  单 Agent Runtime
  MCP Client / MCP Tool Executor
  ModelInvokeService
  AgentToolDispatcher
  TokenUsageService
  TraceService
```

## 3. 决策表

| 能力 | 是否第一版引入 | 判断 |
| --- | --- | --- |
| `StateGraph` | 是 | 用它表达 Team 固定状态机 |
| 普通边 | 是 | 表达固定流程：build context -> plan -> execute -> review |
| 条件边 | 是 | 表达 review 后的 retry / replan / final |
| `AgentState` | 是 | 承载 TeamGraphState |
| `Channel` / reducer | 谨慎引入 | 第一版多数 key 用覆盖语义，列表字段再考虑 appender |
| `CompiledGraph` 预编译 | 是 | `TeamGraphFactory` 初始化一次，运行时复用 |
| `stream` | 暂不作为主路径 | SSE 已由 TeamEventSink 控制，第一版可用 invoke |
| checkpoint | 否 | 第一版不做暂停恢复和长任务状态持久化 |
| Postgres saver | 否 | 会引入新的表/状态序列化边界，后置 |
| interruptBefore | 否 | 高危工具确认沿用现有 pending tool call 机制 |
| resume / time travel | 否 | 依赖 checkpoint，后置 |
| parallel node | 否 | 任务并发后置到 ExecuteBatchNode 内部 |
| subgraph | 否 | 当前单图足够表达 Planner/Executor/Reviewer |
| PlantUML / Mermaid | 可后置 | 适合 Trace 可视化增强，不影响第一版闭环 |
| LangChain4j AgentExecutor | 否 | 会绕开本项目工具/Trace/Token/MCP 边界 |
| Spring AI AgentExecutor | 否 | ModelInvokeService 已经是模型适配边界 |
| MCP integration 示例 | 只借鉴思路 | MCP 执行仍走本项目已有 MCP 链路 |

## 4. State 设计决策

LangGraph4j 的核心是：

```text
node 返回 Map<String, Object>
graph 根据 state schema / reducer 合并更新
```

本项目第一版建议：

```text
TeamGraphState extends AgentState
```

字段分三类。

### 4.1 覆盖型字段

这些字段每次更新都覆盖旧值：

```text
command
conversationId
context
availableTools
plan
previousPlan
scheduledTasks
answerDraft
review
finalAnswer
usage
step
runSpanId
route
retryTaskId
```

不需要自定义 reducer。

### 4.2 追加型字段

这些字段天然是历史记录：

```text
planResults
taskExecutionResults
executionResults
reviewResults
fallbackModelInvocations
```

第一版有两个选择：

方案 A：

```text
不用 Channel appender。
每个 node 自己读取旧 List，append 后返回完整新 List。
```

优点：

```text
最直观
容易调试
不依赖 reducer 语义
```

缺点：

```text
node 代码稍啰嗦
```

方案 B：

```text
用 Channels.appender。
node 只返回新增元素。
```

优点：

```text
更符合 LangGraph4j 风格
后续并发合并更自然
```

缺点：

```text
需要清楚 reducer 语义
测试要覆盖列表合并顺序
```

建议：

```text
第一版用方案 A。
等 ExecuteBatchNode 内部并发时，再考虑 appender reducer。
```

### 4.3 禁止放入 state 的对象

不要放：

```text
Spring Bean
Mapper
Entity
ThreadPool
MCP Client
TeamRunLimiter
TeamEventSink
TraceService
TokenUsageService
ObjectMapper
```

这些放入：

```text
TeamGraphRuntimeContext
```

原因：

```text
state 未来可能 checkpoint / serialize。
runtime object 不应该进入可持久化状态。
```

## 5. Graph 形态决策

第一版 graph：

```text
START
  -> build_context
  -> plan
  -> validate_plan
  -> schedule
  -> execute_batch
  -> review
  -> route_after_review

route_after_review:
  retry  -> execute_batch -> review -> route_after_review
  replan -> plan -> validate_plan -> schedule -> execute_batch -> review -> route_after_review
  final  -> final_answer -> END
```

不要第一版做成：

```text
supervisor -> arbitrary agent -> supervisor
```

原因：

```text
本项目不是开放式多 Agent 派发。
本项目是受控 Planner/Executor/Reviewer 流程。
```

`route_after_review` 应该是确定性代码节点，不是 LLM 节点。

输入：

```text
ReviewResultDTO
```

输出：

```text
route = RETRY / REPLAN / FINAL
scheduledTasks = retry task 或空
previousPlan = plan，当 replan 时设置
retryTaskId = retry task id
```

## 6. Node 边界决策

### 6.1 BuildContextNode

职责：

```text
require conversationId
AgentContextBuilder.build
AgentToolResolver.resolve
```

保留 Trace：

```text
context.build
```

不发 Team SSE。

### 6.2 PlanNode

职责：

```text
TeamPlanner.plan
记录 plan model usage
emit team_plan
```

不直接校验工具权限，工具可用性仍来自：

```text
AgentToolResolver.resolve(context)
TaskPlanValidator.validate(plan, availableToolNames)
```

### 6.3 ValidatePlanNode

职责：

```text
limiter.checkTaskCount
TaskPlanValidator.validate
```

这是比当前手写 Runtime 更清晰的边界。

### 6.4 ScheduleNode

职责：

```text
TaskDependencySorter.sort
replan 时过滤旧 task id 和已完成 task id
```

第一版不做并发分层，只输出稳定顺序 List。

### 6.5 ExecuteBatchNode

职责：

```text
按 scheduledTasks 串行执行
emit team_task_start
TOOL_TASK 前 emit team_tool_call
TeamExecutor.execute
consume model/tool calls
record model usage
emit team_tool_result
emit team_task_result
```

不要让 graph 直接调用：

```text
AgentToolDispatcher
McpToolExecutor
McpClient
```

它们仍由 `DefaultTeamExecutor` 间接使用。

### 6.6 ReviewNode

职责：

```text
TeamAnswerDraftBuilder.build
TeamReviewer.review
record review model usage
emit team_review
```

Reviewer 仍然不执行工具、不重新规划。

### 6.7 FinalAnswerNode

职责：

```text
TeamFinalAnswerBuilder.build
必要时 fallbackModelAnswer
totalUsage
emit team_final
```

fallback model 仍走：

```text
ModelInvokeService
```

不要改成 LangGraph4j 的 AgentExecutor。

## 7. Trace / SSE / Token 决策

第一版继续显式记录，不依赖 LangGraph4j hook：

```text
每个 node 内部或 support helper 显式 startSpan / finishSpan
每个业务阶段显式 emit TeamRuntimeEventDTO
每个 ModelInvokeResult 显式 tokenUsageService.record
```

原因：

```text
当前项目已经有稳定的 Trace/SSE/Token 语义。
hook 适合做横切增强，不适合第一版承载业务语义。
```

后续可以再抽：

```text
TeamGraphTraceHook
TeamGraphEventRecorder
TeamGraphUsageRecorder
```

但第一版代码应优先可读。

## 8. Checkpoint / Human-in-the-loop 决策

官方 `checkpointSaver` 和 `interruptBefore` 很适合：

```text
暂停工具调用前等待用户确认
查看历史状态
resume 执行
time travel 调试
```

但本项目第一版不启用。

原因：

```text
需要持久化 graph state
需要 threadId / checkpointId 管理
需要前端恢复交互
需要 TeamRunSnapshotDTO 或新表设计
需要明确敏感数据是否能进入 checkpoint
```

当前项目已经有：

```text
pendingToolCall
confirmedToolKeys
TeamRunLimiter
trace_spans.attributes
```

所以第一版：

```text
高危确认继续走现有 pending tool call。
Team graph 一次请求内跑完。
Trace 负责可观测，不负责 resume。
```

## 9. 并发决策

官方支持 parallel node / subgraph / async。

本项目第一版不做任务并发。

原因：

```text
当前 DefaultTeamRuntimeService 是串行执行。
现有测试锁定了事件顺序和 step 递增。
如果同时引入 graph 和并发，回归面太大。
```

后续可以在 `ExecuteBatchNode` 内部做：

```text
按 dependsOn 拓扑分层
同层任务并发
maxConcurrentTasks 限制
结果按拓扑顺序合并
SSE step 由同步事件发射器统一分配
```

graph 结构不需要变。

## 10. 可视化决策

LangGraph4j 支持 PlantUML / Mermaid graph visualization。

第一版可以不接前端，但建议保留设计空间：

```text
TeamGraphFactory 暴露 graphRepresentation()
Trace 页面后续可展示 Team 执行图
每个 node 对应 trace span
每条 route 对应 review 决策
```

前端展示建议：

```text
先做只读执行路径
再做 graph 静态结构
最后做每次运行的状态快照
```

不要第一版做拖拽编排。

## 11. 和当前代码的对应关系

当前：

```text
DefaultTeamRuntimeService.run()
  负责全部流程
```

目标：

```text
DefaultTeamRuntimeService.run()
  validate command
  create TeamRunLimiter
  start team.run span
  create TeamGraphRuntimeContext
  call TeamGraphFactory.invoke
  finish team.run span
  return AgentRunResult
```

原 private helper 的去向：

| 当前 helper | 目标位置 |
| --- | --- |
| `buildContext` | `BuildContextNode` |
| `executeTasks` | `ExecuteBatchNode` |
| `executeNewTasks` | `ScheduleNode` + `ExecuteBatchNode` |
| `executeTask` | `ExecuteBatchNode` |
| `review` | `ReviewNode` |
| `emitReview` | `ReviewNode` |
| `needsRetry` | `RouteAfterReview` |
| `needsReplan` | `RouteAfterReview` |
| `fallbackModelAnswer` | `FinalAnswerNode` |
| `totalUsage` | `FinalAnswerNode` 或 `TeamUsageAggregator` |
| `recordModelInvocations` | `TeamGraphSupport` |
| `safeStartSpan/safeFinishSpan` | `TeamGraphSupport` |

## 12. 实施前必须补充的设计检查

真正写代码前，建议先补两个设计文档或小节：

### 12.1 TeamGraphState 字段表

写清楚：

```text
字段名
Java 类型
是否可序列化
更新节点
是否允许进入 checkpoint
是否可能包含敏感信息
```

### 12.2 TeamGraphEvent 顺序表

写清楚：

```text
正常路径事件顺序
retry 路径事件顺序
replan 路径事件顺序
TOOL_TASK 路径事件顺序
fallback 路径事件顺序
```

这些表比直接写代码更重要，因为它们决定前端 SSE 和测试是否稳定。

## 13. 最终建议

最终建议保持不变：

```text
方案 A：保留 DefaultTeamRuntimeService 名字，内部换成 LangGraph4j。
```

但实施顺序必须克制：

```text
1. 先只建 TeamGraphState / TeamGraphFactory / smoke test
2. 再迁移 build_context / plan / validate_plan / schedule
3. 再迁移 execute_batch / review / route
4. 最后迁移 final_answer / fallback
5. 全部测试过后，再切 DefaultTeamRuntimeService 主路径
```

第一版成功标准：

```text
DefaultTeamRuntimeServiceTest 不需要大改，核心断言仍通过。
Gateway/Web Team SSE 测试仍通过。
单 Agent Runtime 不受影响。
MCP/Skill 执行链路不变。
Trace/Token 记录不退化。
```
