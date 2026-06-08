# 单 Agent 核心流程图阅读指南

文件：`runModelLoop-flowchart.drawio`

这张图按当前代码补全，不按未来设计补全。主要依据：

- `agent-platform-core/src/main/java/com/ls/agent/core/agent/application/DefaultAgentRuntimeService.java`
- `agent-platform-core/src/main/java/com/ls/agent/core/context/application/DefaultAgentContextBuilder.java`
- `agent-platform-core/src/main/java/com/ls/agent/core/agent/tool/DefaultAgentToolResolver.java`
- `agent-platform-core/src/main/java/com/ls/agent/core/agent/tool/DefaultAgentToolCallValidator.java`
- `agent-platform-core/src/main/java/com/ls/agent/core/agent/tool/DefaultToolExecutionPlanner.java`
- `agent-platform-core/src/main/java/com/ls/agent/core/agent/tool/DefaultAgentToolDispatcher.java`
- `agent-platform-core/src/main/java/com/ls/agent/core/model/application/DefaultModelInvokeService.java`

## 1. 先看 run() 外层主路线

主路线是上方粗蓝箭头：

```text
run(command)
  -> validate
  -> start agent_runtime.run span
  -> ensureConversation
  -> buildContext
  -> isTeamMode?
  -> save user message
  -> runModelLoop
  -> buildFinalAnswer
  -> save assistant message
  -> saveMemory
  -> return AgentRunResult
```

`TEAM` 分支只是旁路标注：当前代码会转给 `teamRuntimeService.run`，不是单 Agent 主路线。

## 2. 再看 runModelLoop()

中间区域按原始学习图的风格保持简版，只保留能决定流程走向的节点：

- 初始消息来自 `context.apiMessages()`。
- 可用工具来自 `agentToolResolver.resolve(context)`。
- 最多 6 轮模型调用：`MAX_AGENT_STEPS = 6`。
- 每轮先 `compactMessages()`，再 `invokeModel()`。
- 工具调用来自两种来源：模型返回的 `toolCalls`，或助手文本里的 `@source:tool JSON`。
- 如果模型没调工具，代码会尝试一次天气场景的 `repairMissingToolRequestBatch`。
- 仍然没有工具时，就返回模型答案。
- 工具无效、解析异常、工具次数达到 8 次、循环耗尽时，进入 `fallbackDirectAnswer()`。

## 3. 右侧备注

右侧备注只解释当前代码事实，不再挤到主流程里：

- `AgentContextBuilder` 负责 Profile、Skill、MCP、Memory、Experience、RAG、历史窗口和 token budget。
- `DefaultModelInvokeService` 负责模型配置、Provider 选择、流式兜底和返回 `ModelInvokeResult`。
- 工具批处理内部由 `ToolExecutionPlanner.plan` 决定串行或并行。
- 只有 `readOnly=true`、`LOW` 风险、`resourceKeys` 不冲突的工具才可能并行，最大并行数是 `MAX_PARALLEL_TOOLS = 4`。
- 高危工具未确认时不执行，返回 `[tool confirm required]`。
- 真正执行时由 `AgentToolDispatcher` 分流到 `SkillExecutor` 或 `McpToolExecutor`。

## 4. 持久化和观测

- Trace / Token / Hook 是观察与记录能力，不控制主流程。
- 持久化记忆只在 `memoryStrategy.mode = READ_WRITE` 时写入。

## 5. 一句话理解

当前单 Agent 是 `run()` 外层编排 + `runModelLoop()` 简洁循环 + 工具批处理。它不是 LangGraph，也没有 Planner、Reviewer、图状态机或多 Agent 角色拆分。
