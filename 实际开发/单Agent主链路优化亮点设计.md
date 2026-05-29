# 单 Agent 主链路优化亮点设计

## 目标

在不改变当前 `Web -> Gateway -> Core AgentRuntime` 主架构的前提下，把后续优化亮点收敛到**单 Agent 主链路**里。单 Agent 的核心不是预设固定步骤，而是让模型在受控 Harness 中循环决策：

```text
构建上下文 -> 模型生成 -> 判断最终答案或工具调用
  -> 工具执行 -> observation 写回上下文 -> 下一轮模型生成
```

本文只同步设计和图，不修改当前代码。

配套图见 [single-agent-optimization-flow.drawio](../docs/single-agent-optimization-flow.drawio)。

## 当前状态与边界

当前项目已经有基础链路：

- `Web -> Gateway -> Core AgentRuntime` 路由成立。
- Gateway 已承担鉴权、Trace、Token 配额、敏感数据治理、SSE 输出。
- Core 已有 Profile、Skill、MCP、Memory、模型调用、对话落库。
- Agent Runtime 已有 ReAct 雏形：模型输出工具调用标记后，Runtime 执行 Skill/MCP，并把结果写回上下文。

当前仍需优化：

- SSE 还不是 token 级实时输出。
- 单 Agent Runtime 中仍有 `calculator/weather/search` 这类硬编码工具预选。
- 上下文管理偏硬裁剪，缺少 micro compact、auto compact 和失败降级。
- ToolResolver/ToolDispatcher 尚未完全成为 Runtime 唯一工具入口。
- RAG/向量库、Jar Skill 热加载、高危工具确认还没有进入单 Agent 主链路闭环。

能力边界：

- Gateway 只做入口、治理、协议转换和 SSE，不拼 Prompt、不决定工具、不执行 Skill/MCP。
- Core AgentRuntime 负责上下文、模型循环、工具执行调度、最终答案清洗。
- SubAgent/Task 工具属于可选增强，不等同于 Team Planner/Executor/Reviewer。
- 向量库建议采用 PostgreSQL + pgvector，不先引入 Qdrant/Milvus 等独立服务。

## 精细流程

### A. 入口与 Gateway 治理

1. 用户在 Web Chat 输入需求，前端通过 SSE 接收事件。
2. Web Controller 带 JWT 转发到 Gateway。
3. Gateway 创建 SSE 输出流。
4. Gateway 治理链执行：
   - 创建 `traceId`
   - Token 配额预扣
   - 请求敏感数据扫描
   - 告警兜底

关键箭头语义：

- `用户消息 + profileId + skill/mcp 选择`：前端传给 Web。
- `内部请求，保持 SSE`：Web 转发给 Gateway。
- `AgentRunCommand`：Gateway 治理后交给 Core 的运行命令。
- `message_delta`：模型 token 增量实时从 Core/Gateway 推给 Web。

### B. Core 上下文与 RAG

5. `AgentRuntimeService.run` 进入单 Agent 总控。
6. `AgentContextBuilder` 组装：
   - Platform System Prompt
   - Profile Prompt
   - History
   - Memory
7. `ToolResolver` 生成当前可用工具声明：
   - Skill
   - MCP
   - 参数 Schema
   - 风险等级
8. `Query Embedding` 把用户问题转成查询向量。
9. `PostgreSQL + pgvector` 作为向量库：
   - `knowledge_chunk.embedding` 存文档分段向量
   - 用 cosine/L2 等相似度检索 topK
10. `RAG Context Composer` 把 topK 知识片段压成参考资料，并进入 token 预算。
11. 输出 `Context Budget Snapshot`：
   - `messages`
   - `tool specs`
   - `rag refs`
   - `tokenEstimate`

注意：RAG 检索结果不能无限拼接，必须和历史、工具声明一起进入上下文预算。

## 主模型 ReAct 循环

显式循环如下：

```text
Round Start
  -> Micro Compact
  -> Token Budget Decision
      -> 超阈值：Auto Compact / Fallback Trim
      -> 未超阈值：直接继续
  -> Main Model Streaming Call
  -> Parse Assistant Output
      -> 最终答案：Final Answer Builder -> message/done
      -> 工具调用：Tool Request Batch -> 并行工具执行
  -> Tool observations 写回 messages
  -> Loop Limit Decision
      -> 未超限：回到 Round Start
      -> 超限：降级为当前最优答案/风险说明
```

### 12. Round Start

每一轮先检查整体约束：

- `maxRounds`
- `maxToolCalls`
- `timeout`
- `token danger ratio`

### 13. Micro Compact

作用：每轮模型调用前做低成本清理，避免脏上下文进入模型。

处理对象：

- 过长 tool observation
- 过长 RAG 片段
- 异常输出
- 大 JSON 结果

策略：保留关键前缀和截断说明，不直接整段删除。

### 14-15. Token Budget 与 Auto Compact

Token 预算计算范围：

- System Prompt
- Profile Prompt
- Memory
- History
- Tool Specs
- RAG refs
- 当前用户输入
- 上轮 observation

超过阈值时：

- 旧历史摘要化
- 保留最近 N 条原文
- 摘要失败则 fallback trim，并注入系统提示说明上下文已降级

### 16. Main Model Streaming Call

作用：

- 调用主模型。
- token chunk 通过回调形成 `message_delta` SSE。
- 同时聚合完整 assistant message，供后续解析、落库和记忆使用。

### 17. Parse Assistant Output

模型输出分三类：

- 最终答案：进入 Final Answer Builder。
- 单个工具调用：进入 Tool Request Batch。
- 多个工具调用：进入 Tool Request Batch，并可并行执行。

非法输出应进入修正或降级路径，不能把内部草稿直接返回用户。

### 18. Final Answer Builder

作用：只返回用户可读答案。

必须过滤：

- `User request`
- `Goal`
- `Execution results`
- `task-*`
- 工具原始 JSON
- mock search 结果
- Reviewer/Executor 内部错误

当工具链失败时，返回简洁降级答案，而不是返回内部执行清单。

## 并行工具执行

工具调用不应画成单一路径。模型一次响应可能返回多个互不依赖的工具调用，应走 Fork/Join。

```text
Tool Request Batch
  -> Fork
      -> Skill 调用
      -> MCP 调用
      -> Task/SubAgent 调用（可选增强）
  -> Risk Guard
  -> ToolDispatcher
  -> Tool Result Normalizer
  -> Join
  -> observation 列表写回 messages
```

### Fork

作用：把同一次模型响应中的多个独立工具请求拆开执行。

并行前提：

- 工具之间没有依赖关系。
- 没有共享可变状态冲突。
- 没有超过 `maxToolCalls`。

### Skill 调用

作用：执行配置型 Skill 或 Jar Skill。

Jar Skill 热加载属于 Skill 子系统：

- Developer/Admin 上传 Jar。
- 校验通过后进入 ENABLED/LOADED。
- URLClassLoader 隔离加载。
- 新版本加载成功后切换 `currentVersion`。
- Runtime 下一次构建上下文时自然看到新 Skill。

### MCP 调用

作用：调用外部 MCP 工具。Runtime 不直接感知外部协议细节，只通过 `ToolDispatcher` 收到统一结果。

### Task/SubAgent 调用

这是可选增强，不是当前必须项，也不是 Team 模式。

作用：

- 把复杂子任务委托给独立上下文的小 Agent。
- 子 Agent 使用裁剪后的工具集。
- 防递归：子 Agent 不暴露 task 工具。

边界：

- SubAgent 仍服务于单 Agent 的一个工具调用。
- Team 模式才是 Planner/Executor/Reviewer 三角色协作。

### Risk Guard

按风险等级处理：

- LOW：直接执行。
- MEDIUM：记录 Trace，可配置是否确认。
- HIGH：必须确认或策略阻断。

Web 场景不能照搬 CLI 的 y/n/a，应通过 SSE 发 `tool_confirm_required`，前端确认后继续。

### ToolDispatcher

目标：Runtime 不再直接依赖 `SkillExecutor` / `McpToolExecutor`。

职责：

- 统一分发 Skill/MCP/SubAgent。
- 校验工具名是否存在。
- 捕获工具异常。
- 输出统一 `ToolDispatchResult`。

### Tool Result Normalizer

作用：把工具结果统一成 observation。

规则：

- 成功：写入精简后的结果。
- 空结果：写 `[empty]`。
- 失败：写错误摘要。
- 工具崩溃：写 `[tool crash]`，让模型换策略。

### Join

作用：

- 等待并行工具全部完成。
- 按原工具请求顺序合并 observation。
- 写回 `messages`。
- 进入下一轮 Round Start。

## 存储与治理观测

存储侧：

- `conversation_message`：用户消息、助手消息、工具 observation。
- `memory`：长期记忆和会话摘要候选。
- `knowledge_chunk`：RAG 分段文本和 embedding。
- `token_usage`：模型 usage、估算 usage、结算状态。

Trace Span 建议覆盖：

- `context.build`
- `rag.search`
- `compact.micro`
- `compact.auto`
- `model.invoke`
- `tool.execute`
- `final.answer.build`

敏感数据扫描范围：

- 用户请求
- 工具入参
- Skill/MCP 返回
- RAG 片段
- 模型最终响应
- 记忆写入
- Trace/日志摘要

## 推荐落地顺序

1. Token 级流式输出。
2. 上下文压缩和 Token 预算。
3. ToolResolver + ToolDispatcher 收口，删除硬编码工具预选。
4. Skill Jar 热加载。
5. RAG + PostgreSQL pgvector 最小闭环。
6. 高危工具确认。
7. 可选 Task/SubAgent 工具。
8. 前端高级化。
9. 飞书机器人扩展。

这个顺序的原因：

- 流式和上下文预算先保证主链路体验与稳定性。
- 工具链收口后，Jar Skill、MCP、SubAgent 才能作为统一工具能力接入。
- RAG 必须受 token 预算约束，否则会把上下文撑爆。
- 前端和飞书不应早于核心 Agent 运行质量。
