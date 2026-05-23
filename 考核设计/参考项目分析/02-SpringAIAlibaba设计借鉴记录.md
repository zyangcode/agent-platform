# Spring AI Alibaba 设计借鉴记录

> 参考目录：`D:\study\蓝山最终考核项目\spring-ai-alibaba-main`。本文只记录对本项目有参考价值的设计方法，不直接引入 Spring AI Alibaba 的 Graph/Reactor 技术栈，也不照搬其平台模型。

---

## 1. 参考结论

Spring AI Alibaba 的价值主要在四类设计：

```text
1. Agent Framework 用 Hook / Interceptor 扩展 Agent 执行链路。
2. Graph Runtime 把 Agent / Workflow / Multi-Agent 统一成状态图执行。
3. Admin 平台把模型、工具、MCP、可观测性、调试 UI 作为平台能力管理。
4. Spring AI ChatClient / ToolCallingManager / Advisor 的用法可作为后续真实模型和工具调用参考。
```

对当前项目的直接结论：

```text
阶段 2 可以借鉴 TraceId 透传、调用链拦截器、模型/工具调用限制、Token 估算思想。
阶段 3 可以借鉴 Agent Chat UI 的消息结构、工具调用展示、Graph/Trace 可视化思路。
阶段 4 可以借鉴 Sequential / Parallel / Routing / Loop Agent 的编排模式。
暂不引入 Graph Core、WebFlux/Reactor 流式管线、完整 Admin 平台、RAG 工作流和 A2A。
```

原因：

```text
本项目当前阶段目标是自研可控的 Gateway 治理链和 PostgreSQL Trace。
现有技术栈是 Spring MVC + SseEmitter/StreamingResponseBody + MyBatis-Plus。
直接引入 Graph/Reactor 会改变运行模型，扩大范围，也会和当前考核主线冲突。
```

---

## 2. 借鉴点一：Hook / Interceptor 扩展链

参考文件：

```text
spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/ReactAgent.java
spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/interceptor/ModelInterceptor.java
spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/interceptor/InterceptorChain.java
```

Spring AI Alibaba 的 ReAct Agent 把扩展点拆成：

```text
beforeAgent
afterAgent
beforeModel
afterModel
model interceptor
tool interceptor
streaming interceptor
```

这对本项目的启发是：

```text
Gateway 治理链适合用责任链表达。
Agent Runtime 内部后续也可以引入轻量 listener / hook，而不是把 Trace、Token、脱敏、告警硬编码进主流程。
拦截器顺序必须明确：谁最外层、谁最先看到原始请求、异常如何传播。
```

阶段 2 第一轮建议不做通用 Hook 框架，只做最小 TraceService 调用：

```text
Gateway:
  startRoot
  finishRoot

AgentRuntime:
  startSpan(context.build)
  startSpan(model.invoke)
  recordTokenUsage
```

阶段 2 后半段或阶段 3 再考虑抽象：

```text
GatewayFilter / GatewayInterceptor
AgentRunListener
ModelInvokeListener
ToolInvokeListener
```

不要现在直接引入 Spring AI Alibaba 的 Hook 类型。它依赖 Graph State 和 Reactor Flux，和当前代码结构不匹配。

---

## 3. 借鉴点二：模型和工具调用限制

参考文件：

```text
spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/hook/modelcalllimit/ModelCallLimitHook.java
spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/hook/toolcalllimit/ToolCallLimitHook.java
spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/hook/TokenCounter.java
```

Spring AI Alibaba 把限制分成：

```text
thread limit
run limit
指定工具 limit
全部工具 limit
超过限制后 END 或 ERROR
```

本项目可以吸收这个策略，但要映射到自己的概念：

```text
thread limit  -> conversation 级限制
run limit     -> 单次 AgentRun 限制
tool limit    -> 单个 Skill/MCP 或全部工具调用限制
END           -> 返回受控的 SSE done/message
ERROR         -> 返回 SSE error 或 HTTP 429/400
```

阶段 1 已有：

```text
DefaultAgentRuntimeService.MAX_AGENT_STEPS = 3
```

阶段 2/阶段 4 可增强为：

```text
Profile.maxSteps 控制单次 AgentRun 最大模型循环。
Profile 或安全策略控制单次工具调用次数。
Token 配额不足时 Gateway 返回 429。
工具调用次数超限时返回可解释错误，不继续盲目循环。
```

Token 估算可以借鉴 `chars / 4` 的兜底方式。本项目已有 `DefaultModelInvokeService.estimateTokens`，后续记录 token_usage_logs 时继续保留 `estimated=true` 标记。

---

## 4. 借鉴点三：TraceId 对外透传

参考文件：

```text
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/tracing/TraceIdInterceptor.java
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/tracing/GlobalResponseBodyAdvice.java
```

Spring AI Alibaba Admin 的做法：

```text
从 Micrometer Tracer 当前 Span 取 traceId。
写入响应头 X-Request-ID。
对统一响应 Result 注入 traceId。
```

本项目阶段 2 的取舍：

```text
继续由 Gateway 自己生成 tr_xxx traceId。
SSE 每个事件 data 中都带 traceId。
后续普通 JSON 查询接口可以在 ApiResponse 里补 traceId，或至少写 X-Trace-Id 响应头。
OpenAI 兼容接口严格模式优先放 X-Trace-Id，避免污染 body。
```

暂不直接引入 Micrometer Tracing 作为主 Trace 存储：

```text
阶段 2 先用 PostgreSQL trace_roots / trace_spans / token_usage_logs 跑通可查询闭环。
后续再通过 TraceExporter 接 OTLP / SkyWalking / Jaeger。
```

---

## 5. 借鉴点四：ModelFactory 与 OpenAI 兼容模型适配

参考文件：

```text
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/model/llm/ModelFactory.java
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/model/llm/impl/OpenAIProvider.java
```

可借鉴点：

```text
Provider 负责声明 credential spec、endpoint、参数规则。
ModelFactory 根据 provider/model 配置创建 ChatModel。
OpenAI compatible endpoint 需要处理 /v1 后缀差异。
模型参数规则单独建模，例如 temperature、top_p、max_tokens、seed。
ObservationRegistry 可以挂到 Spring AI 模型调用上。
```

本项目当前已经有：

```text
model_providers
model_configs
DefaultModelConfigService
DefaultModelInvokeService
OpenAI compatible HTTP 调用
```

阶段 2 不需要重构为 Spring AI ChatClient。建议只吸收两点：

```text
1. 模型 Provider 的 endpoint 规范化逻辑要写清楚，避免 baseUrl 重复拼 /chat/completions。
2. model_configs.capabilities_json 后续可以扩展参数规则、是否支持 stream、是否支持 tool calling、多模态能力。
```

阶段 3 或后续真实流式输出时，再评估是否把 `core.model` 内部替换为 Spring AI ChatClient。替换边界必须仍然封在 `core.model`，不能让 Spring AI 类型外泄到 `core.agent` / `gateway` / `web`。

---

## 6. 借鉴点五：Spring AI 工具调用与 Tool 参数合并

参考文件：

```text
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/agent/BasicAgentExecutor.java
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/agent/tool/ToolArgumentsHelper.java
spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/agent/tool/CompositeToolCallbackProvider.java
```

可借鉴点：

```text
工具来源可以组合：平台工具、插件工具、MCP 工具、应用组件工具。
工具调用参数可以由模型输出参数 + 运行时 extraParams 合并而成。
ToolCallingManager 可以关闭模型客户端内部自动工具执行，改由平台自己接管工具调用。
工具调用结果需要回写到消息历史，再让模型二次总结。
```

本项目现状：

```text
SkillExecutor 和 McpToolExecutor 已由 AgentRuntime 显式调用。
ReAct 工具调用目前用 @skill:name {...} / @mcp:name {...} 文本协议解析。
阶段 1 以 mock 和可演示为主，暂不依赖模型原生 tool calling。
```

后续可增强：

```text
统一 ToolDescriptor，抽象 Skill/MCP 的名称、描述、参数 schema、风险等级。
支持 selectedSkillIds / selectedMcpToolIds 之外的运行时 extraParams。
真实模型支持 tool calling 时，仍由 AgentRuntime 接管工具执行，不让模型客户端自动执行。
工具调用结果写入 trace_spans，attributes 中记录 toolName、success、latency、截断后的结果摘要。
```

---

## 7. 借鉴点六：Agent Chat UI 与可视化调试

参考文件：

```text
spring-ai-alibaba-studio/README.md
spring-ai-alibaba-studio/agent-chat-ui/src/components/thread
spring-ai-alibaba-studio/agent-chat-ui/src/components/graph
```

可借鉴点：

```text
聊天 UI 不只是展示 assistant 文本，还展示工具请求、工具结果、人工确认、中断、Graph 节点状态。
Graph 页面把节点执行时间线、状态查看器、图结构放在同一个工作台里。
前端通过统一的 message/event 类型驱动 UI，而不是按接口写死。
```

本项目阶段 3 前端可以借鉴展示形态，但不要照搬技术实现：

```text
User Chat 页面展示 SSE event: thinking/action/observation/message/done/error。
Trace Detail 页面展示 trace_root、trace_spans、token_usage_logs。
工具调用用 action + observation 两类事件展示。
后续 Agent Team 页面再增加 Planner/Executor/Reviewer 节点视图。
```

阶段 2 后端需要提前保证：

```text
SSE event 格式稳定。
traceId 每个事件都有。
span_name / span_type 命名稳定。
token_usage_logs 字段足够支撑前端基础看板。
```

---

## 8. 后续阶段映射

### 8.1 阶段 2：AI Infra 网关治理

吸收：

```text
TraceId 响应头 / SSE 透传。
Model/Tool 调用限制策略。
责任链式 Gateway 治理链。
Token estimated 标记。
```

暂不吸收：

```text
Micrometer Tracing 主链路。
Graph Runtime。
Reactor Flux streaming pipeline。
Spring AI 自动工具执行。
```

### 8.2 阶段 3：前端控制台 MVP

吸收：

```text
Agent Chat UI 的消息分块展示。
工具调用请求/响应展示。
Trace 时间线和节点详情展示。
```

暂不吸收：

```text
完整 Graph 工作台。
复杂中断恢复 UI。
低代码 DSL 导出。
```

### 8.3 阶段 4：Agent Team 高分项

吸收：

```text
SequentialAgent。
ParallelAgent。
RoutingAgent。
LoopAgent。
HookPosition 思路。
Human-in-the-loop 高危工具确认。
```

本项目自己的落地方式：

```text
Planner / Executor / Reviewer 仍按项目设计实现。
不要引入 Graph Core 作为运行时依赖。
可借鉴状态图思想，但保持 AgentRuntimeService 边界稳定。
```

---

## 9. 已加入本项目决策的取舍

本项目继续坚持：

```text
Gateway 只做治理，不组装 Agent 上下文。
core.context 负责上下文预算和裁剪。
core.agent 负责 ReAct / Team 执行。
core.model 负责模型调用适配，Spring AI 类型不外泄。
Trace 第一轮使用 PostgreSQL 自研表。
SSE 使用 Spring MVC，不引入 WebFlux。
```

可以从 Spring AI Alibaba 长期吸收：

```text
拦截器链顺序设计。
模型/工具调用限制策略。
工具来源组合思路。
模型参数规则建模。
TraceId 对外透传。
前端工具调用和 Trace 时间线展示方式。
```

明确不照搬：

```text
Graph Core 运行时。
Reactor Flux 作为本项目阶段 2 SSE 主技术。
完整 Admin 低代码平台。
完整 RAG / Workflow / A2A。
Spring AI 自动接管工具调用。
```

---

## 10. 最终判断

Spring AI Alibaba 是成熟的 Agent 应用框架和平台参考，但它解决的是更通用、更复杂的 Agent/Workflow/Multi-Agent 问题。

本项目当前应只借鉴它的工程方法：

```text
用拦截器和 Hook 思想隔离治理逻辑。
用稳定事件和 Trace 数据支撑前端可视化。
用模型/工具调用限制保护 Agent 循环。
把模型供应商、参数规则、工具来源做成可配置能力。
```

不要在阶段 2 改变技术路线。阶段 2 的主线仍然是：

```text
PostgreSQL Trace/Token 表
core.trace 服务
Gateway 创建 root
AgentRuntime 记录 span/token
Web 查询 Trace
全量测试和手工 SSE 验收
```
