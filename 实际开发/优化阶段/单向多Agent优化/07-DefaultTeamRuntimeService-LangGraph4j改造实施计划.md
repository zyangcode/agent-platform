# DefaultTeamRuntimeService LangGraph4j 改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` when implementing this plan task-by-task. 本文档服务于本项目已有 Team Runtime，不是新建一套并行 Team 入口。

**Goal:** 保留 `DefaultTeamRuntimeService` 作为默认 Team 运行时入口，把内部手写编排迁移为 LangGraph4j `CompiledGraph` 编排。

**Architecture:** 对外接口、Bean 名义、单 Agent 主链路、SSE 事件格式、Trace/Token 记录保持不变；内部新增 `core.team.graph` 包，把 Planner / Executor / Reviewer 的调用拆成 graph state + graph node。第一版只替换编排方式，不同时引入任务并发、checkpoint 持久化、Team 表。

**Tech Stack:** Java 17, Spring Boot 3.4.13, Maven, LangGraph4j, existing `core.team` Planner/Executor/Reviewer, existing SSE/Trace/Token infrastructure.

---

## 1. 最终判断

当前更合适的是方案 A：

```text
保留 DefaultTeamRuntimeService 名字
保留 TeamRuntimeService 接口
保留 DefaultAgentRuntimeService -> TeamRuntimeService 的调用关系
只把 DefaultTeamRuntimeService 内部从手写流程换成 LangGraph4j graph
```

原因很直接：

```text
DefaultTeamRuntimeService 现在已经是默认 Team 入口
DefaultAgentRuntimeService / Gateway / Web SSE 都已经依赖 TeamRuntimeService 抽象
新增 LangGraphTeamRuntimeService 会多出 Bean 切换、命名解释和测试分支
当前目标是升级 Team 内核，不是新增一条对外执行模式
```

名字看不出 LangGraph4j 的问题，用内部包结构解决：

```text
DefaultTeamRuntimeService
  -> TeamGraphFactory
  -> TeamGraphState
  -> graph/node/*
```

这样读代码时仍能明确知道内部已经是 LangGraph4j 驱动。

## 2. 必须保留的现有能力

改造不是重写 Team，而是把现有 orchestrator 拆成图节点。以下能力必须继续保留：

| 能力 | 当前位置 | 改造要求 |
| --- | --- | --- |
| Team 入口 | `TeamRuntimeService`, `DefaultTeamRuntimeService` | 接口不变 |
| 单 Agent 委托 Team | `DefaultAgentRuntimeService` | 不改单 Agent 主链路 |
| Planner | `DefaultTeamPlanner` | 继续只产出 `TaskPlanDTO` |
| Executor | `DefaultTeamExecutor` | 继续只执行 `MODEL_TASK` / `TOOL_TASK` |
| Reviewer | `DefaultTeamReviewer` | 继续只审查，不执行工具 |
| 上限控制 | `TeamRunLimiter` | 每次 run 创建独立 limiter |
| 任务依赖排序 | `TaskDependencySorter` | 第一版继续串行排序执行 |
| 计划校验 | `TaskPlanValidator` | 可以接入 `validate_plan` node |
| 草稿生成 | `TeamAnswerDraftBuilder` | 继续由 Orchestrator/Node 生成 |
| 最终答案 | `TeamFinalAnswerBuilder` + fallback model | 保留当前 fallback 行为 |
| SSE 事件 | `TeamRuntimeEventDTO` | 类型和顺序尽量不变 |
| Trace | `TraceService` | 保留 span 名称和父子关系 |
| Token 记录 | `TokenUsageService` | 每次模型调用继续记录 |

## 3. 现有流程到 Graph 的映射

当前 `DefaultTeamRuntimeService.run()` 实际流程：

```text
validate command
new TeamRunLimiter
start team.run span
emit team_start
require conversationId
build context
resolve tools
planner.plan
record planner token usage
emit team_plan
execute tasks by dependency order
emit task/tool events
build answerDraft
review
emit team_review
if retry:
  retry one existing task
  review again
else if replan:
  planner.plan with previous plan/results/review
  execute only new task ids
  review again
final answer
fallback model if final answer is blank or prompt echo
emit team_final
aggregate usage
finish team.run span
return AgentRunResult
```

建议第一版 graph：

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

第一版不要做动态 graph node。即使 replan 后任务数量变化，也由 `plan` node 更新 `TeamGraphState.plan`，由 `schedule` node 重新计算待执行任务，由 `execute_batch` node 在节点内部执行。

## 4. 文件结构

新增：

```text
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/TeamGraphFactory.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/TeamGraphState.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/TeamGraphRuntimeContext.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/TeamGraphNodeNames.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/TeamGraphRoute.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/BuildContextNode.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/PlanNode.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/ValidatePlanNode.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/ScheduleNode.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/ExecuteBatchNode.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/ReviewNode.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/RouteAfterReview.java
agent-platform-core/src/main/java/com/ls/agent/core/team/graph/node/FinalAnswerNode.java
```

修改：

```text
pom.xml
agent-platform-core/pom.xml
agent-platform-core/src/main/java/com/ls/agent/core/team/application/DefaultTeamRuntimeService.java
agent-platform-core/src/test/java/com/ls/agent/core/team/application/DefaultTeamRuntimeServiceTest.java
```

可选新增测试：

```text
agent-platform-core/src/test/java/com/ls/agent/core/team/graph/TeamGraphFactoryTest.java
agent-platform-core/src/test/java/com/ls/agent/core/team/graph/node/RouteAfterReviewTest.java
```

## 5. 依赖接入

父 POM 增加版本属性：

```xml
<langgraph4j.version>1.8.17</langgraph4j.version>
```

父 POM 通过 BOM 管理 LangGraph4j 版本：

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-bom</artifactId>
    <version>${langgraph4j.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

`agent-platform-core/pom.xml` 增加依赖：

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core</artifactId>
</dependency>
```

实施时必须先跑依赖解析：

```powershell
mvn.cmd -pl agent-platform-core -DskipTests compile
```

已验证结果：

```text
2026-06-09 已验证 langgraph4j-bom/langgraph4j-core 1.8.17 可以解析。
mvn.cmd -pl agent-platform-core -am -DskipTests compile 通过。
```

API 校准结果：

```text
TeamGraphState 需要继承 org.bsc.langgraph4j.state.AgentState。
StateGraph 构造可以使用 new StateGraph<>(TeamGraphState::new)。
Node 使用 node_async(...) 包装，节点返回 Map<String, Object> 增量更新。
CompiledGraph.invoke(Map<String, Object>) 返回 Optional<TeamGraphState>。
START / END 不要手写字符串，应该引用 GraphDefinition.START / GraphDefinition.END。
真实值是 "__START__" / "__END__"，不是 "__start__" / "__end__"。
```

如果后续升级 LangGraph4j 版本，必须重新跑 `TeamGraphFactoryTest`，确认最小 graph 仍能 compile/invoke。

## 6. State 设计

第一版 `TeamGraphState` 只保存本次 run 的业务状态，不放 Spring service、mapper、entity：

```java
public final class TeamGraphState {
    private AgentRunCommand command;
    private Long conversationId;
    private AgentContextDTO context;
    private List<AgentToolDTO> availableTools = List.of();

    private TaskPlanDTO plan;
    private TaskPlanDTO previousPlan;
    private List<TeamTaskDTO> scheduledTasks = List.of();
    private List<TeamTaskExecutionResultDTO> taskExecutionResults = new ArrayList<>();
    private List<ExecutionResultDTO> executionResults = new ArrayList<>();

    private List<TeamPlanResultDTO> planResults = new ArrayList<>();
    private List<TeamReviewResultDTO> reviewResults = new ArrayList<>();
    private List<ModelInvokeResult> fallbackModelInvocations = new ArrayList<>();

    private String answerDraft = "";
    private ReviewResultDTO review;
    private String finalAnswer = "";
    private ModelUsageDTO usage;

    private int step = 1;
    private Long runSpanId;
    private String route = "final";
    private String retryTaskId;
}
```

如果 LangGraph4j 当前版本要求 state 继承它自己的状态基类，则按实际 API 包一层 adapter，但字段边界不变。

`TeamGraphRuntimeContext` 放运行时对象：

```java
public final class TeamGraphRuntimeContext {
    private final TeamEventSink eventSink;
    private final TeamRunLimiter limiter;
    private final Long runSpanId;
}
```

更完整的实现中可以继续放：

```text
Trace helper
Token usage recorder
ObjectMapper
```

但不要把它们放进可 checkpoint 的 state。

## 7. Node 职责

### 7.1 BuildContextNode

迁移现有：

```text
buildContext(command, conversationId, runSpanId)
agentToolResolver.resolve(context)
```

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

事件：

```text
不额外 emit Team SSE
context.build 仍写 Trace span
```

### 7.2 PlanNode

迁移现有：

```text
planner.plan(new PlanTeamCommand(...))
limiter.consumeModelCalls(...)
recordModelInvocations(...)
emit team_plan
```

首次规划：

```java
new PlanTeamCommand(command.userInput(), context, tools)
```

replan：

```java
new PlanTeamCommand(
    command.userInput(),
    context,
    tools,
    previousPlan,
    executionResults,
    review
)
```

输出：

```text
plan
planResults append
```

### 7.3 ValidatePlanNode

职责：

```text
limiter.checkTaskCount(plan.tasks().size())
TaskPlanValidator.validate(plan, availableToolNames)
```

注意：当前 `DefaultTeamRuntimeService` 只显式调用 `limiter.checkTaskCount`，但项目已有 `TaskPlanValidator`。LangGraph4j 改造时可以把 `TaskPlanValidator` 正式接入，这属于合理增强。

### 7.4 ScheduleNode

职责：

```text
scheduledTasks = taskDependencySorter.sort(plan.tasks())
```

replan 时只执行新任务：

```text
previousPlan != null 时
过滤 previousPlan task ids
过滤 executionResults 已完成 task ids
```

### 7.5 ExecuteBatchNode

职责：

```text
按 scheduledTasks 串行执行
每个任务前 checkTimeout
emit team_task_start
TOOL_TASK 先 emit team_tool_call
executor.execute(...)
consume model/tool calls
record model token usage
emit team_tool_result
emit team_task_result
```

第一版必须保持串行，不要同时引入并发。后续如果要并发，只在这个 node 内部做：

```text
按 dependsOn 分层
同层任务并发
maxConcurrentTasks 限流
合并结果时按拓扑顺序稳定排序
```

### 7.6 ReviewNode

迁移现有：

```text
answerDraftBuilder.build(...)
reviewer.review(...)
limiter.consumeModelCalls(...)
recordModelInvocations(...)
emit team_review
```

输出：

```text
answerDraft
review
reviewResults append
```

### 7.7 RouteAfterReview

职责：

```text
if review failed and retryTasks not empty:
  limiter.consumeRetry()
  retryTaskId = first retry task id
  scheduledTasks = only retry task
  route = retry
  emit team_retry

else if review failed and replanRequired true:
  limiter.consumeRetry()
  previousPlan = plan
  route = replan
  emit team_retry

else:
  route = final
```

这一层只做路由判断，不调用 Planner/Executor/Reviewer。

### 7.8 FinalAnswerNode

迁移现有：

```text
finalAnswerBuilder.build(answerDraft, review)
if blank or prompt echo:
  fallbackModelAnswer(...)
aggregate total usage
emit team_final
```

输出：

```text
finalAnswer
usage
```

## 8. DefaultTeamRuntimeService 改造后形态

改造后 `DefaultTeamRuntimeService` 只保留入口职责：

```java
@Override
public AgentRunResult run(AgentRunCommand command, TeamEventSink eventSink) {
    validate(command);
    TeamEventSink activeEventSink = eventSink == null ? this.eventSink : eventSink;
    TeamRunLimiter limiter = limiterTemplate.newRun();

    TraceSpanDTO runSpan = safeStartSpan(... "team.run" ...);
    TeamGraphState initialState = TeamGraphState.initial(command, runSpan == null ? null : runSpan.id());
    TeamGraphRuntimeContext runtimeContext = new TeamGraphRuntimeContext(activeEventSink, limiter, spanId(runSpan));

    emit(activeEventSink, TeamRuntimeEventDTO.start(command.traceId(), 1, "Team run started", null));

    try {
        TeamGraphState finalState = teamGraphFactory.invoke(initialState, runtimeContext);
        safeFinishSpan(runSpan, "SUCCESS", null, null);
        return new AgentRunResult(finalState.conversationId(), finalState.finalAnswer(), finalState.usage());
    } catch (Exception ex) {
        safeFinishSpan(runSpan, "FAILED", errorCode(ex), errorMessage(ex));
        throw ex;
    }
}
```

注意：上面是目标形态示意，具体 `teamGraphFactory.invoke` 和 LangGraph4j state update 写法要以实际依赖版本 API 为准。

## 9. 测试策略

现有 `DefaultTeamRuntimeServiceTest` 是最重要的回归测试，不要删。改造后至少继续覆盖：

```text
runsPlannerExecutorReviewerAndEmitsEventsInOrder
retriesReviewerRequestedTaskOnce
replansWhenReviewerRequestsNewTasksAndExecutesOnlyNewTasks
includesFallbackModelUsageWhenFinalAnswerIsBlank
returnsUsableMultiTaskResultsWithoutBlockingFinalModelCall
emitsToolCallAndToolResultAroundToolTask
```

这些测试实际锁住了关键行为：

```text
事件顺序
step 递增
Planner / Executor / Reviewer 调用次数
replan 时第二次 PlanTeamCommand 携带 previousPlan / previousResults / previousReview
replan 只执行新增 task id
tool call 和 tool result 包在 TOOL_TASK 周围
usage 聚合包含 planner / executor / reviewer / fallback
```

新增 `RouteAfterReviewTest` 建议覆盖：

```text
review passed -> route final
review failed + retryTasks -> route retry and scheduledTasks only one
review failed + replanRequired + no retryTasks -> route replan and previousPlan set
retry 超上限 -> throws BizException
```

新增 `TeamGraphFactoryTest` 建议覆盖：

```text
graph can compile
normal path reaches final_answer
retry path loops back execute_batch once
replan path loops back plan once
```

## 10. 实施步骤

### Task 1: 依赖和 API 验证

状态：已完成。

修改：

```text
pom.xml
agent-platform-core/pom.xml
```

执行：

```powershell
mvn.cmd -pl agent-platform-core -DskipTests compile
```

目标：

```text
确认 langgraph4j-core 能解析
确认 Java 17 编译通过
确认具体 StateGraph / CompiledGraph API
```

实际落地：

```text
pom.xml 增加 langgraph4j.version=1.8.17 和 langgraph4j-bom。
agent-platform-core/pom.xml 增加 langgraph4j-core。
通过 javap 校准 StateGraph / CompiledGraph / AgentState / GraphDefinition API。
```

### Task 2: 创建 graph 包和最小 state

状态：已完成最小骨架。

新增：

```text
TeamGraphState
TeamGraphRuntimeContext
TeamGraphNodeNames
TeamGraphRoute
```

目标：

```text
先让 state 字段完整表达当前 DefaultTeamRuntimeService 需要的数据
不接入业务逻辑
编译通过
```

实际落地：

```text
TeamGraphState 继承 AgentState，并提供 command/context/plan/results/review/finalAnswer 等 typed getter。
TeamGraphRoute 定义 RETRY / REPLAN / FINAL。
TeamGraphNodeNames 引用 GraphDefinition.START / GraphDefinition.END。
TeamGraphRuntimeContext 保存 TeamEventSink / TeamRunLimiter / runSpanId。
```

### Task 3: 创建 TeamGraphFactory 的空图 smoke test

状态：已完成最小 smoke test。

新增：

```text
TeamGraphFactory
TeamGraphFactoryTest
```

目标：

```text
确认能 compile graph
确认能 invoke 一次
确认能从初始 state 到最终 state
```

实际落地：

```text
新增 TeamGraphFactoryTest。
新增 TeamGraphFactory，当前只包含 START -> final_answer -> END 的最小 graph。
mvn.cmd -pl agent-platform-core -am -Dtest=TeamGraphFactoryTest "-Dsurefire.failIfNoSpecifiedTests=false" test 通过。
```

### Task 4: 迁移 build_context / plan / validate_plan / schedule

新增 node：

```text
BuildContextNode
PlanNode
ValidatePlanNode
ScheduleNode
```

验证：

```powershell
mvn.cmd -pl agent-platform-core -Dtest=DefaultTeamRuntimeServiceTest test
```

此阶段允许 `DefaultTeamRuntimeService` 仍然手写后半段流程，但前半段可以先通过 node 调用，以降低一次性迁移风险。

### Task 5: 迁移 execute_batch / review / route

新增 node：

```text
ExecuteBatchNode
ReviewNode
RouteAfterReview
```

重点保持：

```text
task/tool SSE 事件顺序
retry 只重试一个已有任务
replan 只执行新增任务
limiter 消耗逻辑不变
```

验证：

```powershell
mvn.cmd -pl agent-platform-core -Dtest=DefaultTeamRuntimeServiceTest test
```

### Task 6: 迁移 final_answer 和 fallback

新增 node：

```text
FinalAnswerNode
```

重点保持：

```text
finalAnswerBuilder.build
shouldGenerateFinalAnswer
fallbackModelAnswer
totalUsage
team_final event
```

验证：

```powershell
mvn.cmd -pl agent-platform-core -Dtest=DefaultTeamRuntimeServiceTest test
```

### Task 7: DefaultTeamRuntimeService 切到 graph 主路径

修改：

```text
DefaultTeamRuntimeService
DefaultTeamRuntimeServiceTest
```

目标：

```text
run() 内部不再手写 Planner -> Executor -> Reviewer 主流程
run() 只负责校验、创建 limiter/runtime context、启动/结束 team.run span、返回 AgentRunResult
```

### Task 8: 回归 Gateway/Web SSE

执行：

```powershell
mvn.cmd -pl agent-platform-gateway -Dtest=InternalAiControllerTest test
mvn.cmd -pl agent-platform-web -Dtest=ChatControllerTest test
```

目标：

```text
确认 team_* SSE 事件仍能透传到外层
确认最终 message/done 不受影响
```

### Task 9: 模块和全量验证

执行：

```powershell
mvn.cmd -pl agent-platform-core -Dtest=DefaultTeamPlannerTest,DefaultTeamExecutorTest,DefaultTeamReviewerTest test
mvn.cmd -pl agent-platform-core -Dtest=DefaultTeamRuntimeServiceTest test
mvn.cmd test
```

目标：

```text
Team 三角色服务未被破坏
Team Runtime 行为未被破坏
ArchUnit 包边界未被破坏
全模块回归通过
```

## 11. 风险点

### 11.1 LangGraph4j API 版本差异

网上样例可能使用不同版本，`StateGraph` / `CompiledGraph` / state schema 的 API 可能不同。必须先小步验证依赖和空图，不要直接重写大段业务代码。

### 11.2 State 可序列化边界

第一版不做 checkpoint，所以可以务实地让 state 保存 DTO。但仍不要把 Spring Bean、Mapper、Entity、线程池、MCP Client 这类运行时对象塞进 state。

### 11.3 事件顺序回归

前端和 Gateway 已经依赖 `team_start`、`team_plan`、`team_task_start`、`team_tool_call`、`team_tool_result`、`team_task_result`、`team_review`、`team_retry`、`team_final`。改造后事件顺序和 step 递增要优先由测试锁住。

### 11.4 不要同时做任务并发

LangGraph4j 改造本身已经改变主流程形态。第一版继续串行任务执行；并发优化以后只放在 `ExecuteBatchNode` 内部，不改变 graph 结构和外部接口。

### 11.5 MVP 约束与优化阶段边界

`CLAUDE.md` 中阶段 4 MVP 约束写过“不引入 Graph/Workflow 引擎”。本方案属于“优化阶段”的后续升级，不应回写成 MVP 必须项。交付说明里要明确：MVP 可以继续使用当前手写 Team Runtime；优化版再引入 LangGraph4j。

## 12. 一句话总结

```text
保留入口，替换内核；保留角色服务，替换编排方式；第一版只做图编排，不同时做并发和持久化。
```
