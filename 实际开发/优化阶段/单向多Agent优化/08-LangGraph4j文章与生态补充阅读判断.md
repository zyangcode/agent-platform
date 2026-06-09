# LangGraph4j 文章与生态补充阅读判断

本文继续阅读 LangGraph4j 官方文档、how-to、文章和部分生态项目。目标不是马上改代码，而是判断哪些设计适合本项目 `DefaultTeamRuntimeService` 后续优化，哪些不应该引入。

## 1. 本轮阅读范围

官方资料：

- LangGraph4j Overview
  https://langgraph4j.github.io/langgraph4j/
- LangGraph4j Getting Started
  https://langgraph4j.github.io/langgraph4j/getting-started/
- References
  https://langgraph4j.github.io/langgraph4j/references/
- Multi-agent supervisor
  https://langgraph4j.github.io/langgraph4j/how-tos/multi-agent-supervisor/
- Agent Executor + MCP
  https://langgraph4j.github.io/langgraph4j/how-tos/agentexecutor-mcp/
- Hooks Samples
  https://langgraph4j.github.io/langgraph4j/how-tos/hooks/
- Spring AI Integrations
  https://langgraph4j.github.io/langgraph4j/integrations/spring-ai/
- Wait for User Input
  https://langgraph4j.github.io/langgraph4j/how-tos/wait-user-input/
- Checkpoint Postgres
  https://langgraph4j.github.io/langgraph4j/core/checkpoint-postgres/

文章：

- LangGraph4j - Multi-Agent handoff implementation with Spring AI
  https://bsorrentino.github.io/bsorrentino/ai/2025/05/10/Langgraph4j-agent-handoff.html
- LangGraph4j Deep Agents (Agent 2.0)
  https://bsorrentino.github.io/bsorrentino/ai/2025/10/17/langgraph4j-deepagents.html

生态项目：

- spring-ai-alibaba
  https://github.com/alibaba/spring-ai-alibaba
- ACP LangGraph / LangChain Bridge
  https://github.com/OsgiliathEnterprise/acp-langgraph-langchain-bridge
- AIDEEPIN
  https://github.com/moyangzhan/langchain4j-aideepin

## 2. 官方 Multi-agent Supervisor 的启发

官方 multi-agent supervisor 示例核心结构是：

```text
START
  -> supervisor
  -> conditional edge:
       coder
       researcher
       FINISH -> END
coder -> supervisor
researcher -> supervisor
```

它的关键点是：

```text
Supervisor 节点由模型判断 next
Worker 节点负责具体任务
Worker 完成后回到 Supervisor
Supervisor 决定继续派发还是结束
```

对本项目的判断：

```text
可借鉴：条件边、route 字段、循环回到决策节点。
不建议照搬：让 LLM Supervisor 直接决定任意下一个 Worker。
```

原因是本项目已经有明确的三角色边界：

```text
Planner -> Executor -> Reviewer
```

Reviewer 不应该变成一个自由派发的 Supervisor。更稳的是：

```text
Reviewer 只输出结构化 review:
  passed
  retryTasks
  replanRequired

Orchestrator / Graph route 节点根据 review 做有限状态转移:
  final
  retry
  replan
```

也就是说，本项目应该使用 LangGraph4j 的条件边，但不要把控制权完全交给一个开放式 Supervisor。

## 3. Agent Handoff 文章的启发

Agent handoff 文章的核心思想是：

```text
把另一个 agent 描述成一个 tool/function
模型通过 function calling 选择调用哪个 agent
被调用的 agent 处理自己的专业任务
```

文章里把 marketplace agent、payment agent 都包装成可调用 action，主 agent 通过工具调用完成 handoff。

对本项目的判断：

```text
短期不建议用于当前 Team Runtime 主链路。
中长期可用于“子 Agent 工具化”。
```

原因：

```text
当前 Team 模式已经有 Planner/Executor/Reviewer 三角色。
Executor 已经是唯一拿工具执行权限的角色。
如果现在把子 Agent 也包装成 tool，会和 Executor 的工具边界混在一起。
```

更合适的演进路径：

```text
第一阶段：
  Team graph 只编排 Planner/Executor/Reviewer。

第二阶段：
  Executor 内部可以执行一种新的任务类型 SUB_AGENT_TASK。

第三阶段：
  SUB_AGENT_TASK 再映射到专门的 profile / skill set / mcp tool set。
```

这样既吸收 handoff 思想，又不破坏现有边界。

## 4. Agent Executor + MCP 的启发

官方 Agent Executor + MCP 示例走的是：

```text
LangGraph4j AgentExecutor
  -> LangChain4j MCP client
  -> MCP tools
```

它适合展示 LangChain4j 生态下的 ReAct agent + MCP。

对本项目的判断：

```text
不建议直接引入 LangChain4j AgentExecutor。
不建议让 LangGraph4j 接管 MCP 工具调用。
```

原因：

```text
本项目已经有自研 MCP Client、MCP Server、MCP Tool DB 元数据、Profile 绑定关系。
前面已经讨论过同名工具、多租户、server 约束、refresh 等问题。
如果改用 LangChain4j MCP 工具链，会绕开当前的授权、租户、Trace、Token、SSE 体系。
```

更稳的方案：

```text
LangGraph4j 只做 Team 编排。
MCP 执行仍走现有 AgentToolResolver / AgentToolDispatcher / McpToolExecutor。
```

也就是说：

```text
Graph node 调 Executor。
Executor 调现有工具分发。
工具分发再调 MCP。
```

不要让 graph 直接调 MCP。

## 5. Hooks / OpenTelemetry 的启发

Hooks 示例展示了：

```text
addWrapCallNodeHook
addBeforeCallNodeHook
addAfterCallNodeHook
addWrapCallEdgeHook
```

hook 可以在 node 执行前后统一记录日志、状态更新、异常。

对本项目的判断：

```text
可以借鉴 hook 思路，但第一版不要依赖 hook 承担业务 Trace。
```

原因：

```text
当前 TraceService 已经有明确 span 名称：
  team.run
  context.build
  team.plan
  team.task.execute
  team.review
  team.fallback

这些 span 已经被项目看板和测试认知。
```

更合适的方式：

```text
第一版：
  仍在每个 node 内显式 startSpan / finishSpan。

第二版：
  再把通用 node span 包装为 TeamGraphTraceHook。
```

可借鉴的 hook 用法：

```text
统一记录 nodeId
统一记录 node 输入/输出摘要
统一捕获异常并打失败 span
```

但不能把所有业务事件都藏到 hook 里，否则读代码时看不到 Team SSE 是在哪里发出的。

## 6. Spring AI Integration 的启发

Spring AI Integration 文档重点展示：

```text
AgentExecutor 使用 Spring AI ChatModel
ToolContext 可以把 graph state 传给 tool
tool 也可以更新 graph state
```

对本项目的判断：

```text
不建议让 tool 直接更新 TeamGraphState。
```

原因：

```text
本项目工具执行结果必须经过 AgentToolDispatcher / ExecutionResultDTO。
工具结果还要经过敏感扫描、Trace、SSE、Token/告警治理。
如果允许 tool 直接写 graph state，会绕过 Executor 的结果归一化。
```

可以借鉴的是：

```text
工具调用时可传入只读 runtime context。
```

但落到本项目应是：

```text
Executor 构造 ExecuteTeamTaskCommand:
  task
  context
  availableTools
  previousResults

工具只返回 AgentToolDispatchResult。
Executor 负责变成 ExecutionResultDTO。
```

## 7. Wait for User Input / Human-in-the-loop 的启发

Wait for User Input 和 Spring AI Alibaba 都强调 Human-in-the-loop。

对本项目的判断：

```text
Team 第一版不做 graph 级暂停/恢复。
高危工具确认仍走现有 pending tool call 机制。
```

原因：

```text
当前项目已经有单 Agent 的 pendingToolCall / confirmedToolKeys 设计。
Team 模式如果马上做 graph checkpoint + resume，会引入状态持久化、前端恢复、审批事件三件套。
这会超过当前优化阶段的核心目标。
```

后续可以设计：

```text
Reviewer 发现需要人工确认
  -> route = human_review
  -> graph checkpoint
  -> SSE 推 team_waiting_user
  -> 用户确认后 resume
```

但这应作为 Team v2，不是 LangGraph4j 替换第一步。

## 8. Checkpoint / Postgres 的启发

LangGraph4j 支持 checkpoint，甚至有 Postgres how-to。

对本项目的判断：

```text
第一版不要启用 checkpoint saver。
```

原因：

```text
CLAUDE.md 里阶段 4 约束是不新增 Team 表。
当前 Team run 数据先写 trace_spans.attributes。
TeamGraphState 里会包含 AgentRunCommand、AgentContextDTO、AgentToolDTO、TaskPlanDTO 等对象。
这些对象未必适合直接持久化。
```

推荐边界：

```text
第一版：
  graph 只在一次请求内执行。
  Trace/SSE/Token 作为可观测记录。

第二版：
  如果要支持长任务/暂停恢复，再设计 TeamRunSnapshotDTO。
  SnapshotDTO 只存可序列化 JSON，不直接存 Spring Bean / runtime object。
```

## 9. Deep Agents 文章的启发

Deep Agents 文章提出四个支柱：

```text
Explicit Planning
Hierarchical Delegation
Persistent Memory
Extreme Context Engineering
```

对本项目的判断：

```text
最值得借鉴的是 Explicit Planning 和 Hierarchical Delegation。
```

本项目已经有：

```text
Planner 输出 TaskPlanDTO
Executor 执行任务
Reviewer 审查结果
```

所以本项目不是从普通 ReAct 升级为 Deep Agent，而是已经有了 Deep Agent 的一部分骨架。

下一步应该加强：

```text
TaskPlanDTO 的任务状态表达
ExecutionResultDTO 的证据字段
Reviewer 的 issue 分类
FinalAnswerBuilder 的风险说明
```

而不是马上引入文件系统式 artifact memory。

对 Persistent Memory 的判断：

```text
不建议把 Deep Agents 的文件型 memory 直接搬进本项目。
```

本项目已有：

```text
core.memory
core.rag
core.context slot
trace spans
```

所以 Team 中间产物应优先进入：

```text
trace_spans.attributes
必要时进入 memory/rag 的正式接口
```

不要绕过现有 memory/rag 边界。

## 10. spring-ai-alibaba 的启发

spring-ai-alibaba 的 README 把系统拆成：

```text
Agent Framework
Graph runtime
Admin / Studio
MCP management
Context Engineering
Human in the loop
```

它的 Graph 是 Agent Framework 的底层运行时，Agent Framework 再提供 SequentialAgent、ParallelAgent、RoutingAgent、LoopAgent 等高层模式。

对本项目的判断：

```text
这支持我们当前的分层方向：
  TeamRuntimeService 是高层业务入口
  LangGraph4j 只作为底层编排 runtime
```

不建议：

```text
直接引入 spring-ai-alibaba-agent-framework
直接复制它的 Admin / Studio / Graph runtime
```

原因：

```text
本项目已经有自己的 Web/Gateway/Core 分层、Trace/Token/MCP/Profile/Memory 体系。
引入另一个完整 Agent Framework 会造成框架重叠。
```

可借鉴：

```text
Context Engineering 作为显式模块
Workflow 可视化作为后续增强
Graph API 低层可控，高层业务保持自研
```

## 11. ACP Bridge 的启发

ACP bridge 项目清晰拆分：

```text
ACP transport
Agent support bridge
LangGraph4j adapter
PromptGraph
```

最有价值的是它的边界：

```text
协议层只处理 stdio JSON-RPC / streaming event
LangGraph4j adapter 只处理 graph stream
PromptGraph 只定义 graph
```

对本项目的判断：

```text
可借鉴 adapter 分层。
```

映射到本项目：

```text
Gateway/Web SSE
  -> 只做协议和事件传输

DefaultTeamRuntimeService
  -> 业务入口，创建 Team run

TeamGraphRuntimeAdapter
  -> 调用 CompiledGraph.invoke/stream

TeamGraphFactory
  -> 只定义 graph
```

但当前不需要 ACP：

```text
本项目入口是 Web/Gateway/OpenAI-compatible API。
ACP 是 IDE/CLI agent protocol，不是当前需求。
```

## 12. AIDEEPIN 的启发

AIDEEPIN 是平台型产品，能力包括：

```text
AI Chat
RAG
Workflow
MCP service marketplace
ASR/TTS
Short & Long-term Memory
Open API
```

它说明一个趋势：

```text
用户视角最终看到的是平台能力组合，而不是某个 graph 框架。
```

对本项目的判断：

```text
LangGraph4j 不应暴露成用户必须理解的概念。
```

前端/产品层应该继续表达为：

```text
Profile
Team Mode
Planner/Executor/Reviewer
Tools/MCP
Memory/RAG
Trace
```

LangGraph4j 只作为后端 Team Runtime 的内部实现。

## 13. 汇总设计判断

当前最稳的路线仍然是：

```text
DefaultTeamRuntimeService 名字保留。
TeamRuntimeService 接口保留。
Planner / Executor / Reviewer 保留。
LangGraph4j 只替换编排方式。
MCP / Skill / Trace / Token / SSE 仍走本项目已有链路。
```

不建议第一版做：

```text
不直接引入 LangChain4j AgentExecutor。
不直接引入 Spring AI Alibaba Agent Framework。
不让工具直接写 TeamGraphState。
不启用 checkpoint saver。
不做 graph 级 human-in-the-loop。
不做任务并发。
不把子 Agent 包成 tool 交给模型自由 handoff。
```

可以第一版做：

```text
StateGraph 编排固定 Team 状态机。
route_after_review 用条件边实现 retry / replan / final。
Graph node 内部复用现有 Planner / Executor / Reviewer。
TeamGraphFactory 预编译 CompiledGraph。
DefaultTeamRuntimeService 作为入口调用 graph。
保留现有 TeamRuntimeServiceTest 的事件顺序断言。
```

后续增强可以做：

```text
ExecuteBatchNode 内部支持按 dependsOn 分层并发。
TeamGraphTraceHook 统一记录 node span。
TeamRunSnapshotDTO 支持 checkpoint/resume。
SUB_AGENT_TASK 支持受控子 Agent。
Team graph 导出 PlantUML/Mermaid 给前端 Trace 可视化。
```

## 14. 对当前项目的最终建议

一句话：

```text
把 LangGraph4j 当成“Team 编排内核”，不要当成“Agent 平台框架”。
```

当前项目已经有平台框架：

```text
Profile
Context
Memory/RAG
Skill/MCP
Trace/Token/Gateway
SSE
Planner/Executor/Reviewer
```

LangGraph4j 应该补的是：

```text
显式状态机
条件边
循环控制
后续可视化/调试/并发扩展点
```

而不是替换这些已有平台能力。
