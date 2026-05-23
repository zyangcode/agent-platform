# emo 设计借鉴记录

> 参考项目目录：`D:\study\蓝山最终考核项目\emo-master`。  
> 本文整理 emo 与当前 `agent-platform` 的差异、可借鉴设计、阶段映射和取舍边界。本文只作为设计参考，不直接照搬 emo 的模块拆分、技术栈和完整产品能力。

---

## 1. 总体判断

emo 是一个“功能型完整 Agent 平台样板”，偏产品演示：

```text
LLM
Memory
Skill
MCP
Agent Team
Console
Vue 前端
Docker Compose
```

当前 `agent-platform` 是“按考核路线推进的工程骨架”，偏可控交付：

```text
身份与权限
Agent Profile
Web -> Gateway -> Core
SSE
PostgreSQL 表归属
Gateway 治理链
架构测试
分阶段验收
```

结论：

```text
如果只看 Agent 能力实现，emo 更成熟。
如果看考核交付架构，当前 agent-platform 更稳。
```

因此不建议替换为 emo 架构，而是从 emo 中局部吸收成熟设计。

---

## 2. 两个项目的核心区别

| 维度 | 当前项目 `agent-platform` | `emo-master` |
|---|---|---|
| 模块规模 | 4 个 Maven 模块：common/core/web/gateway | 10+ 后端模块 + skills 子模块 + Vue 前端 |
| 架构重点 | Web/Gateway/Core 强边界，面向考核的 AI Infra 治理 | Agent 运行时能力完整，偏平台产品 |
| 数据库 | PostgreSQL + MyBatis-Plus + Flyway | JPA 为主，部署说明中包含 MySQL/Redis/RabbitMQ |
| 前端 | 规划 React + Vite + Tailwind + shadcn/ui | Vue 3 + Element Plus 控制台 |
| Agent Runtime | 简化 ReAct：`@skill:` / `@mcp:` 文本协议，最多 3 步 | Pipeline 阶段化：上下文、记忆、工具解析、模型聚合、持久化 |
| LLM 层 | `ModelInvokeService` 抽象，当前更偏 Mock/统一入口 | 独立 `echomind-llm`，多 Provider、流式、函数调用适配 |
| Skill | 数据表和执行接口已打底 | 独立 `skill-api` + JAR 热加载 + 示例 Skill |
| MCP | 当前是表和执行接口级 MVP | stdio MCP Client、外部 MCP runtime、工具适配更完整 |
| 记忆 | 持久化 summary + recall 基础能力 | MySQL 历史 + Redis 近期上下文 + Redis Stack 向量 + 用户长期画像 |
| Team | 当前文档规划，代码未实现 | Planner/Executor/Reviewer 黑板状态机已实现 |
| 治理 | Gateway 独立模块，适合后续 Trace/Quota/脱敏/告警 | 没有当前项目这种明确 Gateway 治理层 |
| 测试/边界 | ArchUnit 和阶段测试意识更强 | 功能测试不少，但考核边界不如当前项目明确 |

---

## 3. 当前项目更强的地方

当前项目更适合继续作为考核交付主线，原因是：

```text
1. web 和 gateway 已分离，后续做 Trace、Token 配额、脱敏、告警更顺。
2. core 内部按 identity/profile/model/skill/mcp/memory/agent/context 分包，和数据库归属约束一致。
3. API Key 绑定 Application、JWT 浏览器入口、内部 Token、OpenAI 兼容入口更贴合项目设计。
4. 有 ArchUnit 测试防止跨层访问，工程纪律更适合长期推进。
5. PostgreSQL + Flyway + MyBatis-Plus 更符合当前正式数据库模型。
```

需要继续坚持的边界：

```text
Gateway 只做鉴权、Trace、Token、脱敏、告警、协议转换和调用适配。
Core 负责 Agent 上下文、模型调用、Skill/MCP 执行、记忆和业务表。
Web 负责浏览器入口、JWT 鉴权、管理查询和转发 Gateway。
不要让参考项目的产品复杂度拖垮当前考核 MVP。
```

---

## 4. emo 最值得马上吸收的四个设计

### 4.1 Pipeline

emo 已有：

```text
ExecutionPipeline
PipelineStage
PipelineContext
ContextEnrichStage
ToolResolutionStage
ResultAggregationStage
MemoryPersistStage
```

当前项目的 `DefaultAgentRuntimeService` 目前承担了：

```text
保存用户消息
构建上下文
调用模型
解析工具调用
执行 Skill/MCP
再次调用模型
保存助手消息
写入记忆
```

短期可运行，但阶段 2 之后会不断变长。Pipeline 的价值是把一次 Agent 执行拆成多个固定阶段，每个阶段只负责一件事。

建议后续演进为：

```text
AgentRunCommand
  -> ContextBuildStage
  -> ToolResolutionStage
  -> ModelInvokeStage
  -> ToolExecutionStage
  -> ResultAggregationStage
  -> MemoryPersistStage
  -> AgentRunResult
```

Pipeline 不是新功能，而是组织 Agent 执行流程的一种架构。

它能支撑：

```text
Trace 每个阶段记录 span
Token 每次模型调用记录 usage
脱敏扫描用户输入、工具入参、工具返回、模型输出
SSE 推送 thinking/action/observation/message/done
Team 复用上下文、工具、模型调用阶段
Skill/MCP 调用统一审计
```

### 4.2 统一工具注册

emo 的思路是把 Skill 和 MCP 最终都进入统一能力注册表：

```text
Skill JAR
External MCP Server
  -> CapabilityRegistry
  -> ResultAggregationStage
  -> LLM Function Calling
```

当前项目 Skill 和 MCP 是两套执行接口：

```text
SkillRegistry / SkillExecutor
McpToolRegistry / McpToolExecutor
```

后续可以在 `core.agent.tool` 或 `core.tool` 内部增加统一工具视图：

```text
AgentTool {
  name
  description
  parameterSchema
  sourceType: SKILL | MCP
  riskLevel
  invoker
}
```

这样 Agent Runtime、前端工具展示、Profile 绑定、Team Executor 都可以复用同一套工具描述。

### 4.3 LLM Provider Registry

emo 的 `echomind-llm` 已有：

```text
ModelProvider
ModelProviderRegistry
ProviderRequest
DynamicModelRouter
OpenAICompatibleProvider
DeepSeekProvider
MockModelProvider
```

当前项目已有：

```text
model_providers
model_configs
ModelConfigService
ModelInvokeService
```

建议保留 `ModelInvokeService` 作为 core 对外门面，在内部拆出：

```text
ModelProvider
ModelProviderRegistry
ProviderRequest
ProviderResponse
MockModelProvider
OpenAiCompatibleProvider
UsageParser
```

阶段 2 的 Trace、Token 配额、usage 统计，都依赖模型调用结果结构稳定。因此这个设计应优先吸收。

### 4.4 Team 黑板状态机

emo 的 Team 设计包含：

```text
Team
Run
Step
Event
Planner
Executor
Reviewer
Retry
Clarification
Final Report
```

它的核心价值不是具体代码，而是状态机和黑板模型：

```text
用户任务 -> Run
Planner 生成 Step
Reviewer 审查计划
Executor 并发执行 Step
Reviewer 审查结果
失败可重试
歧义可澄清
全过程写 Event
```

阶段 4 可以借鉴这个模型，但不建议照搬 emo 的大 Service。当前项目落地时建议拆成：

```text
TeamRunService
TeamPlanningService
TeamExecutionService
TeamReviewService
TeamEventService
```

---

## 5. 四个设计对应阶段

| 设计 | 对应阶段 | 建议时机 |
|---|---|---|
| Pipeline | 阶段 2 后 / 阶段 3 前 | 阶段 2 先用小方法封装 Trace/Token 埋点，跑通闭环后再评估是否阶段化执行 |
| 统一工具注册 | 阶段 1 后半 / 阶段 3 前 | 阶段 3 前端展示工具、Profile 绑定工具、阶段 4 Team Executor 都需要 |
| LLM Provider Registry | 阶段 1 后半 / 阶段 2 | 多模型供应商、真实模型调用、usage 解析、Token 配额和 Trace 需要稳定结构 |
| Team 黑板状态机 | 阶段 4 | 明确属于 Agent Team 高分项，不要抢在主链路前面 |

推荐顺序：

```text
1. 阶段 2 优先做 LLM Provider Registry 雏形。
2. 阶段 2 主线完成 Trace / Token Usage 闭环。
3. 阶段 2 完成后或阶段 3 前评估 Pipeline 重构。
4. 阶段 3 前做统一工具注册。
5. 阶段 4 再做 Team 黑板状态机。
```

一句话：

```text
LLM Provider Registry 是阶段 2 的优先增强；Pipeline 是阶段 2 后的重构方向，不作为阶段 2 前置任务；统一工具注册放在阶段 3 前。
Team 黑板状态机只放阶段 4。
```

---

## 6. 谁做得更好

四个 Agent 能力设计上，emo 做得更完整：

| 设计 | 谁做得更好 | 原因 |
|---|---|---|
| Pipeline | emo | 已经有 ExecutionPipeline、PipelineStage、PipelineContext |
| 统一工具注册 | emo | Skill 和 MCP 最终进入统一能力注册表 |
| LLM Provider Registry | emo | 独立 llm 模块，支持 Provider Registry、多模型路由、流式和函数调用适配 |
| Team 黑板状态机 | emo | 已实现 Team/Run/Step/Event 入库，Planner/Executor/Reviewer 状态推进 |

但当前项目在考核交付架构上更好：

| 方向 | 谁做得更好 |
|---|---|
| Web / Gateway / Core 边界 | 当前项目 |
| 考核阶段规划 | 当前项目 |
| 权限、JWT、API Key、Application 体系 | 当前项目 |
| Gateway 治理链预留 | 当前项目 |
| PostgreSQL + Flyway + 表归属约束 | 当前项目 |
| ArchUnit 架构测试 | 当前项目 |

最终判断：

```text
Agent 能力实现：emo 更成熟。
考核交付架构：当前项目更稳。
```

---

## 7. Pipeline 解释

Pipeline 就是把一次 Agent 执行拆成多个固定阶段依次处理。

不用 Pipeline 时，当前执行逻辑大致是：

```text
AgentRuntimeService.run()
  1. 保存用户消息
  2. 构建上下文
  3. 调模型
  4. 解析工具调用
  5. 执行 Skill/MCP
  6. 再调模型
  7. 保存助手消息
  8. 写记忆
```

用了 Pipeline 后，变成：

```text
AgentRunCommand
   ↓
ContextBuildStage        构建 system prompt、历史消息、长期记忆
   ↓
ToolResolutionStage      找出当前 Agent 可用 Skill/MCP
   ↓
ModelInvokeStage         调模型
   ↓
ToolExecutionStage       如果模型要调工具，就执行工具
   ↓
ResultAggregationStage   整理最终回复
   ↓
MemoryPersistStage       保存对话和记忆
   ↓
AgentRunResult
```

每个 Stage 只负责一件事。

和当前项目能力的对应关系：

| 当前能力 | Pipeline 阶段 |
|---|---|
| `DefaultAgentContextBuilder` | `ContextBuildStage` |
| `SkillRegistry` / `McpToolRegistry` | `ToolResolutionStage` |
| `ModelInvokeService` | `ModelInvokeStage` |
| `SkillExecutor` / `McpToolExecutor` | `ToolExecutionStage` |
| `MemoryWriteService` | `MemoryPersistStage` |
| SSE 事件 | 每个 Stage 执行时发事件 |
| Trace Span | 每个 Stage 开始/结束记录 span |

Pipeline 要解决的问题：

```text
避免 DefaultAgentRuntimeService 越来越长。
让 Trace、Token、脱敏、SSE、工具审计能插入到稳定阶段中。
让 Team 模式复用基础 Agent 执行能力。
```

---

## 8. 现在应该做什么

当前不建议马上照搬 emo 改大结构。更稳的动作是：

```text
1. 打开阶段 2 执行计划，确认下一步是不是 Trace / Token Usage 闭环。
2. 如果阶段 2 要开始，优先补 LLM Provider Registry 雏形，稳定 provider/model/usage 返回结构。
3. 再推进 Gateway 治理链：Trace、Token、脱敏、告警。
4. Pipeline 不作为阶段 2 前置任务；等 Trace / Token 闭环稳定后再单独评估重构。
```

建议阶段 2 前的最小模型调用结构：

```text
core.model.api.ModelInvokeService
  -> core.model.provider.ModelProviderRegistry
  -> core.model.provider.ModelProvider
  -> core.model.provider.MockModelProvider
  -> core.model.provider.OpenAiCompatibleProvider
```

这样可以先稳定：

```text
providerId
modelName
request messages
stream flag
assistant message
promptTokens
completionTokens
totalTokens
estimated flag
raw response summary
```

阶段 2 的 Token 配额和 Trace 就不会被模型适配层反复牵动。

---

## 9. 不建议照搬的内容

不建议现在吸收：

```text
emo 的多 Maven 模块拆分
RabbitMQ 异步聊天链路
Redis Stack 向量记忆
完整用户长期画像服务
Vue 前端实现
完整 Skill 市场和热加载策略
Team 大 Service 代码结构
```

原因：

```text
这些能力会显著扩大当前阶段范围。
部分技术栈和当前项目设计不一致。
当前考核主线优先级是 Gateway 治理和可演示闭环。
```

可以长期借鉴，但不要作为阶段 2 的阻塞项。

---

## 10. 最终取舍

当前项目继续保留：

```text
agent-platform-common
agent-platform-core
agent-platform-web
agent-platform-gateway
```

emo 只作为局部设计来源：

```text
LLM Provider Registry -> 阶段 2 优先吸收
Pipeline -> 阶段 2 后或阶段 3 前评估重构
统一工具注册 -> 阶段 3 前吸收
Team 黑板状态机 -> 阶段 4 吸收
```

最重要的原则：

```text
不换架构。
不抢阶段。
不引入不必要中间件。
只吸收能增强当前考核主线的设计。
```
