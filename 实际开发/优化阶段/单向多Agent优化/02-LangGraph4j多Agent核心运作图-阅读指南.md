# LangGraph4j 多 Agent 核心运作图阅读指南

现在本目录保留三类图：

| 文件 | 定位 |
| --- | --- |
| `02A-LangGraph4j多Agent核心运作图-第一版.drawio` | 当前落地版，对齐 `11-最终改造方案总览.md` 的实施边界 |
| `02B-LangGraph4j多Agent核心运作图-最终版.drawio` | 最终增强版，把并发、ReAct 子图、统一记忆写入、Checkpoint、Human-in-the-loop 等后续能力放进去 |
| `../../归档/已替代文档/02-LangGraph4j多Agent核心运作图-早期合并图.drawio` | 早期合并图，已归档为历史参考，不再作为当前方案图 |

读图时不要把第一版和最终增强版混在一起。先看 `02A`，确认当前要改什么；再看 `02B`，理解后续可以怎么扩。

## 1. 第一版主路线

第一版主路线是 `02A` 中间的蓝色箭头：

```text
DefaultAgentRuntimeService
  -> TeamRuntimeService
  -> DefaultTeamRuntimeService
  -> START
  -> build_context
  -> plan
  -> validate_plan
  -> schedule
  -> execute_batch
  -> review
  -> route_after_review
  -> final_answer
  -> END
```

这张图表达的核心结论是：

```text
保留 DefaultTeamRuntimeService 名字和入口，只把内部编排换成 LangGraph4j。
```

第一版明确不做：

```text
任务并发
REACT_TASK
Reflect
FinalizeMemoryWrites
Checkpoint
Human-in-the-loop
新增 Team 表
新增 LangGraphTeamRuntimeService 并行 Bean
```

## 2. 最终版主路线

最终版主路线是 `02B` 中间的蓝色和绿色箭头：

```text
DefaultAgentRuntimeService
  -> TeamRuntimeService
  -> DefaultTeamRuntimeService
  -> START
  -> build_context
  -> plan
  -> validate_plan
  -> schedule
  -> execute_batch
  -> evaluate_tasks
  -> global_review
  -> route_after_review
  -> reflect
  -> finalize_memory_writes
  -> final_answer
  -> END
```

最终版表达的是后续增强方向，不代表当前马上全部实现。

最终版仍然保留这个入口判断：

```text
DefaultTeamRuntimeService 仍然是唯一默认 Team 实现。
LangGraph4j 是它内部的编排内核。
```

最终版才加入：

```text
execute_batch 内部并发
REACT_TASK 子图
Reflect / FinalizeMemoryWrites
Checkpoint / resume / time travel
Human-in-the-loop
Team 专用 Context Schema
maxParallelTasks / maxReplans / maxReActSteps
```

## 3. 图例理解

- 蓝色圆角框：主流程处理节点。
- 黄色菱形：条件判断和路由点。
- 紫色区域：`execute_batch` 内部执行细节。
- 绿色虚线：Memory / RAG 读写边界，第一版只保留边界，最终版才写入。
- 灰色虚线：State / SSE / Trace / Token / Checkpoint 等运行支撑。
- 红色虚线：失败、重试、重规划、降级路线。

## 4. 异常回路

第一版异常回路：

```text
route_after_review:
  RETRY  -> execute_batch -> review -> route_after_review
  REPLAN -> plan -> validate_plan -> schedule -> execute_batch -> review -> route_after_review
  FINAL  -> final_answer -> END
```

最终版在这个基础上再扩展：

```text
evaluate_tasks:
  还有 ready tasks -> schedule

route_after_review:
  HUMAN_APPROVAL -> pending -> approve/reject -> resume
```

## 5. 侧边能力

右侧不是新的执行链路，只是支撑主路线：

- `TeamGraphState`：保存计划、任务、执行结果、审查结果、路由、usage 和计数器。
- `Memory / RAG / Experience`：多 Agent 可以读快照；长期写入最终由 Reflect / Finalize 统一收口。
- `SSE / Trace / Token 护栏`：保证前端事件、Trace Span、Token 计数和超时上限不丢。
- `Human-in-the-loop`：只属于最终增强版，用于高风险工具、高成本任务、争议记忆写入等暂停/恢复场景。

## 6. 一句话理解

第一版解决“用 LangGraph4j 替换手写 Team 编排”；最终版解决“把 Team Runtime 扩展成可并发、可恢复、可人工介入、可统一沉淀记忆的多 Agent 编排内核”。
