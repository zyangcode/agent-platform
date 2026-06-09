# LangGraph4j 多 Agent 核心运作图阅读指南

这张图已经从原来的两页合并成一页。读图时不要从所有框同时看起，按下面顺序看。

## 1. 先看主路线

主路线是图中间的粗蓝箭头：

```text
入口兼容层
  -> START
  -> build_team_context
  -> plan
  -> validate_plan
  -> schedule
  -> execute_batch
  -> evaluate_tasks
  -> global_review
  -> reflect
  -> finalize_memory_writes
  -> final_answer
  -> END
```

这条线表达核心结论：LangGraph4j 负责宏观状态流转，真正的动态并发放在 `execute_batch` 节点内部。

## 2. 再看图例

- 蓝色圆角框：主流程处理节点。
- 黄色菱形：条件判断和路由点。
- 紫色区域：`execute_batch` 内部并发执行，不是主图的新路线。
- 绿色虚线：Memory / RAG 读写边界。
- 灰色虚线：State / SSE / Trace / Token 等运行支撑。
- 红色虚线：失败、重试、重规划、降级路线。

## 3. 再看异常回路

异常回路只看红色虚线：

- `计划有效？` 为否：回到 `plan`，要求修正；连续失败时降级成单任务。
- `任务状态？` 失败且还有预算：进入 `replan`，再回到 `schedule`。
- `审查结论？` 需要重规划：回到 `replan`。
- 审查不完全通过但还能回答：走降级回答，最终答案必须说明风险。

## 4. 最后看侧边能力

右侧不是新的执行链路，只是支撑主路线：

- `LangGraphTeamState`：保存计划、任务图、执行结果、失败原因、计数器和 limits。
- `Memory / RAG / Experience`：多 Agent 可以读快照，但不能各自直接写长期记忆。
- `SSE / Trace / Token 护栏`：保证前端事件、Trace Span、Token 计数和超时上限不丢。
- `角色边界`：Planner 不调工具，Executor 才能调工具，Reviewer 只审查，Reflector 只生成记忆候选。

## 5. 一句话理解

外部仍然是原来的 `TEAM` 模式；内部把旧 Team Runtime 替换成 LangGraph4j 状态机。主图只负责“下一步走哪里”，并发执行、工具调用、ReAct 子图、记忆写入都被收口到明确的节点内部。
