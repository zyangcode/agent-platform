# TeamGraphState 字段与 SSE 事件顺序设计

本文补充 `TeamGraphState` 字段表和 Team SSE 事件顺序表。它是后续真正实现 LangGraph4j 改造前的行为契约，不包含代码实现。

## 1. 设计目标

LangGraph4j 改造不能只看 graph 是否能跑通，还必须保证：

```text
State 字段边界清楚
敏感信息边界清楚
checkpoint 后续可扩展
SSE 事件顺序不破坏前端和测试
Trace/Token 语义不退化
```

当前最重要的回归依据是：

```text
DefaultTeamRuntimeServiceTest
TeamRuntimeEventDTO
Gateway/Web Team SSE passthrough tests
```

## 2. TeamGraphState 字段总表

| 字段 | Java 类型 | 更新节点 | 是否第一版 checkpoint | 敏感信息风险 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `command` | `AgentRunCommand` | runtime 初始化 | 否 | 高 | 包含 userInput、tenantId、userId、applicationId、profileId |
| `conversationId` | `Long` | runtime 初始化 / build_context | 可 | 低 | 返回 `AgentRunResult` 必需 |
| `context` | `AgentContextDTO` | `build_context` | 否 | 高 | 包含 prompt、memory、context slots，可能有敏感信息 |
| `availableTools` | `List<AgentToolDTO>` | `build_context` | 可，需裁剪 | 中 | 包含工具名、描述、schema，不含工具实现 |
| `plan` | `TaskPlanDTO` | `plan` | 可，需脱敏 | 中 | 可能复述用户问题和任务细节 |
| `previousPlan` | `TaskPlanDTO` | `route_after_review` | 可，需脱敏 | 中 | replan 时保存旧计划 |
| `scheduledTasks` | `List<TeamTaskDTO>` | `schedule` / `route_after_review` | 可，需脱敏 | 中 | 当前即将执行的任务列表 |
| `taskExecutionResults` | `List<TeamTaskExecutionResultDTO>` | `execute_batch` | 否 | 高 | 包含工具结果、模型输出 |
| `executionResults` | `List<ExecutionResultDTO>` | `execute_batch` | 可，需脱敏 | 高 | answerDraft/review/final 依赖它 |
| `planResults` | `List<TeamPlanResultDTO>` | `plan` | 否 | 高 | 包含 `ModelInvokeResult` 和 usage |
| `reviewResults` | `List<TeamReviewResultDTO>` | `review` | 否 | 高 | 包含 review 模型调用结果 |
| `fallbackModelInvocations` | `List<ModelInvokeResult>` | `final_answer` | 否 | 高 | fallback 模型输出和 usage |
| `answerDraft` | `String` | `review` | 可，需脱敏 | 高 | 中间答案草稿，可能包含工具结果摘要 |
| `review` | `ReviewResultDTO` | `review` | 可，需脱敏 | 中 | route_after_review 依赖它 |
| `finalAnswer` | `String` | `final_answer` | 可，需脱敏 | 高 | 最终用户响应 |
| `usage` | `ModelUsageDTO` | `final_answer` | 可 | 低 | 聚合 token usage |
| `step` | `Integer` | 所有 emit 节点 | 可 | 低 | SSE 顺序计数 |
| `runSpanId` | `Long` | runtime 初始化 | 可 | 低 | Trace 父 span id |
| `route` | `TeamGraphRoute` | `route_after_review` | 可 | 低 | `RETRY` / `REPLAN` / `FINAL` |
| `retryTaskId` | `String` | `route_after_review` | 可 | 低 | retry 指定任务 |

## 3. 第一版 State 边界

第一版建议：

```text
TeamGraphState 可以保存 DTO 引用。
不启用 checkpoint。
只在一次请求内流转。
```

这允许实现更简单，也符合当前阶段不新增 Team 表的约束。

但要从第一版开始遵守：

```text
不要把 Spring Bean 放进 state。
不要把 Mapper 放进 state。
不要把 Entity 放进 state。
不要把 MCP Client 放进 state。
不要把 ThreadPool 放进 state。
不要把 TeamEventSink 放进 state。
不要把 TeamRunLimiter 放进 state。
```

这些放入：

```text
TeamGraphRuntimeContext
```

## 4. TeamGraphRuntimeContext 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `eventSink` | `TeamEventSink` | 发 Team SSE 事件 |
| `limiter` | `TeamRunLimiter` | 控制 maxTasks/maxRetries/maxToolCalls/maxModelCalls/timeoutMs |
| `runSpanId` | `Long` | `team.run` span id |
| `support` | `TeamGraphSupport` 或等价 helper | 封装 Trace/Token/Event 公共逻辑 |

`TeamGraphRuntimeContext` 不进入 checkpoint。

## 5. 字段更新规则

### 5.1 初始化

由 `DefaultTeamRuntimeService.run()` 初始化：

```text
command
conversationId
step = 1 或 2
runSpanId
route = FINAL
```

注意：

```text
team_start 事件建议仍在 DefaultTeamRuntimeService 入口 emit。
```

这样 graph 内第一个业务事件仍是：

```text
team_plan
```

和现有测试顺序一致。

### 5.2 build_context

输入：

```text
command
conversationId
runSpanId
```

输出：

```text
context
availableTools
```

不更新：

```text
step
```

原因：

```text
当前 context.build 没有 Team SSE 事件。
```

### 5.3 plan

输入：

```text
command
context
availableTools
previousPlan
executionResults
review
step
```

输出：

```text
plan
planResults append
step + 1
```

事件：

```text
team_plan
```

### 5.4 validate_plan

输入：

```text
plan
availableTools
limiter
```

输出：

```text
无业务字段，除非需要写 validationStatus
```

第一版不建议新增 `validationStatus`，异常直接抛出。

### 5.5 schedule

输入：

```text
plan
previousPlan
executionResults
```

输出：

```text
scheduledTasks
```

规则：

```text
首次 plan：scheduledTasks = sort(plan.tasks)
retry：scheduledTasks 已由 route_after_review 指定，不经过 schedule
replan：scheduledTasks = 新 plan 中未出现在 previousPlan 且未出现在 executionResults 的任务
```

### 5.6 execute_batch

输入：

```text
scheduledTasks
context
availableTools
executionResults
step
limiter
```

输出：

```text
taskExecutionResults append
executionResults replace/append
step + emittedEventCount
```

事件：

```text
team_task_start
team_tool_call，仅 TOOL_TASK
team_tool_result，仅 TOOL_TASK 且有 toolResults
team_task_result
```

### 5.7 review

输入：

```text
command
plan
executionResults
context
step
```

输出：

```text
answerDraft
review
reviewResults append
step + 1
```

事件：

```text
team_review
```

### 5.8 route_after_review

输入：

```text
review
plan
executionResults
step
limiter
```

输出：

```text
route
retryTaskId
previousPlan
scheduledTasks
step + 1，仅 retry/replan 时
```

事件：

```text
team_retry，仅 retry/replan 时
```

路由规则：

```text
if review.passed == false and retryTasks not empty:
  route = RETRY
  retryTaskId = first retry task id
  scheduledTasks = [retry task]
  emit team_retry

else if review.passed == false and replanRequired == true:
  route = REPLAN
  previousPlan = plan
  emit team_retry

else:
  route = FINAL
```

### 5.9 final_answer

输入：

```text
answerDraft
review
plan
executionResults
planResults
taskExecutionResults
reviewResults
fallbackModelInvocations
step
```

输出：

```text
finalAnswer
usage
fallbackModelInvocations append，必要时
step + 1 可选
```

事件：

```text
team_final
```

当前测试不强制 final 后 step 是否递增，只强制事件本身出现。

## 6. SSE 事件类型表

当前 `TeamRuntimeEventDTO` 类型：

| 类型 | 触发阶段 | 关键字段 |
| --- | --- | --- |
| `team_start` | Team run 开始 | `traceId`, `step`, `message` |
| `team_plan` | Planner 完成 | `payload = TaskPlanDTO` |
| `team_task_start` | 子任务开始 | `taskId`, `message` |
| `team_tool_call` | TOOL_TASK 调工具前 | `taskId`, `toolName` |
| `team_tool_result` | 工具返回后 | `taskId`, `toolName`, `status`, `payload` |
| `team_task_result` | 子任务完成 | `taskId`, `status`, `message`, `payload = ExecutionResultDTO` |
| `team_review` | Reviewer 完成 | `status`, `message`, `payload.review`, `payload.answerDraft` |
| `team_retry` | Reviewer 要求 retry/replan | `taskId` 可空，`message` |
| `team_final` | 最终答案生成 | `message` |

## 7. 正常路径事件顺序

对应测试：

```text
runsPlannerExecutorReviewerAndEmitsEventsInOrder
```

事件顺序必须保持：

```text
1 team_start
2 team_plan
3 team_task_start
4 team_task_result
5 team_review
6 team_final
```

Graph 路径：

```text
START
  -> build_context
  -> plan                 emits team_plan
  -> validate_plan
  -> schedule
  -> execute_batch         emits team_task_start, team_task_result
  -> review                emits team_review
  -> route_after_review    route FINAL
  -> final_answer          emits team_final
  -> END
```

## 8. TOOL_TASK 路径事件顺序

对应测试：

```text
emitsToolCallAndToolResultAroundToolTask
```

事件顺序必须保持：

```text
1 team_start
2 team_plan
3 team_task_start
4 team_tool_call
5 team_tool_result
6 team_task_result
7 team_review
8 team_final
```

并且 step 必须是：

```text
1, 2, 3, 4, 5, 6, 7, 8
```

关键约束：

```text
team_tool_call 必须在 executor.execute 前 emit。
team_tool_result 必须在 executor.execute 后、team_task_result 前 emit。
```

## 9. Retry 路径事件顺序

对应测试：

```text
retriesReviewerRequestedTaskOnce
```

事件顺序必须保持：

```text
1  team_start
2  team_plan
3  team_task_start
4  team_task_result
5  team_review
6  team_retry
7  team_task_start
8  team_task_result
9  team_review
10 team_final
```

Graph 路径：

```text
review
  -> route_after_review route RETRY, emits team_retry
  -> execute_batch only retry task
  -> review
  -> route_after_review route FINAL
  -> final_answer
```

关键约束：

```text
retry 只重试 ReviewResultDTO.retryTasks 的第一个任务。
重试结果要 replace 原 executionResults 中同 taskId 的结果。
taskExecutionResults 可以保留两次执行记录。
```

## 10. Replan 路径事件顺序

对应测试：

```text
replansWhenReviewerRequestsNewTasksAndExecutesOnlyNewTasks
```

事件顺序必须保持：

```text
1  team_start
2  team_plan
3  team_task_start
4  team_task_result
5  team_review
6  team_retry
7  team_plan
8  team_task_start
9  team_tool_call
10 team_tool_result
11 team_task_result
12 team_review
13 team_final
```

Graph 路径：

```text
review
  -> route_after_review route REPLAN, emits team_retry
  -> plan with previousPlan / executionResults / previousReview
  -> validate_plan
  -> schedule only new tasks
  -> execute_batch
  -> review
  -> route_after_review route FINAL
  -> final_answer
```

关键约束：

```text
第二次 PlanTeamCommand.previousPlan = 第一次 plan
第二次 PlanTeamCommand.previousResults = 已完成 executionResults
第二次 PlanTeamCommand.previousReview.replanRequired = true
execute_batch 只执行新增 task id
```

## 11. Replan 空批次 fallback 路径

当前代码有特殊 fallback：

```text
如果 replan 后没有新任务执行，则调用 fallbackModelAnswer。
如果 fallback 有答案，直接 emit team_final 并返回。
```

Graph 中建议保留：

```text
schedule 输出 scheduledTasks = []
execute_batch 不发 task 事件
review 前或 final_answer 判断空批次 fallback
```

更清晰的设计：

```text
route_after_review route REPLAN
  -> plan
  -> validate_plan
  -> schedule
  -> execute_batch
  -> fallback_if_empty_batch
      if finalAnswer present -> final_answer
      else -> review
```

但第一版为了少加节点，也可以把这段逻辑放在 `execute_batch` 或 `final_answer`。

建议：

```text
单独设计 fallback_if_empty_batch 节点，但第一版实现时可以合并到 final_answer。
```

## 12. Final fallback 路径

对应测试：

```text
includesFallbackModelUsageWhenFinalAnswerIsBlank
```

触发条件：

```text
finalAnswerBuilder.build(...) 返回空
或 looksLikePromptEcho(finalAnswer) 为 true
```

必须保持：

```text
fallbackModelAnswer 使用 ModelInvokeService
fallback model usage 计入 totalUsage
fallback model usage 调 TokenUsageService.record
```

事件顺序：

```text
仍然只需要最终有 team_final
```

不新增：

```text
team_fallback
```

除非前端和测试一起扩展。

## 13. 多任务正常路径

对应测试：

```text
returnsUsableMultiTaskResultsWithoutBlockingFinalModelCall
```

事件模式：

```text
team_start
team_plan
task1 start/result
task2 start/result
team_review
team_final
```

关键约束：

```text
TaskDependencySorter.sort 决定执行顺序。
后置任务 previousResults 包含前置任务结果。
如果 answerDraft/finalAnswer 可用，不调用 fallback ModelInvokeService。
```

## 14. Step 分配规则

建议规则：

```text
step 从 1 开始。
team_start 使用 step=1。
每 emit 一个 TeamRuntimeEventDTO，step + 1。
不发 SSE 的 node 不增加 step。
```

这意味着：

```text
build_context 不增 step。
validate_plan 不增 step。
schedule 不增 step。
route_after_review 只有 emit team_retry 时才增 step。
```

`ExecuteBatchNode` 内部不要各自维护局部 step 后忘记回写。它应该返回：

```text
step = oldStep + emittedEventCount
```

## 15. Graph 改造验收标准

真正实现后，以下断言必须继续成立：

```text
DefaultTeamRuntimeServiceTest.runsPlannerExecutorReviewerAndEmitsEventsInOrder
DefaultTeamRuntimeServiceTest.retriesReviewerRequestedTaskOnce
DefaultTeamRuntimeServiceTest.replansWhenReviewerRequestsNewTasksAndExecutesOnlyNewTasks
DefaultTeamRuntimeServiceTest.includesFallbackModelUsageWhenFinalAnswerIsBlank
DefaultTeamRuntimeServiceTest.returnsUsableMultiTaskResultsWithoutBlockingFinalModelCall
DefaultTeamRuntimeServiceTest.emitsToolCallAndToolResultAroundToolTask
```

Gateway/Web 也必须继续通过：

```text
InternalAiControllerTest
ChatControllerTest
```

这比 graph 代码是否“优雅”更重要。

## 16. 设计结论

TeamGraphState 不是一个随便塞对象的运行时 Map，它是后续可观测、可恢复、可测试的核心状态契约。

SSE 事件顺序不是附属细节，而是 Team 模式对前端和用户体验的行为契约。

所以 LangGraph4j 改造的优先级应该是：

```text
先保持行为完全等价
再利用 graph 优化结构
最后再谈 checkpoint / 并发 / human-in-the-loop
```
