# 自研 Agent Graph 编排引擎设计方案

## 1. 背景与目标

### 1.1 现状

当前 Team 模式的 Planner→Executor→Reviewer 流程采用硬编码 if/else 控制流（`DefaultTeamRuntimeService.run()`），存在以下问题：

- 控制流逻辑编译时固定，加新角色/新跳转路径必须改动主流程代码
- 状态通过局部变量手动传递（`plan`、`executionResults`、`reviewResult`），新增字段需改接口
- 不支持暂停/恢复（长时间任务不可中断）
- 不支持人机交互节点（如高危工具确认等待用户审批）
- 简历描述缺乏技术亮点

### 1.2 目标

吸收 **LangGraph**（LangChain 团队的有状态 Agent 编排框架）的核心设计思想，用纯 Java 自研一个轻量级 Agent Graph 编排引擎：

- **声明式图配置**：节点 + 条件边 = Agent 工作流，运行时动态解释
- **统一状态管理**：框架自动传递 State，节点函数无状态
- **Checkpoint 暂停/恢复**：长时间任务可中断，从断点恢复
- **全链路可观测**：每个节点的输入/输出/耗时/token 自动记录
- **零外部编排依赖**：不引入 LangGraph、LangChain4j、Flowable、Activiti 等框架

### 1.3 与引入 LangGraph 的对比

| 维度 | 直接引入 LangGraph | 自研 Agent Graph |
|------|-------------------|-----------------|
| 语言生态 | Python 原生，Java 版不成熟 | 纯 Java 17+，Spring Boot 原生集成 |
| 简历含金量 | "用过 LangGraph" | "自研了类 LangGraph 的 Agent 编排引擎" |
| 面试可聊深度 | 调 API 经验 | 状态机设计、图遍历算法、并发模型、容错恢复 |
| 依赖风险 | Python/Java 混合部署 | 零外部编排依赖 |
| 灵活性 | 受框架限制 | 完全自定义，随业务扩展 |

---

## 2. 核心设计

### 2.1 架构概览

```text
 ┌─────────────────────────────────────────────────────────┐
  │                   AgentGraph 编排引擎                     │
  │                                                          │
  │  ┌──────────┐    ┌──────────┐    ┌───────────────┐     │
  │  │ GraphDef │───→│ Compiler │───→│ ValidatedGraph │     │
  │  │ (声明式)  │    │ (校验拓扑) │    │ (可执行图)      │     │
  │  └──────────┘    └──────────┘    └───────┬───────┘     │
  │                                          │              │
  │                                          ↓              │
  │  ┌──────────────────────────────────────────────────┐   │
  │  │              GraphExecutor（执行引擎）              │   │
  │  │                                                   │   │
  │  │  ┌────────┐   ┌────────┐   ┌────────┐            │   │
  │  │  │Planner │──→│Executor│──→│Reviewer│            │   │
  │  │  └────────┘   └────────┘   └───┬────┘            │   │
  │  │         ↑                      │                  │   │
  │  │         └──── retry ───────────┘ (条件边)         │   │
  │  │         └──── replan ──────────┘ (条件边)         │   │
  │  │                                                   │   │
  │  │  ┌──────────────────────────────────────┐        │   │
  │  │  │  AgentGraphState（贯穿所有节点）        │        │   │
  │  │  │  - variables: Map<String, Object>     │        │   │
  │  │  │  - eventLog: List<GraphEvent>         │        │   │
  │  │  │  - currentNodeId: String              │        │   │
  │  │  │  - checkpoint(): byte[]               │        │   │
  │  │  └──────────────────────────────────────┘        │   │
  │  └──────────────────────────────────────────────────┘   │
  │                                                          │
  │  ┌────────────┐  ┌──────────────┐  ┌────────────────┐  │
  │  │  Limiter   │  │  EventSink    │  │ CheckpointStore│  │
  │  │ 步数/超时   │  │ SSE实时推送   │  │ 暂停/恢复存储   │  │
  │  │ token预算   │  │ 节点事件      │  │ PostgreSQL实现  │  │
  │  └────────────┘  └──────────────┘  └────────────────┘  │
  └─────────────────────────────────────────────────────────┘
```

### 2.2 核心接口

#### AgentGraphState —— 共享状态

```java
public class AgentGraphState {
    /** 任意键值对，所有节点读写同一个 State */
    private final Map<String, Object> variables;

    /** 完整事件日志，每个节点的输入/输出自动记录 */
    private final List<AgentGraphEvent> eventLog;

    /** 当前正在执行的节点 ID */
    private String currentNodeId;

    /** 全局执行步数 */
    private int stepCount;

    /** 开始时间 */
    private final Instant startedAt;

    /** 最后一次 checkpoint 时间 */
    private Instant lastCheckpointAt;

    // ---- 方法 ----

    /** 读变量 */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) { ... }

    /** 写变量 */
    public void set(String key, Object value) { ... }

    /** 生成 checkpoint（序列化为字节数组，用于暂停恢复） */
    public byte[] checkpoint() { ... }

    /** 从 checkpoint 恢复 */
    public static AgentGraphState restore(byte[] data) { ... }

    /** 记录事件 */
    public void log(String nodeId, NodeEventType type, String summary, Object input, Object output) { ... }
}
```

#### AgentNode —— 节点

```java
/**
 * 图中的一个节点。每个节点是一个无副作用的函数：
 * 输入 = AgentGraphState，输出 = NodeResult
 *
 * 节点不持有状态，不直接调用下一个节点——跳转逻辑由条件边负责。
 */
@FunctionalInterface
public interface AgentNode {

    /**
     * 执行节点逻辑。
     *
     * @param state  共享状态，包含所有上游节点的输出
     * @param context 只读上下文（traceId, tenantId, modelConfigId 等不变信息）
     * @return NodeResult 决定下一步去哪里
     */
    NodeResult execute(AgentGraphState state, AgentGraphContext context) throws Exception;
}
```

#### NodeResult —— 节点输出

```java
/**
 * 节点执行结果，包含路由信息。
 */
public record NodeResult(
    /** 路由到下一个节点的 ID，null 表示结束 */
    @Nullable String nextNodeId,

    /** 是否等待人工审批（高危工具确认场景） */
    boolean waitForHuman,

    /** 等待审批时的提示信息 */
    @Nullable String humanPrompt,

    /** 本节点的结构化输出 key，用于写入 State（如 "plan", "executionResults", "review"） */
    @Nullable String outputKey,

    /** 本节点的结构化输出值 */
    @Nullable Object outputValue
) {
    /** 固定跳转到下一个节点 */
    public static NodeResult next(String nodeId) { ... }

    /** 当前节点结束，等待条件边决定下一步 */
    public static NodeResult conditional() { ... }

    /** 图结束 */
    public static NodeResult end() { ... }

    /** 等待人工审批 */
    public static NodeResult awaitHuman(String prompt) { ... }
}
```

#### ConditionalEdge —— 条件边

```java
/**
 * 运行时决定下一个节点。接收当前 State，返回下一个 nodeId。
 * 用于实现 "Review 不通过→重试"、"Planner 失败→降级" 等动态路由。
 */
@FunctionalInterface
public interface ConditionalEdge {

    /**
     * @param state 当前共享状态
     * @return 下一个节点的 ID，或 null / END 表示图结束
     */
    String route(AgentGraphState state);
}
```

#### AgentGraphDefinition —— 图定义

```java
/**
 * 声明式图配置。使用 Builder 模式链式构建。
 */
public class AgentGraphDefinition {

    /** 节点集合：nodeId → AgentNode */
    private final Map<String, AgentNode> nodes;

    /** 条件边：源 nodeId → 路由函数 */
    private final Map<String, ConditionalEdge> conditionalEdges;

    /** 固定边：源 nodeId → 目标 nodeId（无条件跳转） */
    private final Map<String, String> fixedEdges;

    /** 入口节点 */
    private final String entryNodeId;

    /** 图名称 */
    private final String name;

    // Builder 模式
    public static Builder builder() { ... }

    public static class Builder {
        public Builder node(String id, AgentNode node) { ... }
        public Builder conditional(String fromNodeId, ConditionalEdge edge) { ... }
        public Builder edge(String from, String to) { ... }
        public Builder entry(String nodeId) { ... }
        public Builder name(String name) { ... }
        public AgentGraphDefinition build() { ... }
    }
}
```

#### AgentGraphLimiter —— 执行约束

```java
/**
 * 图级别的运行限制，防止 Agent 图无限循环或过度消耗资源。
 * 参考 TeamRunLimiter 的设计，提升为图编排级别的通用能力。
 */
public class AgentGraphLimiter {
    private final int maxSteps;           // 最大总步数（所有节点累加）
    private final int maxNodeVisits;      // 单节点最大被访问次数（防死循环）
    private final int maxModelCalls;      // 最大模型调用次数
    private final int maxToolCalls;       // 最大工具调用次数
    private final long timeoutMs;         // 整体超时
    private final Clock clock;

    public void checkStep(AgentGraphState state) { ... }
    public void consumeModelCall() { ... }
    public void consumeToolCall() { ... }
    public void checkTimeout() { ... }
}
```

#### AgentGraphExecutor —— 执行引擎

```java
/**
 * 图执行引擎。接收编译后的图定义和初始状态，循环执行节点直到 END 或超限。
 */
public class AgentGraphExecutor {

    private final AgentGraphLimiter limiter;
    private final AgentGraphEventSink eventSink;
    private final CheckpointStore checkpointStore;
    private final TraceService traceService;
    private final TokenUsageService tokenUsageService;

    /**
     * 执行图。
     *
     * @param graph      已编译的图定义
     * @param state      初始状态（可从 checkpoint 恢复）
     * @param context    只读上下文
     * @return 执行结果，包含最终 State 和汇总指标
     */
    public AgentGraphResult execute(
            AgentGraphDefinition graph,
            AgentGraphState state,
            AgentGraphContext context
    ) {
        String currentNode = graph.entryNodeId();
        state.setCurrentNodeId(currentNode);

        while (currentNode != null && !"END".equals(currentNode)) {
            limiter.checkStep(state);
            limiter.checkTimeout();

            // 1. 取节点
            AgentNode node = graph.nodes().get(currentNode);
            if (node == null) {
                throw new AgentGraphException("Unknown node: " + currentNode);
            }

            // 2. 开始节点 Span
            TraceSpan span = traceService.startSpan(currentNode, context.traceId());

            // 3. 执行节点 + 自动记录事件
            long startMs = System.currentTimeMillis();
            state.log(currentNode, NodeEventType.START, "开始执行", null, null);
            eventSink.emit(new GraphNodeStartEvent(currentNode, state.stepCount()));

            NodeResult result;
            try {
                result = node.execute(state, context);
            } catch (Exception ex) {
                state.log(currentNode, NodeEventType.ERROR, ex.getMessage(), null, null);
                eventSink.emit(new GraphNodeErrorEvent(currentNode, ex.getMessage()));
                traceService.finishSpan(span, "FAILED", ex);
                throw new AgentGraphException("Node " + currentNode + " failed", ex);
            }

            long elapsedMs = System.currentTimeMillis() - startMs;
            state.log(currentNode, NodeEventType.COMPLETE, "执行完成", null, result);

            // 4. 写节点输出到 State
            if (result.outputKey() != null) {
                state.set(result.outputKey(), result.outputValue());
            }

            // 5. 完成节点 Span
            traceService.finishSpan(span, "SUCCESS", null, null, Map.of("elapsedMs", elapsedMs));

            // 6. Checkpoint（每节点完成后自动存档）
            if (checkpointStore != null) {
                checkpointStore.save(context.runId(), state.checkpoint());
            }

            // 7. 路由到下一个节点
            if (NodeResult.END_NODE.equals(result.nextNodeId())) {
                currentNode = null;
            } else if (result.waitForHuman()) {
                // 暂停等待人工审批，保存 checkpoint 后退出
                checkpointStore.save(context.runId(), state.checkpoint());
                eventSink.emit(new GraphAwaitHumanEvent(currentNode, result.humanPrompt()));
                return AgentGraphResult.awaitingHuman(state, result.humanPrompt());
            } else if (result.nextNodeId() != null) {
                // 固定跳转：直接到指定节点
                currentNode = result.nextNodeId();
            } else {
                // 条件跳转：调用 conditional edge 决定
                ConditionalEdge edge = graph.conditionalEdges().get(currentNode);
                if (edge == null) {
                    throw new AgentGraphException("No outgoing edge from node: " + currentNode);
                }
                currentNode = edge.route(state);
            }

            // 8. 限制检查：单节点最大访问次数
            limiter.checkNodeVisit(currentNode, state);
        }

        eventSink.emit(new GraphCompleteEvent(state));
        return AgentGraphResult.completed(state);
    }
}
```

### 2.3 使用示例 —— Team 模式迁移

**迁移前（当前代码，硬编码 if/else）**：
```java
// DefaultTeamRuntimeService.run()
planResult = planner.plan(...);
batch = executeTasks(...);
reviewResult = review(...);
if (needsRetry) { executeTask(...); reviewResult = review(...); }
else if (needsReplan) { planResult = planner.plan(...); executeNewTasks(...); reviewResult = review(...); }
finalAnswer = finalAnswerBuilder.build(...);
```

**迁移后（AgentGraph 声明式配置）**：
```java
AgentGraphDefinition teamGraph = AgentGraphDefinition.builder()
    .name("team-workflow")
    .entry("planner")

    .node("planner", (state, ctx) -> {
        PlanTeamCommand cmd = buildPlanCommand(state, ctx);
        TeamPlanResultDTO result = planner.plan(cmd);
        return NodeResult.next("executor")
            .withOutput("plan", result.plan())
            .withOutput("planModelInvocations", result.modelInvocations());
    })

    .node("executor", (state, ctx) -> {
        TaskPlanDTO plan = state.get("plan");
        List<ExecutionResultDTO> results = executor.executeAll(plan, state.get("tools"));
        return NodeResult.next("reviewer")
            .withOutput("executionResults", results);
    })

    .node("reviewer", (state, ctx) -> {
        ReviewResultDTO review = reviewer.review(
            state.get("plan"),
            state.get("executionResults"),
            state.get("answerDraft")
        );
        return NodeResult.conditional()  // 等待条件边决定下一步
            .withOutput("review", review);
    })

    .conditional("reviewer", state -> {
        ReviewResultDTO r = state.get("review");
        if (r.passed()) return "buildFinalAnswer";
        if (!r.retryTasks().isEmpty()) return "retryExecutor";
        if (Boolean.TRUE.equals(r.replanRequired())) return "planner";
        return "buildFinalAnswer";
    })

    .node("retryExecutor", (state, ctx) -> {
        ReviewResultDTO review = state.get("review");
        ExecutionResultDTO retried = executor.executeTask(review.retryTasks().get(0));
        List<ExecutionResultDTO> results = state.get("executionResults");
        replaceResult(results, retried);
        return NodeResult.next("reviewer");
    })

    .node("buildFinalAnswer", (state, ctx) -> {
        String answer = finalAnswerBuilder.build(
            state.get("answerDraft"),
            state.get("review")
        );
        return NodeResult.end().withOutput("finalAnswer", answer);
    })

    .build();

// 执行
AgentGraphState initialState = AgentGraphState.create()
    .set("command", command)
    .set("context", context)
    .set("tools", tools);

AgentGraphResult result = graphExecutor.execute(teamGraph, initialState, graphContext);
String finalAnswer = result.state().get("finalAnswer");
```

### 2.4 图编译与校验

```java
public class AgentGraphCompiler {

    /**
     * 编译图定义，校验拓扑合法性。
     *
     * @throws AgentGraphException 如果：
     *   - 入口节点不存在
     *   - 有节点不可达（从入口出发的 DFS 无法到达）
     *   - 条件边引用了不存在的目标节点
     *   - 存在孤立节点（没有任何入边或出边，且不是入口节点）
     *   - 存在循环但没有退出条件（死循环风险）
     */
    public ValidatedGraph compile(AgentGraphDefinition definition) {
        // 1. 校验入口节点
        // 2. 校验所有 edge 引用的节点存在于 nodes 集合中
        // 3. DFS 从入口遍历，标记可达节点
        // 4. 警告不可达节点
        // 5. 检测死循环风险（conditional 没有通往 END 的分支）
        // 6. 返回 ValidatedGraph（不可变、已验证的图实例）
    }
}
```

---

## 3. 高级特性

### 3.1 Checkpoint 暂停/恢复

```java
/**
 * Checkpoint 存储接口。默认实现：PostgreSQL jsonb 列。
 * 未来可扩展 Redis / 文件系统。
 */
public interface CheckpointStore {
    void save(String runId, byte[] checkpoint);
    byte[] load(String runId);
    void delete(String runId);
    List<String> listActive();  // 列出所有未完成的 run
}

/**
 * 使用场景：
 * 1. 长时间任务自动 checkpoint（每 N 个节点后）
 * 2. 高危工具审批：节点返回 waitForHuman → 用户在前端点"确认" → 后端 resume(runId, approved=true)
 * 3. 服务重启恢复：启动时从 CheckpointStore 加载所有 active run，继续执行
 */
public AgentGraphResult resume(String runId, AgentGraphDefinition graph, HumanDecision decision) {
    byte[] checkpoint = checkpointStore.load(runId);
    AgentGraphState state = AgentGraphState.restore(checkpoint);
    state.set("humanDecision", decision);
    return execute(graph, state, buildContext(runId));
}
```

### 3.2 人机交互节点

```java
// 高危工具确认节点
.node("confirmDangerousTool", (state, ctx) -> {
    ToolCall call = state.get("pendingToolCall");
    if (call.riskLevel() == RiskLevel.HIGH) {
        return NodeResult.awaitHuman(
            "工具 [" + call.name() + "] 标记为高危操作，参数：" + call.arguments()
        );
    }
    return NodeResult.next("executeTool");
})

// 前端收到 GraphAwaitHumanEvent → 显示确认对话框 → 用户点确认/拒绝
// → 前端 POST /api/graph/{runId}/resume { decision: "approved"|"denied" }
// → 后端 load checkpoint → 设置 humanDecision → 继续执行
```

### 3.3 并行节点（Fan-out / Fan-in）

```java
// 声明并行执行多个子任务
.node("fanOutSearch", (state, ctx) -> {
    List<String> queries = state.get("searchQueries");  // ["天气", "团建场地", "交通"]
    // Executor 并行执行，结果自动合并
    return NodeResult.fanOut("searchTask", queries).collectAt("mergeResults");
})

.node("searchTask", (state, ctx) -> {
    // 每个 query 一个实例并行执行
    String query = state.get("_currentFanOutItem");
    String result = searchTool.search(query);
    return NodeResult.next("_fanOutComplete").withOutput(null, result);
})

.node("mergeResults", (state, ctx) -> {
    List<String> allResults = state.get("_fanOutResults");
    // 合并所有并行结果
    return NodeResult.next("reviewer").withOutput("searchResults", allResults);
})
```

### 3.4 嵌套子图（SubGraph）

```java
// 子 Agent 作为图中的一个节点
.node("subAgentExplore", (state, ctx) -> {
    AgentGraphDefinition subGraph = AgentGraphDefinition.builder()
        .name("explore-sub-agent")
        .entry("search")
        .node("search", ...)
        .node("summarize", ...)
        .build();

    // 子图有独立的 State、Limiter、工具集
    AgentGraphResult subResult = graphExecutor.execute(
        subGraph,
        AgentGraphState.create().set("query", state.get("question")),
        ctx.withToolFilter(ToolFilter.exploreOnly())  // 只读工具
    );

    return NodeResult.next("mainReviewer")
        .withOutput("explorationResult", subResult.state().get("summary"));
})
```

---

## 4. 可观测性

### 4.1 事件体系

| 事件类型 | 触发时机 | 携带信息 |
|---------|---------|---------|
| `graph_start` | 图开始执行 | graphName, runId |
| `node_start` | 节点开始执行 | nodeId, stepCount, inputState |
| `node_complete` | 节点执行完成 | nodeId, elapsedMs, outputValue |
| `node_error` | 节点执行失败 | nodeId, errorMessage |
| `conditional_edge` | 条件边路由 | fromNode, toNode, routeReason |
| `graph_await_human` | 等待人工审批 | nodeId, prompt, runId |
| `graph_checkpoint` | Checkpoint 已保存 | runId, stepCount |
| `graph_fan_out` | 并行扇出开始 | nodeId, itemCount |
| `graph_fan_in` | 并行扇入完成 | nodeId, completedCount |
| `graph_complete` | 图执行完成 | totalSteps, totalElapsedMs, finalState |

### 4.2 前端可视化

Trace Detail 页面展示图执行路径：

```text
┌─────────────────────────────────────────────┐
│  Agent Graph: team-workflow                  │
│  Run ID: run_abc123   Status: COMPLETED      │
│  Total Steps: 5   Elapsed: 12.3s             │
│                                               │
│  ● planner ────→ ● executor ────→ ● reviewer  │
│                     ↑                │         │
│                     └── retry ───────┘         │
│                                     │         │
│                                     ↓         │
│                               ● finalAnswer   │
│                                               │
│  Nodes:                                        │
│  ├─ planner      2.1s  ✓  模型调用×1           │
│  ├─ executor     3.2s  ✓  工具调用×2           │
│  ├─ reviewer     1.5s  ✗  retry requested      │
│  ├─ executor     2.8s  ✓  模型调用×1           │
│  ├─ reviewer     1.1s  ✓  passed               │
│  └─ finalAnswer  0.3s  ✓                       │
└─────────────────────────────────────────────┘
```

---

## 5. 落地计划

### Step 1：AgentGraph 核心（预计 2-3 个提交）

**新增包**：`core.agent.graph`

- `AgentGraphState`：共享状态 + checkpoint 序列化
- `AgentNode`、`NodeResult`：节点函数接口
- `ConditionalEdge`：条件路由函数
- `AgentGraphDefinition` + Builder：链式图配置
- `AgentGraphCompiler`：拓扑校验
- `AgentGraphExecutor`：图执行引擎（循环 + 路由 + 事件）
- `AgentGraphLimiter`：步数/超时/模型调用/工具调用限制
- `AgentGraphResult`：执行结果封装
- `AgentGraphContext`：只读上下文
- `AgentGraphEventSink`：事件推送接口（已有 `TeamEventSink` 可复用）

**迁移验证**：将 `DefaultTeamRuntimeService.run()` 的内部逻辑迁移到图配置，功能不变，测试全量通过。

### Step 2：Checkpoint 暂停/恢复（预计 1-2 个提交）

- `CheckpointStore` 接口 + PostgreSQL 实现（`agent_graph_checkpoints` 表）
- 每节点完成后自动 checkpoint
- `GraphExecutor.resume(runId, decision)` 恢复执行
- `WaitForHuman` 节点类型：高危工具确认场景
- 前端确认对话框 + `/api/graph/{runId}/resume` 接口

### Step 3：可视化 + 调试（预计 1-2 个提交）

- Trace Detail 页面的图执行路径展示
- 每个节点的输入/输出、耗时、token 消耗
- 支持单步调试：“下一步”按钮手动推进节点

---

## 6. 与其他模式的对比

| 模式 | 当前状态 | 实现基础 | 适用场景 |
|------|---------|---------|---------|
| **AgentGraph 编排** | 📋 本文档方案 | 自研 `core.agent.graph` | 结构化多步工作流 |
| **Planner→Executor→Reviewer** | ✅ 已实现 | `DefaultTeamRuntimeService` | 固定三步 Team 模式 |
| **主 Agent→SubAgent** | 📋 规划中 | 参考 Claude Code `TaskTool` | 复杂任务分治 |
| **Blackboard 共享记忆** | 📋 规划中 | 现有 `MemoryRecallService` | 开放式多 Agent 协作 |

AgentGraph 编排引擎是**底层引擎**，Team 模式、SubAgent、Blackboard 可以都跑在它上面：

```text
┌──────────────────────────────────────────────┐
│               AgentGraph 编排引擎              │
│                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │Team 模式  │  │SubAgent  │  │  Blackboard  │ │
│  │P→E→R     │  │主→从     │  │  共享记忆     │ │
│  └──────────┘  └──────────┘  └──────────────┘ │
└──────────────────────────────────────────────┘
```
