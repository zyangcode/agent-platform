# LangGraph4j 复杂任务项目阅读记录

本文补充阅读两个更贴近复杂任务编排的项目：

- `imfangs/langgraph4j-deep-researcher`
- `bhavuklabs/research4j`

目标是判断它们对当前项目 `LangGraphTeamRuntimeService` 的实际借鉴价值。

## 1. 总体判断

第一轮阅读 Azure 和 Dynamo 后，能确认 LangGraph4j 如何嵌入 Spring Boot，以及基础 `StateGraph` 怎么写。但那两个项目还不足以支撑当前项目的 Team 改造，因为：

```text
Azure 样例是 Supervisor 单跳路由。
Dynamo 是最小串行 graph 示例。
```

本轮两个项目的价值更接近复杂任务：

| 项目 | 核心价值 | 对当前项目的启发 |
| --- | --- | --- |
| `langgraph4j-deep-researcher` | 多轮研究、multi-graph、Supervisor 调用并发 Researcher 子图 | `execute_batch` 可以作为一个 graph node，在 node 内部受控并发执行子任务 |
| `research4j` | GraphExecutor 抽象、Legacy/LangGraph4j 双执行器、失败回退 | 当前项目可保留旧 Team Runtime，同时新增 LangGraph4j Runtime，降低改造风险 |

最终判断：

```text
deep-researcher 更适合参考复杂任务分层。
research4j 更适合参考工程迁移策略。
```

## 2. langgraph4j-deep-researcher

仓库：

```text
https://github.com/imfangs/langgraph4j-deep-researcher
```

### 2.1 项目结构

README 和源码目录显示，它有单图版本和 multi-graph 版本。

核心目录：

```text
langgraph4j-deep-researcher-core/src/main/java/io/github/imfangs/ai/deepresearch/core
  graph
  nodes
  service
  multigraphversion
    mgraph
      MainGraph.java
      SupervisorSubgraph.java
      ResearcherSubgraph.java
    mnodes
      WriteResearchBriefNode.java
      SupervisorBridgeNode.java
      SupervisorNode.java
      SupervisorToolsNode.java
      CompressNode.java
      FinalReportGenerationNode.java
      researchernode
        ResearcherNode.java
        TavilySearchTool.java
        ThinkTool.java
        ResearchCompleteTool.java
    mstate
      MainGraphState.java
      SupervisorState.java
      ResearcherState.java
    mservice
      MainGraphService.java
```

### 2.2 单图研究流程

README 中的单图流程是：

```text
User Input Research Topic
  -> QueryGeneratorNode
  -> WebSearchNode
  -> SummarizerNode
  -> ReflectionNode
  -> RouteResearch
      -> continue: Generate New Query -> WebSearchNode
      -> finalize: FinalizerNode
  -> Output Final Research Report
```

这个流程体现的是：

```text
生成查询
搜索
总结
反思知识缺口
决定继续还是结束
最终汇总
```

对当前项目的启发：

```text
Team 不是一次 Planner -> Executor -> Answer 就结束。
复杂任务需要 evaluate/review 节点判断是否继续、重试、补充信息或结束。
```

### 2.3 Multi-Graph 架构

multi-graph 版本更重要。它分三层：

```text
MainGraph
  -> writeResearchBrief
  -> supervisorBridge
  -> finalReportGeneration

SupervisorSubgraph
  -> supervisor
  -> supervisor_tools
  -> supervisor
  -> END

ResearcherSubgraph
  -> researcher
  -> compress
  -> END
```

也就是说：

```text
主图负责编排大流程。
Supervisor 子图负责多轮任务拆解和任务调度。
Researcher 子图负责具体研究任务执行和压缩结果。
```

这比 Azure 的单跳 Supervisor 更接近当前项目要做的 Team。

### 2.4 MainGraph

`MainGraph` 的结构：

```java
new StateGraph<>(MainGraphState.SCHEMA, new MainGraphSerializer())
    .addNode("writeResearchBrief", node_async(writeResearchBriefNode))
    .addNode("supervisorBridge", node_async(supervisorBridgeNode))
    .addNode("finalReportGeneration", node_async(finalReportGenerationNode))
    .addEdge(START, "writeResearchBrief")
    .addEdge("writeResearchBrief", "supervisorBridge")
    .addEdge("supervisorBridge", "finalReportGeneration")
    .addEdge("finalReportGeneration", END);
```

它的主图没有直接执行搜索，而是先生成 research brief，再通过 bridge node 调 supervisor 子图，最后汇总报告。

对当前项目的对应关系：

```text
writeResearchBrief      -> build_team_context / plan
supervisorBridge        -> execute_batch / evaluate_tasks
finalReportGeneration   -> final_answer
```

### 2.5 SupervisorSubgraph

`SupervisorSubgraph` 的结构：

```java
new StateGraph<>(SupervisorState.SCHEMA, new SupervisorStateSerializer())
    .addNode("supervisor", node_async(supervisorNode))
    .addNode("supervisor_tools", node_async(supervisorToolsNode))
    .addEdge(START, "supervisor")
    .addEdge("supervisor_tools", "supervisor")
    .addConditionalEdges(
        "supervisor",
        edge_async(state -> {
            SupervisorState supervisorState = new SupervisorState(state.data());
            if (!supervisorState.isContinue()) {
                return "finalize";
            }
            return "continue";
        }),
        Map.of(
            "continue", "supervisor_tools",
            "finalize", END));
```

关键点：

```text
SupervisorNode 负责生成下一批任务。
SupervisorToolsNode 负责执行这一批任务。
执行完回到 SupervisorNode。
SupervisorNode 决定继续还是结束。
```

这和当前项目的 `Planner -> Executor -> Reviewer -> maybe retry/replan` 很像。

但当前项目不建议直接照它做子图，第一版更稳的是：

```text
plan
validate_plan
schedule
execute_batch
evaluate_tasks
global_review
```

如果后续 Team 复杂度上升，再把 `execute_batch` 或 `review/replan` 抽成子图。

### 2.6 ResearcherSubgraph

`ResearcherSubgraph` 的结构：

```java
new StateGraph<>(ResearcherState.SCHEMA, new ResearcherStateSerializer())
    .addNode("researcher", node_async(researcherNode))
    .addNode("compress", node_async(compressNode))
    .addEdge(START, "researcher")
    .addEdge("researcher", "compress")
    .addEdge("compress", END);
```

它体现了一个很实用的模式：

```text
任务执行节点
  -> 结果压缩节点
  -> 返回给上层
```

对当前项目的启发：

```text
Executor 执行子任务后，不要把完整工具返回、模型中间输出全部堆给 Reviewer。
应该有结果规整/压缩步骤，形成 ExecutionResult。
```

也就是：

```text
rawObservation
  -> summarizedObservation
  -> evidenceRefs
  -> answerFragment
```

### 2.7 State 设计

`MainGraphState` 包含：

```text
supervisor_messages
research_brief
raw_notes
notes
final_report
request_id
user_id
research_topic
current_node_start_time
error_message
success
```

`SupervisorState` 包含：

```text
research_topic
research_brief
supervisor_messages
notes
raw_notes
task_list
is_continue
research_iterations
supervision_start_time
last_iteration_time
error_message
success
```

`ResearcherState` 包含：

```text
researcher_messages
tool_call_iterations
research_task
compressed_research
raw_notes
request_id
user_id
success
error_message
```

对当前项目的启发：

```text
TeamGraphState 需要区分 raw 数据和压缩后的可审查数据。
```

建议当前项目的 execution result 保留：

```text
rawToolResultRef
observationSummary
answerFragment
evidence
error
usage
durationMs
```

不要把大段原始网页、工具响应、模型中间消息都塞进最终 answer 和 Reviewer prompt。

### 2.8 SupervisorNode

`SupervisorNode` 做的事情：

```text
1. 看 researchIterations 是否达到 maxIterations
2. 构造包含 topic、brief、已有研究过程的 prompt
3. 调模型生成下一批 tasks
4. 解析 JSON 数组
5. 如果任务为空，结束
6. 如果任务非空，写 task_list 并继续
```

它有一个硬限制：

```java
private final int maxIterations = 3;
```

这对当前项目非常关键：

```text
Team 模式必须有整体上限。
```

当前项目应至少保留：

```text
maxTasks
maxRetries
maxToolCalls
maxModelCalls
timeoutMs
maxIterations
```

### 2.9 SupervisorToolsNode

`SupervisorToolsNode` 是最值得看的节点。

它读取 `task_list`，按批次并发执行 Researcher 子图：

```text
MaxConcurrentResearch = 3
task_list
  -> 每批最多 3 个
  -> CompletableFuture 并发执行
  -> 收集 successfulTasks / failedTasks / compressedReports / rawNotes
```

核心模式：

```java
List<CompletableFuture<ResearcherState>> batchFutures = currentBatchTasks.stream()
    .map(this::executeResearchTaskAsync)
    .collect(Collectors.toList());
```

执行完成后返回：

```text
supervisor_messages
notes
raw_notes
task_list = empty
research_iterations + 1
```

对当前项目最重要的启发：

```text
动态任务不要都变成 LangGraph4j 动态节点。
更稳的是：Graph 里只有 execute_batch 一个节点，
execute_batch 内部根据 Planner 输出的 tasks 做受控并发。
```

这正好符合你已有设计：

```text
LangGraph4j 管宏观流程。
动态任务并发在 ExecuteBatchNode 内部处理。
```

### 2.10 ResearcherNode

`ResearcherNode` 使用 LangChain4j `AiServices`，绑定三个工具：

```text
TavilySearchTool
ResearchCompleteTool
ThinkTool
```

并设置：

```java
.maxSequentialToolsInvocations(10)
```

对当前项目的启发：

```text
Executor 必须限制单个子任务内最大工具调用次数。
```

当前项目可对应：

```text
maxToolCallsPerTask
maxSequentialToolCalls
maxTaskDurationMs
```

### 2.11 CompressNode

`CompressNode` 读取 researcher messages，然后调模型压缩成 `compressed_research`，并保留 `raw_notes`。

这对当前项目很有价值：

```text
复杂任务中的每个 Executor 输出应经过规整。
Reviewer 不应直接读取大量原始工具返回。
```

当前项目可以在 `ExecuteBatchNode` 内部或之后增加：

```text
TaskResultNormalizer
```

第一版不一定单独做 Graph Node，但概念要保留。

### 2.12 FinalReportGenerationNode

它把所有 `notes` 汇总为最终报告：

```text
notes -> findings
researchBrief + findings + currentDate -> finalReport
```

对应当前项目：

```text
executionResults + reviewResult + originalQuestion -> final_answer
```

注意：当前项目的最终回答不应该由 Reviewer 直接生成，而应该由 Orchestrator / FinalAnswerNode 生成。

这和你已有阶段 4 约束一致。

### 2.13 deep-researcher 的风险点

这个项目有不少 demo/样例风险，不能照搬：

| 风险点 | 表现 | 当前项目建议 |
| --- | --- | --- |
| 节点里 `newCachedThreadPool` | 并发不可控，缺少租户/应用级隔离 | 使用 Spring 管理的受控 Executor |
| 子图执行时反复 `compile` | 每批任务都可能重复编译 graph | GraphFactory 启动时预编译或缓存 |
| `MemorySaver` | 内存 checkpoint，不适合多实例 | 第一版不用 checkpoint 或后续接 Postgres/Redis |
| 任务是 `List<String>` | 缺少任务 id、类型、依赖、权限、预算 | 使用结构化 TaskPlan |
| JSON 解析失败直接空任务 | 可能错误地提前结束 | Planner 输出必须 JSON Schema 校验，失败可修正/降级 |
| fixed maxIterations = 3 | 配置不灵活 | 放入 TeamPolicy |
| 子任务失败只记字符串 | 不利于 Trace 和重试 | 使用结构化 ExecutionResult |

## 3. research4j

仓库：

```text
https://github.com/bhavuklabs/research4j
```

### 3.1 项目定位

`research4j` 更像一个通用研究库，不是典型 Spring Boot Agent 平台。它的价值主要在工程迁移和 pipeline 抽象。

README 中的核心流程：

```text
User Query
  -> Query Analysis
  -> Citation Fetch
  -> Reasoning Selection
  -> Reasoning Execution
  -> Response Generation
  -> ResearchResult
```

### 3.2 GraphExecutor 抽象

它定义了：

```java
public interface GraphExecutor {
    CompletableFuture<ResearchAgentState> processQuery(
        String sessionId,
        String query,
        UserProfile userProfile,
        ResearchPromptConfig config);

    boolean isHealthy();
    void shutdown();
    String getExecutorType();
}
```

然后通过工厂选择执行器：

```java
GraphExecutorFactory.create(...)
```

支持两类：

```text
LEGACY_CUSTOM
LANGGRAPH4J
```

如果 LangGraph4j executor 创建失败，自动 fallback 到 legacy executor：

```java
catch (Exception e) {
    return createLegacyExecutor(...);
}
```

这对当前项目非常实用。

当前项目可以采用类似策略：

```text
TeamRuntimeService
  -> DefaultTeamRuntimeService
  -> LangGraphTeamRuntimeService

TeamRuntimeFactory / TeamRuntimeRouter
  -> 根据配置选择实现
  -> LangGraph 初始化失败时回退旧实现或禁用 Team
```

但需要注意：生产环境是否允许自动回退，要看场景。对考核 Demo 来说，回退有利于稳定。

### 3.3 LangGraphExecutor

`LangGraphExecutor` 构造时创建节点并编译 graph：

```text
query_analysis
citation_fetch
reasoning_selection
reasoning_execution
```

流程：

```text
START
  -> query_analysis
  -> 条件判断是否 fetch citations
  -> citation_fetch 或 reasoning_selection
  -> reasoning_selection
  -> reasoning_execution
  -> END
```

关键点：

```java
private final CompiledGraph<LangGraphState> compiledGraph;
```

这比 `deep-researcher` 每次执行时重复 compile 更稳。

当前项目建议：

```text
TeamGraphFactory 负责构建并缓存 CompiledGraph。
LangGraphTeamRuntimeService 每次请求只 invoke/stream。
```

### 3.4 LangGraphState

它的 state 包含：

```text
session_id
query
user_profile
query_analysis
citations
selected_reasoning
research_context
response
error
metadata
```

它还提供：

```text
fromLegacyState
toLegacyState
```

这体现一个迁移思路：

```text
LangGraph state 可以作为旧状态模型的适配层。
```

当前项目也可以这样设计：

```text
TeamGraphState
  -> from AgentRunCommand / TeamRunCommand
  -> to TeamRunResult
```

不要让 LangGraph4j 的状态结构外泄到 Web/Gateway/API 层。

### 3.5 NodeAdapter

`NodeAdapter` 将旧 `GraphNode<ResearchAgentState>` 包装成 LangGraph4j `NodeAction<LangGraphState>`。

模式：

```java
ResearchAgentState legacyState = state.toLegacyState();
ResearchAgentState result = legacyNode.process(legacyState).join();
return convertLegacyStateToUpdates(legacyState, result);
```

这对当前项目非常有用：

```text
如果已有 TeamPlannerService / TeamExecutor / TeamReviewer，
可以用 NodeAdapter 包一层，而不是重写全部逻辑。
```

建议当前项目的节点也走这个方向：

```text
PlanNode -> plannerService.plan(...)
ExecuteBatchNode -> teamTaskExecutor.execute(...)
ReviewNode -> reviewerService.review(...)
```

### 3.6 DynamicRouter

`research4j` 的旧执行器中有一个 `DynamicRouter`，包含：

```text
MAX_RETRIES = 3
MAX_TOTAL_ITERATIONS = 15
retry_count
iteration_count
visited_nodes
information_quality
```

它会根据：

```text
query complexity
citation count
source relevance
source diversity
content richness
response quality
```

决定是否：

```text
继续 citation_fetch
重新 reasoning_selection
结束
```

当前项目不需要照搬这些研究质量指标，但应该借鉴它的运行护栏：

```text
总迭代次数上限
每节点重试次数
访问过的节点记录
信息质量/执行质量评估
是否允许继续
```

这可以对应你当前设计中的：

```text
EvaluateTasksNode
GlobalReviewNode
TeamPolicy
```

### 3.7 research4j 的风险点

| 风险点 | 表现 | 当前项目建议 |
| --- | --- | --- |
| README 说 Java 21，当前项目 Java 17 | 虚拟线程相关实现不适配 | 不引入 Java 21 依赖 |
| 不是平台级多租户架构 | 缺少 tenant/profile/tool 权限边界 | 只借鉴执行器抽象 |
| 部分源码质量不稳定 | 读取时发现文件内容/类名疑似重复或不一致 | 不照搬实现 |
| LangGraph4j 用法较浅 | 主要是线性 pipeline | 参考工程迁移，不参考复杂编排 |

## 4. 对当前项目的新增建议

### 4.1 第一版不要做真正 multi-graph

`deep-researcher` 的 multi-graph 很有启发，但当前项目第一版不建议直接做：

```text
MainGraph + PlannerSubgraph + ExecutorSubgraph + ReviewerSubgraph
```

原因：

```text
1. 当前阶段 4 要先跑通最小 Team Demo
2. 子图会增加状态映射复杂度
3. SSE/Trace/Token 跨子图聚合更麻烦
4. 你已有设计已经足够表达 Planner -> Executor -> Reviewer
```

第一版建议：

```text
单个 TeamGraph
  -> execute_batch node 内部处理动态并发任务
```

后续如果任务复杂度上升，再把 `execute_batch` 抽成 ExecutorSubgraph。

### 4.2 execute_batch 应做受控并发

借鉴 `SupervisorToolsNode`，但要改成平台级受控实现：

```text
读取 scheduledTasks
按 maxConcurrentTasks 分批
每个任务执行前检查预算
每个任务推 team_task_start
工具调用推 team_tool_call / team_tool_result
结束推 team_task_result
聚合 ExecutionResult
失败任务写结构化 error
```

不要使用：

```java
Executors.newCachedThreadPool()
```

建议使用：

```text
Spring ThreadPoolTaskExecutor
或 Java ExecutorService Bean
按应用/租户加并发限制
任务级 timeout
CompletableFuture.orTimeout
```

### 4.3 增加 TaskResultNormalizer 概念

借鉴 `CompressNode`：

```text
原始工具输出 / 模型输出
  -> 压缩/规整
  -> 可审查 ExecutionResult
```

当前项目可以先不单独建 graph node，但 Executor 输出 DTO 应设计成：

```text
taskId
taskType
status
rawObservationRef
observationSummary
answerFragment
evidenceRefs
toolCalls
usage
error
durationMs
```

这样 Reviewer 和 FinalAnswerNode 就不会被超长原始结果拖垮。

### 4.4 引入 TeamRuntime 选择策略

借鉴 `research4j` 的 `GraphExecutorFactory`：

```text
TeamRuntimeService
  -> DefaultTeamRuntimeService
  -> LangGraphTeamRuntimeService
```

选择逻辑：

```text
profile.teamEngine = LEGACY / LANGGRAPH4J
或 application/team config 控制
```

第一版也可以更简单：

```text
TEAM 模式默认走 LangGraphTeamRuntimeService
保留旧 DefaultTeamRuntimeService 作为 fallback
```

### 4.5 Graph 预编译

借鉴 `research4j`，避免每次请求重复 compile：

```text
TeamGraphFactory 初始化 CompiledGraph
LangGraphTeamRuntimeService 每次请求 stream/invoke
```

但要注意：

```text
CompiledGraph 可以复用
TeamGraphState 每次请求必须独立
RunnableConfig.threadId 使用 runId / traceId / conversationId
```

### 4.6 明确 TeamPolicy

综合 `deep-researcher` 和 `research4j`，当前项目应有一个 TeamPolicy：

```text
maxTasks
maxIterations
maxRetries
maxToolCalls
maxToolCallsPerTask
maxModelCalls
maxConcurrentTasks
timeoutMs
taskTimeoutMs
allowReplan
allowPartialAnswer
```

这些值可以第一版写在配置里，不一定马上做数据库表。

### 4.7 错误处理策略

建议第一版 Team 错误处理：

```text
Planner JSON 失败
  -> 要求模型修正一次
  -> 仍失败则降级单 MODEL_TASK

单个 Executor 任务失败
  -> 写 ExecutionResult(status=FAILED)
  -> 不直接中断整个 Team，除非关键任务失败

工具调用失败
  -> 记录 tool error
  -> 允许任务级重试一次

Reviewer 不通过
  -> retryTasks 非空则只重试指定任务
  -> replanRequired=true 且无 retryTasks 则重新规划一次
  -> 仍失败则返回当前最优答案和风险说明
```

这比 `deep-researcher` 的字符串错误、`research4j` 的通用 retry 更贴合当前项目。

## 5. 更新后的推荐落地路线

结合四个项目阅读后，当前项目推荐路线如下：

```text
1. 单 Agent 不动
2. 保留 DefaultTeamRuntimeService 作为旧实现或 fallback
3. 新增 LangGraphTeamRuntimeService
4. 新增 TeamGraphFactory，预编译 CompiledGraph
5. 新增 TeamGraphState，只保存可序列化快照
6. Graph 第一版保持单图，不做 multi-graph
7. execute_batch node 内部做受控并发
8. Executor 输出结构化 ExecutionResult，并做结果规整
9. Graph node 边界推 SSE 和 Trace span
10. 后续再考虑 ExecutorSubgraph / ReviewerSubgraph
```

最终推荐 graph：

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

如果未来升级到 multi-graph，可以演进为：

```text
MainTeamGraph
  -> BuildContext
  -> PlannerSubgraph
  -> ExecutorSubgraph
  -> ReviewerSubgraph
  -> FinalAnswer
```

但这不是第一版目标。

## 6. 当前最重要的下一步

接下来应该回到本项目源码，定位这些点：

```text
DefaultTeamRuntimeService 是否已经存在
TeamRuntimeService 接口在哪里
AgentRunCommand / AgentRunResult 结构
SSE 事件发送工具在哪里
Trace span 写入服务在哪里
MCP/Skill 工具执行接口在哪里
Token usage 如何记录
Memory/RAG context 如何构建
```

只有确认这些现有边界后，才能写真正可落地的 `LangGraphTeamRuntimeService` 类设计。
