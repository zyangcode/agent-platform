# 本项目 TeamRuntime 源码阅读与改造判断

本文记录回到本项目源码后的判断：当前不需要新增一个并列的 `LangGraphTeamRuntimeService`。更合适的是保留 `DefaultTeamRuntimeService` 这个默认入口，把它内部的手写编排升级为 LangGraph4j 编排。

## 1. 当前入口关系

当前单 Agent Runtime 中已经注入 Team 运行时：

```text
DefaultAgentRuntimeService
  -> TeamRuntimeService teamRuntimeService
  -> teamRuntimeService.run(command, teamEventSink)
```

判断 Team 模式的位置在：

```text
DefaultAgentRuntimeService
  -> command.agentMode() == "team"
  -> profile.teamEnabled()
  -> TeamRuntimeService
```

所以 `DefaultTeamRuntimeService` 已经是外部入口，不需要额外发明新入口。

推荐方向：

```text
保留：
DefaultTeamRuntimeService implements TeamRuntimeService

改造：
DefaultTeamRuntimeService 内部从手写流程改为 CompiledGraph 编排
```

## 2. 为什么方案 A 更合适

方案 A：

```text
保留 DefaultTeamRuntimeService 名字，内部换成 LangGraph4j
```

这是当前项目更好的选择，原因：

```text
1. DefaultTeamRuntimeService 本来就是“默认 Team 运行时”的语义
2. 对外 Bean 和接口不用变
3. DefaultAgentRuntimeService / Gateway / SSE 调用点少改
4. 当前需求是升级 Team 内核，不是新增一个对外运行模式
```

缺点是名字看不出 LangGraph4j，但可以用内部包结构解决：

```text
DefaultTeamRuntimeService
  -> TeamGraphFactory
  -> TeamGraphState
  -> graph/node/*
```

这样读代码时能清楚看到内部已经是 LangGraph4j。

## 3. 当前 Team 代码已经具备的能力

当前 `core.team` 已经有完整基础骨架：

```text
api
  TeamRuntimeService
  TeamPlanner
  TeamExecutor
  TeamReviewer
  TeamEventSink

application
  DefaultTeamRuntimeService
  DefaultTeamPlanner
  DefaultTeamExecutor
  DefaultTeamReviewer
  TeamRunLimiter
  TeamLimits
  TaskDependencySorter
  TaskPlanValidator
  ReviewResultValidator
  TeamAnswerDraftBuilder
  TeamFinalAnswerBuilder

dto
  TaskPlanDTO
  TeamTaskDTO
  ExecutionResultDTO
  ReviewResultDTO
  TeamRuntimeEventDTO
```

这说明改造不是从零做 LangGraph4j Team，而是把现有手写 Orchestrator 拆成图节点。

## 4. 当前 DefaultTeamRuntimeService 的实际流程

当前 `run()` 是一个大方法，核心流程是：

```text
validate command
create TeamRunLimiter
start team.run span
emit team_start
build context
resolve tools
planner.plan
emit team_plan
execute tasks by dependency order
emit task/tool events
build answer draft
review
emit team_review
if retry required:
  retry one task
  rebuild draft
  review again
else if replan required:
  planner.replan
  execute new tasks
  review again
final answer
emit team_final
aggregate usage
finish team.run span
return AgentRunResult
```

这已经非常接近目标 graph：

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

## 5. 哪些能力应该保留

以下现有能力应该保留，不要重写：

### 5.1 Planner

`DefaultTeamPlanner` 已经符合边界：

```text
只生成 TaskPlan JSON
不调工具
不回答用户
失败后让模型修正
仍失败则 fallback 为单 MODEL_TASK
```

应继续复用：

```text
TeamPlanner.plan(PlanTeamCommand)
```

### 5.2 Executor

`DefaultTeamExecutor` 已经区分：

```text
TOOL_TASK
MODEL_TASK
```

并且：

```text
TOOL_TASK -> AgentToolDispatcher
MODEL_TASK -> ModelInvokeService
检查 dependsOn 前置任务结果
不重新规划
不生成最终答案
```

应继续复用：

```text
TeamExecutor.execute(ExecuteTeamTaskCommand)
```

### 5.3 Reviewer

`DefaultTeamReviewer` 已经符合边界：

```text
审查 plan + executionResults + answerDraft
输出 ReviewResult JSON
不调工具
不执行任务
可请求 retryTasks 或 replanRequired
```

应继续复用：

```text
TeamReviewer.review(ReviewTeamCommand)
```

### 5.4 Limiter

已有 `TeamRunLimiter`：

```text
maxTasks
maxRetries
maxToolCalls
maxModelCalls
timeoutMs
```

这个应该进入 `TeamGraphState`，作为一次 Team run 的运行时对象或快照引用。

注意：

```text
如果 TeamGraphState 要严格可序列化，则不要直接把 TeamRunLimiter 放进 state。
第一版不做持久 checkpoint 时，可以放 transient runtime context。
更干净的方式是 TeamGraphRuntimeContext 持有 limiter，state 只保存计数快照。
```

### 5.5 SSE 事件

已有 `TeamRuntimeEventDTO`：

```text
team_start
team_plan
team_task_start
team_tool_call
team_tool_result
team_task_result
team_review
team_retry
team_final
```

Gateway 已经能写 Team SSE：

```text
InternalAiController.writeTeamEvent(...)
```

所以 LangGraph4j 改造不能破坏事件格式。

### 5.6 Trace 和 Token

当前 Runtime 已经做：

```text
team.run span
context.build span
team.plan span
team.task.execute span
team.review span
team.fallback span
model usage record
```

改造后应继续保留这些 span 名称，或者只新增更细粒度 span，不要破坏已有 Trace 看板。

## 6. 哪些应该拆成 Graph Node

当前 `DefaultTeamRuntimeService.run()` 里的逻辑可以拆成：

| Graph Node | 从现有代码迁移的逻辑 |
| --- | --- |
| `build_team_context` | `buildContext()` + `agentToolResolver.resolve(context)` |
| `plan` | `planner.plan(...)` + `recordModelInvocations(...)` |
| `validate_plan` | `limiter.checkTaskCount(...)`，后续可扩展 schema/权限校验 |
| `schedule` | `taskDependencySorter.sort(plan.tasks())` |
| `execute_batch` | `executeTasks(...)` / `executeNewTasks(...)` / `executeTask(...)` |
| `evaluate_tasks` | 判断失败任务、依赖跳过、是否需要部分回答 |
| `global_review` | `answerDraftBuilder.build(...)` + `review(...)` + `emitReview(...)` |
| `retry_or_replan` | 当前 `needsRetry` / `needsReplan` 分支 |
| `final_answer` | `finalAnswerBuilder.build(...)` + fallbackModelAnswer |

第一版也可以合并一点，避免节点太碎：

```text
START
  -> build_context
  -> plan
  -> execute_batch
  -> review
  -> route_after_review
  -> final_answer
  -> END
```

但从文档和后续扩展角度，更推荐保持完整节点名。

## 7. 推荐包结构

在现有 `core.team` 下新增：

```text
agent-platform-core/src/main/java/com/ls/agent/core/team/graph
  TeamGraphFactory.java
  TeamGraphState.java
  TeamGraphRuntimeContext.java
  TeamGraphNodeNames.java
  node
    BuildTeamContextNode.java
    PlanNode.java
    ValidatePlanNode.java
    ScheduleNode.java
    ExecuteBatchNode.java
    EvaluateTasksNode.java
    GlobalReviewNode.java
    RouteAfterReview.java
    FinalAnswerNode.java
```

保留：

```text
DefaultTeamRuntimeService
```

但它内部只负责：

```text
validate command
创建 runtime context
调用 TeamGraphFactory 获取 CompiledGraph
stream/invoke graph
聚合最终 TeamGraphState
返回 AgentRunResult
```

## 8. TeamGraphState 建议字段

建议第一版 state：

```text
command
conversationId
context
availableTools
plan
scheduledTasks
taskExecutionResults
executionResults
answerDraft
reviewResult
planResults
reviewResults
fallbackModelInvocations
finalAnswer
usage
step
runSpanId
success
errorMessage
route
retryTaskId
replanCount
```

如果考虑序列化边界，应拆成：

```text
TeamGraphState
  只放 DTO、字符串、数字、JsonNode、List、Map

TeamGraphRuntimeContext
  放 TeamEventSink、TeamRunLimiter、Trace helper、ObjectMapper 等运行时对象
```

第一版不做 checkpoint 持久化，可以务实一点，但文档里要明确：

```text
不把 Entity / Mapper / Service 放入 state。
```

## 9. LangGraph4j 依赖

当前 `agent-platform-core/pom.xml` 尚未引入 LangGraph4j。

需要新增依赖：

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core</artifactId>
    <version>${langgraph4j.version}</version>
</dependency>
```

父 POM 建议新增：

```xml
<langgraph4j.version>...</langgraph4j.version>
```

版本选择需要实际 Maven 验证。外部项目中见到：

```text
1.0.0
1.6.2
```

当前项目 Java 17，建议优先尝试较新的 `1.6.2`，如果 Maven 解析或 API 不匹配，再降级。

## 10. 关键风险

### 10.1 当前文件已有中文乱码

读取 `DefaultTeamRuntimeService` 时，部分中文字符串在终端中显示为乱码，例如事件消息。需要确认是终端编码问题还是文件已损坏。

改造时建议统一把事件 message 改成 ASCII 或重新保存为 UTF-8。

### 10.2 动态任务并发

当前执行任务是串行：

```text
for task in sorted tasks:
  executeTask(...)
```

LangGraph4j 改造后可以先保持串行，降低风险。

后续再在 `ExecuteBatchNode` 中支持：

```text
按 dependsOn 分层
同层并发
maxConcurrentTasks 限制
```

不要一上来同时引入 graph 改造和并发改造。

### 10.3 Runtime state 与 checkpoint

如果使用 `MemorySaver`，state 需要可序列化。当前 `AgentContextDTO`、`AgentRunCommand`、`AgentToolDTO` 未必适合直接 checkpoint。

第一版建议：

```text
不引入 checkpoint saver
只使用 CompiledGraph.invoke/stream 完成一次请求内执行
Trace/Token/SSE 作为运行记录
```

### 10.4 不要重写 Planner/Executor/Reviewer

当前三者已经比较符合阶段 4 约束。LangGraph4j 改造的重点是编排层，不要扩大到重写角色能力。

## 11. 更新后的落地判断

最终推荐：

```text
DefaultTeamRuntimeService 名字保留。
DefaultTeamRuntimeService 内部升级为 LangGraph4j 驱动。
Planner / Executor / Reviewer 继续复用。
SSE / Trace / Token 格式继续保留。
第一版 graph 保持单图和串行任务。
后续再做 execute_batch 内部并发。
```

一句话：

```text
保留入口，替换内核；保留角色服务，替换编排方式。
```
