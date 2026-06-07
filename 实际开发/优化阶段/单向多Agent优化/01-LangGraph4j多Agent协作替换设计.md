# LangGraph4j 多 Agent 协作替换设计

## 1. 背景与目标

当前项目已经有 `TEAM` 执行模式，入口在 `DefaultAgentRuntimeService`，内部通过 `TeamRuntimeService` 调到自研的 `DefaultTeamRuntimeService`，再按 Planner -> Executor -> Reviewer 的顺序串行推进。用户现在明确要求：不新增可选模式，不保留当前 Team 运行时作为主链路，而是直接把现有 `TEAM` 模式替换为基于 LangGraph4j 的多 Agent 协作运行时。

本设计的目标是：

- 外部接口不变：`Profile.executionMode=TEAM`、Gateway SSE、前端 Team 面板继续使用现有语义。
- 内部实现替换：删除或下线 `DefaultTeamRuntimeService`，由 `LangGraphTeamRuntimeService implements TeamRuntimeService` 成为唯一 Team 运行时。
- 使用 LangGraph4j 承担宏观状态机：规划、校验、调度、批量执行、评估、重规划、全局审查、反思、最终回答。
- 复用项目现有能力：模型调用、工具调度、Context Builder、Trace、Token 配额、SSE 事件、经验/记忆系统都沿用现有模块边界。
- 支持单向多 Agent：任务由 Planner 统一拆解和编排，Executor 执行子任务，Task Evaluator 和 Global Reviewer 反馈给 Planner/Orchestrator，Reflector 只在末尾沉淀经验，不反向操控用户会话。

## 2. 借鉴项目结论

### 2.1 agent-openai-java-banking-assistant-langgraph4j

可借鉴点：

- `StateGraph + AgentState + NodeAction` 的标准组织方式清晰，适合封装为 `LangGraphTeamGraphFactory`。
- Supervisor 节点负责路由，普通 Agent 节点专注执行，职责边界适合本项目的 Planner/Executor/Reviewer 拆分。
- `RunnableConfig.threadId` 与 `MemorySaver` 的使用方式可对应本项目 `traceId/sessionId`，便于后续恢复和调试。

取舍：该项目更像固定 Supervisor 路由到领域 Agent，不解决动态任务 DAG，因此只借鉴 Supervisor/Agent 节点结构，不照搬业务流程。

### 2.2 deep-researcher-agent

可借鉴点：

- 父图嵌入子图：Supervisor Graph 可以调用 Researcher Graph，这正好对应本项目 `execute_batch` 中复杂任务调用 `ReActTaskGraph`。
- 并发子任务：通过 `CompletableFuture` 同时启动多个 researcher，再合并结果，适合本项目执行 ready task batch。
- 条件边循环：Supervisor 判断继续研究、压缩结果、结束，适合本项目的 `route_after_tasks` 和 `decision` 节点。
- `setMaxIterations` 防止图循环失控，必须映射到本项目 TeamLimiter。

取舍：该项目面向 research，不直接使用其 Prompt，但保留“父图调度 + 子图执行 + 并发 + 汇总”的运行模型。

### 2.3 spring-ai-langgraph4j-multi-agent-test

可借鉴点：

- Plan-Execute-Replan 是本需求最贴近的示例：`plan -> validate -> execute -> replan`。
- Edge 类独立封装条件判断，便于测试和避免节点里堆太多 if/else。
- State 中记录 `rePlanCount`、连续失败次数、plan hash，可防止重复坏计划反复执行。
- `WorkflowStreamService` 把 LangGraph4j `NodeOutput` 转为 SSE chunk，可映射现有 Team SSE 事件。

取舍：该项目偏 Demo，本项目需要加入工具权限、Trace、Token、敏感数据扫描、任务 DAG 调度等平台能力。

### 2.4 KUI

可借鉴点：

- `MessagesState` 风格适合保存图运行过程中的消息列表和节点输出。
- GraphExecutionService 将图执行与外层服务隔离，适合本项目把编译图、运行图、流式事件适配分开。
- 文本节点通过 observer 做片段级输出，后续可以扩展为 token 级流式 Team 输出。

取舍：第一版先做节点级 SSE，避免在替换 Team Runtime 时同时重构 token streaming。

### 2.5 MedConsensus

可借鉴点：

- 多 Reviewer 并行评审 + 决策节点汇总，非常适合本项目 Global Reviewer 和 Task Evaluator。
- JSON 输出抽取与 Bean Validation 思路适合强化 Planner/Executor/Reviewer 的结构化输出校验。
- 决策节点可以路由到 retry、human review、finalize，本项目第一版对应 retry/replan/final。

取舍：不引入医疗领域逻辑，只借鉴“评审委员会 + 决策层 + JSON Validator”。

## 3. 替换范围

### 3.1 保持不变

- 前端和 Gateway 仍传 `executionMode=TEAM`。
- `DefaultAgentRuntimeService` 仍通过 `TeamRuntimeService` 接口调用 Team 运行时。
- 现有 Team DTO 和 SSE event type 尽量兼容：`team_start`、`team_plan`、`team_task_start`、`team_tool_call`、`team_tool_result`、`team_task_result`、`team_review`、`team_retry`、`team_final`。
- Planner/Executor/Reviewer 的结构化 DTO 可以先复用：`TaskPlanDTO`、`TeamTaskDTO`、`ExecutionResultDTO`、`ReviewResultDTO`。
- 工具调用仍只由 Executor/Task ReAct 子图触发，Planner 和 Reviewer 不拿工具执行权限。

### 3.2 替换/下线

- 下线 `DefaultTeamRuntimeService` 作为 Spring Bean。
- 新增 `LangGraphTeamRuntimeService implements TeamRuntimeService`。
- 新增 `core.team.graph` 包组织 LangGraph4j 图、状态、节点、边、子图。
- 原有 `DefaultTeamPlanner`、`DefaultTeamExecutor`、`DefaultTeamReviewer` 可在第一版作为节点内部的领域服务复用；后续再逐步拆成更细的 Agent Node。
- 原有串行 Orchestrator 逻辑不再驱动主流程，主流程由 LangGraph4j 的 `StateGraph` 和 conditional edge 驱动。

## 4. 推荐包结构

```text
agent-platform-core/src/main/java/com/ls/agent/core/team
├── api
│   └── TeamRuntimeService.java
├── application
│   ├── LangGraphTeamRuntimeService.java
│   ├── TeamRuntimeProperties.java
│   └── TeamRuntimeEventAdapter.java
├── graph
│   ├── LangGraphTeamGraphFactory.java
│   ├── LangGraphTeamState.java
│   ├── LangGraphTeamStateSerializer.java
│   ├── node
│   │   ├── PlanNode.java
│   │   ├── ValidatePlanNode.java
│   │   ├── ScheduleNode.java
│   │   ├── ExecuteBatchNode.java
│   │   ├── EvaluateTasksNode.java
│   │   ├── ReplanNode.java
│   │   ├── GlobalReviewNode.java
│   │   ├── DecisionNode.java
│   │   ├── ReflectNode.java
│   │   └── FinalAnswerNode.java
│   ├── edge
│   │   ├── AfterPlanEdge.java
│   │   ├── AfterTaskEvaluationEdge.java
│   │   └── AfterGlobalReviewEdge.java
│   └── subgraph
│       ├── ReActTaskGraphFactory.java
│       ├── ReActTaskState.java
│       ├── ReActThinkNode.java
│       ├── ReActToolNode.java
│       └── ReActObserveNode.java
└── validation
    ├── TeamJsonOutputParser.java
    └── TeamGraphStateValidator.java
```

## 5. LangGraphTeamState 设计

`LangGraphTeamState` 是整个图的唯一运行状态，不直接保存 Spring Bean，只保存可序列化业务数据和运行计数。

核心字段：

| 字段 | 说明 |
|---|---|
| traceId / sessionId / userId / tenantId / applicationId | 与 Trace、SSE、配额和隔离绑定 |
| originalUserMessage | 原始用户任务 |
| contextSnapshot | Context Builder 生成的运行上下文摘要 |
| authorizedTools | 当前 Profile/User 可用工具清单，只给 Executor 使用 |
| plan | 当前 `TaskPlanDTO` |
| taskGraph | taskId -> task，包含 dependsOn、taskType、status |
| readyTaskIds | 本轮可执行任务 |
| runningTaskIds | 正在执行任务 |
| completedResults | taskId -> `ExecutionResultDTO` |
| failedResults | taskId -> 失败原因和异常分类 |
| taskEvaluations | taskId -> 子任务评估结果 |
| globalReview | 整体审查结果 |
| answerDraft / finalAnswer | Orchestrator 草稿与最终回答 |
| retryCount / replanCount | 重试与重规划次数 |
| modelCallCount / toolCallCount | 总模型/工具调用次数 |
| lastError | 最近一次异常，供 ReplanNode 使用 |
| experienceNotes | ReflectNode 生成的经验摘要 |

## 6. 主图节点职责

### 6.1 plan

Planner 根据用户任务和上下文生成结构化计划。输出必须包含：

- 任务列表：`taskId`、`title`、`description`、`taskType`、`dependsOn`、`expectedOutput`。
- 静态拆分：一次性识别明显子任务。
- 动态拆分预留：标记需要根据执行结果再拆的任务。
- 任务粒度：每个子任务应能独立验收，通常对应一次明确的信息获取、工具调用、分析或生成，不把一个复杂目标硬塞到一个任务，也不把一个单步工具调用拆成多个伪任务。

### 6.2 validate_plan

校验 Planner 输出：

- JSON Schema / Bean Validation 通过。
- taskId 唯一。
- dependsOn 无环。
- taskType 合法。
- 任务数不超过 `maxTasks`。
- 工具任务没有越权工具。

失败处理：首次返回 Planner 修正；连续失败则降级为一个 `MODEL_TASK`。

### 6.3 schedule

根据依赖关系计算本轮 ready tasks：

- 所有 dependsOn 均已完成。
- 当前任务未完成、未运行、未超过单任务重试上限。
- ready 数量受 `maxParallelTasks` 限制。

这里不使用 LangGraph4j 动态分支创建任务节点，因为运行期生成 DAG 时，静态 parallel branch 不够自然。主图只控制宏观状态，动态并发放在 `execute_batch` 节点内部。

### 6.4 execute_batch

并发执行本轮 ready tasks：

- `TOOL_TASK`：直接调用授权工具/MCP/Skill，返回结构化结果。
- `MODEL_TASK`：调用模型完成单步分析或生成，不调用工具。
- `REACT_TASK`：调用嵌套 `ReActTaskGraph`，允许 think -> tool -> observe 循环。

异常处理：

- 工具调用失败、模型异常、JSON 解析失败、超时都写入 `failedResults` 和 `lastError`。
- 可恢复异常反馈给 Planner/ReplanNode。
- 不可恢复异常进入 GlobalReview，由最终回答说明风险。

### 6.5 evaluate_tasks

对子任务结果做局部验收：

- 是否满足 `expectedOutput`。
- 工具返回是否可信、是否为空、是否存在错误码。
- ReAct 是否达到停止条件。
- 是否需要补充任务或重试。

该节点只给评价，不重新规划、不调用工具。

### 6.6 route_after_tasks

条件边判断：

- 有失败且还有 retry/replan 预算：进入 `replan`。
- 当前 DAG 还有 ready task：回到 `schedule`。
- 所有任务完成：进入 `global_review`。
- 超过上限：进入 `global_review`，并标记 degraded。

### 6.7 replan

Planner 基于当前计划、已完成结果、失败原因、局部评价重新规划：

- 优先只补新增任务或替换失败任务。
- 不重复执行已完成且通过评估的任务。
- 如果计划哈希连续重复，停止重规划，进入 GlobalReview。

### 6.8 global_review

整体任务评估者审查：

- 原始用户目标是否完成。
- 子任务之间是否矛盾。
- 是否有未覆盖问题。
- 最终草稿是否可直接交付。
- 是否需要再规划一次。

### 6.9 decision

决策节点只做路由：

- `PASS`：进入 `reflect`。
- `REPLAN_REQUIRED` 且预算允许：进入 `replan`。
- `RETRY_TASKS`：回到 `schedule` 或 `execute_batch`。
- `FAILED_BUT_ANSWERABLE`：进入 `final_answer`，带风险说明。
- `UNRECOVERABLE`：进入 `final_answer`，给出失败原因和可操作建议。

### 6.10 reflect

反思者根据执行轨迹和审查结果沉淀经验：

- 哪类任务适合拆成并行任务。
- 哪类工具失败需要降级路线。
- 哪些 Planner 输出格式错误需要加入提示约束。
- 哪些用户领域知识可写入经验/长期记忆。

第一版只生成经验摘要并通过现有 experience/memory 服务可选写入，不能阻塞最终回答。

### 6.11 final_answer

由 Orchestrator 生成最终回答，不让 Reviewer 直接产出最终答案。最终回答需要说明：

- 已完成结论。
- 关键依据。
- 未完成或失败项。
- 风险说明。
- 可选后续建议。

## 7. ReActTaskGraph 子图

复杂子任务不应在 `execute_batch` 中写 while 循环，而是用子图封装：

```text
START -> think -> should_call_tool?
  -> tool -> observe -> think
  -> finish -> END
```

子图约束：

- 只拿当前子任务允许的工具集合。
- 有 `maxReActSteps`、`maxToolCallsPerTask`、`timeoutMs`。
- 每次 tool call 都发 `team_tool_call` / `team_tool_result`。
- 子图结果回填父图的 `completedResults` 或 `failedResults`。

## 8. 并行与任务粒度策略

### 8.1 粒度规则

- 一个 `TOOL_TASK` 应尽量对应一次明确工具调用或一组强绑定工具调用。
- 一个 `MODEL_TASK` 应对应一个可独立验收的分析/总结/生成目标。
- 一个 `REACT_TASK` 用于需要多轮观察和决策的复杂子目标。
- 不为了并发而拆分强依赖步骤。
- 不把所有任务都拆到句子级，否则上下文、Trace、Token 和评估成本会失控。

### 8.2 并行策略

- Planner 输出 `dependsOn`。
- ScheduleNode 每轮找出入度为 0 的 ready tasks。
- ExecuteBatchNode 使用 `CompletableFuture` 并发执行 ready tasks。
- 执行完成后统一进入 EvaluateTasksNode。
- 失败任务不会阻塞无依赖的其他 ready tasks，但会影响 GlobalReview 和 Replan。

## 9. SSE / Trace / Token 集成

| 图阶段 | SSE 事件 | Trace Span |
|---|---|---|
| run start | `team_start` | `team.graph.start` |
| plan | `team_plan` | `team.plan` |
| schedule | `team_schedule` 或 attributes | `team.schedule` |
| task start | `team_task_start` | `team.task` |
| tool call | `team_tool_call` | `team.tool.call` |
| tool result | `team_tool_result` | `team.tool.result` |
| task result | `team_task_result` | `team.task.result` |
| task evaluation | `team_task_evaluation` | `team.task.evaluate` |
| retry/replan | `team_retry` | `team.replan` |
| global review | `team_review` | `team.global.review` |
| reflect | `team_reflect` | `team.reflect` |
| final | `team_final` | `team.final` |

第一版可先不新增前端展示事件，只把新事件写入 trace attributes；前端必须兼容已有事件不破坏。

## 10. 限流与降级

必须有 Team 总上限：

- `maxTasks`
- `maxParallelTasks`
- `maxRetries`
- `maxReplans`
- `maxToolCalls`
- `maxModelCalls`
- `maxReActSteps`
- `timeoutMs`

降级路线：

- Planner JSON 连续失败：降级单 `MODEL_TASK`。
- 工具不可用：转 `MODEL_TASK` 说明缺少实时/外部信息。
- 子图超时：保留已完成任务，进入 GlobalReview。
- GlobalReview 不通过且预算耗尽：返回当前最优答案和风险。

## 11. 迁移步骤

1. 引入 LangGraph4j 依赖，确认版本与 Java 17、Spring Boot 3.4.13 兼容。
2. 新建 `core.team.graph` 状态、节点、边和图工厂。
3. 新建 `LangGraphTeamRuntimeService implements TeamRuntimeService`。
4. 将 `DefaultTeamRuntimeService` 从 Spring Bean 中移除或删除。
5. 保留/复用 `DefaultTeamPlanner`、`DefaultTeamExecutor`、`DefaultTeamReviewer` 作为节点内部能力。
6. 接入 `TeamRuntimeEventAdapter`，把 `NodeOutput` 和节点内部事件转成现有 SSE。
7. 接入 Trace Span 和 Token 计数。
8. 补充单元测试：边路由、计划校验、调度排序、失败重规划、预算耗尽。
9. 补充集成测试：`TEAM` 模式从入口到 final answer 的闭环。
10. 前端只做兼容性修补，不新增模式开关。

## 12. 测试清单

- `TEAM` 模式能正常从 `DefaultAgentRuntimeService` 进入 `LangGraphTeamRuntimeService`。
- Planner 输出非法 JSON 时能修正或降级。
- dependsOn 有环时能拒绝并重规划。
- 两个无依赖任务能并发执行。
- 失败工具调用能反馈给 ReplanNode。
- `REACT_TASK` 能通过子图完成工具循环并回填父图。
- GlobalReview 不通过时最多按预算 replan。
- 预算耗尽时不会死循环。
- SSE 事件顺序可被前端消费。
- Trace 中能看到 graph/node/task/tool 四级信息。
- Token 配额仍由 Gateway/Core 现有链路结算，不因并发遗漏。

## 13. 关键决策

- 不新增 `LANGGRAPH_TEAM` 模式，直接替换 `TEAM` 内部实现。
- LangGraph4j 管宏观状态流转，动态任务并发在 `execute_batch` 节点内部完成。
- 第一版保留旧 DTO 和多数事件，降低前后端联调成本。
- Planner/Reviewer 不调用工具，工具权限只给 Executor 和 ReActTaskGraph。
- Reviewer 不生成最终答案，最终答案由 Orchestrator/FinalAnswerNode 统一生成。
- Reflector 不影响主结果，经验写入失败不阻塞用户响应。
