# Trace 与 Token 设计

> 本文档定义阶段 2 的可观测数据模型。目标是先用 PostgreSQL 自研 Trace 跑通主链路，再为前端 Trace 页面、Token 统计、OTLP 导出预留扩展点。

---

## 1. 核心概念

```text
trace_root
  一次完整 Chat 请求。它回答：谁在什么时候发起了什么请求，最终成功还是失败，耗时多少。

trace_span
  请求内部的一个步骤。它回答：这次请求经过了哪些关键环节，每个环节耗时多少。

token_usage_log
  一次模型调用产生的 Token 用量。它回答：哪个应用、哪个模型、哪个 Provider 消耗了多少 Token。
```

三者关系：

```text
trace_roots 1 --- n trace_spans
trace_roots 1 --- n token_usage_logs
trace_spans 1 --- 0..1 token_usage_logs
```

阶段 2 只做最小可用可观测闭环，不把参考项目的完整平台能力搬进来。

本阶段吸收的参考思路：

```text
AgentX：Trace/Token 独立建模，按执行阶段拆 span。
Spring AI Alibaba：Hook/Interceptor、TraceId 透传、模型/工具调用限制的设计思想。
JeecgBoot：OpenAPI 调用日志、API Key 维度、后台 Trace/Token 页面字段预留。
InterviewGuide：如 03 参考文档中有接口、答辩或文档表达建议，则作为阶段文档表达参考。
```

本阶段明确不引入：

```text
Graph Core / Reactor / WebFlux。
JeecgBoot 动态网关路由。
完整低代码、RAG、工作流、报表、大屏。
完整 Hook 框架和复杂 Agent Team 编排。
```

---

## 2. TraceId 规则

阶段 1 已由 Gateway 生成 traceId：

```java
"tr_" + UUID.randomUUID().toString().replace("-", "")
```

阶段 2 继续沿用：

```text
traceId 示例：tr_6a5c03e40f4340bba091b7aedcce50f0
```

约定：

```text
traceId 由 Gateway 生成。
Gateway 写入 trace_roots 的开始记录。
Core 使用同一个 traceId 写入 span 和 token_usage。
SSE 每个事件继续带 traceId。
conversation_messages.trace_id 继续保存这个 traceId。
```

---

## 3. 数据库表设计

迁移文件：

```text
agent-platform-core/src/main/resources/db/migration/V006__init_trace.sql
```

### 3.1 trace_roots

建议 SQL：

```sql
create table trace_roots (
    id bigserial primary key,
    trace_id varchar(64) not null unique,
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    profile_id bigint references agent_profiles (id),
    conversation_id bigint references conversations (id),
    client_request_id varchar(128),
    entrypoint varchar(32) not null,
    agent_mode varchar(32),
    status varchar(32) not null,
    error_code varchar(64),
    error_message text,
    started_at timestamp not null,
    ended_at timestamp,
    latency_ms bigint,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_trace_roots_tenant_started on trace_roots (tenant_id, started_at desc);
create index idx_trace_roots_application_started on trace_roots (application_id, started_at desc);
create index idx_trace_roots_profile_started on trace_roots (profile_id, started_at desc);
create index idx_trace_roots_status_started on trace_roots (status, started_at desc);
```

字段说明：

```text
trace_id          对外展示和查询用 ID。
entrypoint        INTERNAL_WEB 或 API_KEY，区分 Web 登录态调用和开放 API Key 调用。
agent_mode        agent 或 none。
status            RUNNING / SUCCESS / FAILED。
latency_ms        整体耗时。
metadata          第一版可记录 modelConfigId、stream、clientIp、userAgent、apiKeyPrefix 等扩展信息。
```

第一版不强制把所有入口字段都建成独立列。以下字段优先放入 metadata，后续查询频繁后再升为独立列：

```text
client_ip
user_agent
api_key_prefix
request_path
request_method
stream
model_config_id(agent_mode=none 时尤其有用)
```

### 3.2 trace_spans

建议 SQL：

```sql
create table trace_spans (
    id bigserial primary key,
    trace_id varchar(64) not null references trace_roots (trace_id),
    parent_span_id bigint references trace_spans (id),
    span_name varchar(128) not null,
    span_type varchar(64) not null,
    component varchar(64) not null,
    status varchar(32) not null,
    started_at timestamp not null,
    ended_at timestamp,
    latency_ms bigint,
    error_code varchar(64),
    error_message text,
    attributes jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp
);

create index idx_trace_spans_trace_started on trace_spans (trace_id, started_at);
create index idx_trace_spans_type_started on trace_spans (span_type, started_at desc);
```

第一轮 span 建议：

```text
gateway.chat_stream       Gateway 收到请求并驱动 SSE。
agent_runtime.run         Core AgentRuntime 执行。
context.build             构建模型上下文。
model.invoke              调用模型。
skill.execute             执行 Skill，第一轮可选。
mcp.execute               执行 MCP Tool，第一轮可选。
memory.write              写长期记忆，第一轮可选。
```

字段说明：

```text
span_name       可读名称，例如 model.invoke。
span_type       GATEWAY / AGENT / CONTEXT / MODEL / SKILL / MCP / MEMORY。
component       web / gateway / core。
attributes      记录 modelConfigId、modelName、providerId、providerType、httpStatus 等。
```

### 3.3 token_usage_logs

建议 SQL：

```sql
create table token_usage_logs (
    id bigserial primary key,
    trace_id varchar(64) not null references trace_roots (trace_id),
    span_id bigint references trace_spans (id),
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    profile_id bigint references agent_profiles (id),
    model_config_id bigint not null references model_configs (id),
    provider_id bigint not null references model_providers (id),
    model_name varchar(128) not null,
    provider_type varchar(32) not null,
    prompt_tokens int not null default 0,
    completion_tokens int not null default 0,
    total_tokens int not null default 0,
    estimated boolean not null default false,
    created_at timestamp not null default current_timestamp
);

create index idx_token_usage_trace on token_usage_logs (trace_id);
create index idx_token_usage_application_created on token_usage_logs (application_id, created_at desc);
create index idx_token_usage_model_created on token_usage_logs (model_config_id, created_at desc);
create index idx_token_usage_provider_created on token_usage_logs (provider_id, created_at desc);
```

字段说明：

```text
estimated = true   表示 Token 是本地估算，例如 mock-chat 或 provider 没返回 usage。
estimated = false  表示 Token 来自真实模型响应 usage。
```

---

## 4. Java 接口设计

### 4.1 TraceService

建议位置：

```text
agent-platform-core/src/main/java/com/ls/agent/core/trace/api/TraceService.java
```

建议方法：

```java
public interface TraceService {
    TraceRootDTO startRoot(StartTraceRootCommand command);

    void finishRoot(FinishTraceRootCommand command);

    TraceSpanDTO startSpan(StartTraceSpanCommand command);

    void finishSpan(FinishTraceSpanCommand command);
}
```

原则：

```text
startRoot 只在 Gateway 主入口调用。
finishRoot 在请求成功或失败时调用。
startSpan / finishSpan 在 Core 关键步骤调用。
TraceService 内部捕获写库异常，避免影响 Chat 主流程。
startRoot 失败时仍继续 Chat；finishRoot 失败不能影响 SSE done。
span 写入失败只记录日志，不反向中断 AgentRuntime。
```

TraceService 的失败策略必须清楚：

```text
数据库写入异常：捕获并 log.warn，返回空 DTO 或 no-op DTO。
finishRoot 找不到 root：记录 warn，不抛出影响主链路的异常。
finishSpan 找不到 span：记录 warn，不抛出影响主链路的异常。
Trace 数据不完整时，以 Chat 成功为优先；测试中要覆盖 Trace 写入失败不影响 Chat。
```

### 4.2 TokenUsageService

建议位置：

```text
agent-platform-core/src/main/java/com/ls/agent/core/quota/api/TokenUsageService.java
```

建议方法：

```java
public interface TokenUsageService {
    void record(RecordTokenUsageCommand command);
}
```

调用时机：

```text
DefaultModelInvokeService.invoke 成功拿到 ModelInvokeResult 后记录。
```

如果 ModelInvokeService 当前不知道 tenantId/applicationId/profileId，可以第一轮在 AgentRuntime 拿到 ModelInvokeResult 后记录。这样字段更完整，也不需要立刻改 ModelInvokeCommand。

TokenUsageService 的失败策略：

```text
token_usage_logs 写入失败不能导致 Chat 失败。
Token 配额预扣/结算属于后续治理能力，不能和普通 token_usage_logs 混为一谈。
如果后续开始做真实配额扣减，配额扣减失败必须阻断请求；普通 Trace/Usage 明细失败仍只降级记录日志。
```

---

## 5. 第一轮推荐实现策略

为了少改主链路，第一轮推荐：

```text
Gateway:
  创建 trace_root。
  成功时 finishRoot(SUCCESS)。
  异常时 finishRoot(FAILED)。

AgentRuntime:
  创建 agent_runtime.run span。
  创建 context.build span。
  创建 model.invoke span。
  拿到 ModelInvokeResult 后记录 token_usage_logs。

ModelInvokeService:
  暂时不直接写 Trace。
  继续只负责模型调用和返回 ModelInvokeResult。
```

这样做的好处：

```text
不破坏 core.model 的纯模型调用职责。
Token 记录能拿到 tenantId、applicationId、userId、profileId。
后续如果要记录 provider HTTP 细节，再把 model.invoke span 下沉到 ModelInvokeService。
```

模型/工具调用限制的阶段边界：

```text
阶段 2 不做完整 Hook 框架。
阶段 2 继续保留 DefaultAgentRuntimeService.MAX_AGENT_STEPS 和 Profile.maxSteps 预留。
超过最大步骤仍返回 AGENT_MAX_STEPS_EXCEEDED，并写 trace_roots FAILED / trace_spans FAILED。
阶段 4 再扩展 model_call_limit、tool_call_limit、指定工具限制和 END/ERROR 策略。
```

---

## 6. 状态流转

trace_roots：

```text
RUNNING -> SUCCESS
RUNNING -> FAILED
```

trace_spans：

```text
RUNNING -> SUCCESS
RUNNING -> FAILED
```

失败记录：

```text
BizException:
  error_code = BizException.code
  error_message = BizException.message

其他异常:
  error_code = INTERNAL_ERROR
  error_message = exception message
```

入口类型：

```text
INTERNAL_WEB：Web 登录态通过内部 token 调 Gateway。
API_KEY：外部调用通过 API Key 调 Gateway。
```

第一版 API Key 维度记录：

```text
trace_roots.entrypoint = API_KEY
trace_roots.application_id = API Key 绑定的 Application
trace_roots.user_id = API Key 归属用户
trace_roots.metadata.apiKeyPrefix = api_keys.prefix
trace_roots.metadata.clientIp = 请求 IP
trace_roots.metadata.userAgent = 请求 User-Agent
```

后续增强：

```text
api_keys.last_used_at
api_keys.last_used_ip
api_keys.allow_ips
企业增强签名模式（不影响 OpenAI 兼容 Bearer token）
```

---

## 7. DBeaver 验收 SQL

按 traceId 查 root：

```sql
select *
from trace_roots
where trace_id = '替换成你的 traceId';
```

按 traceId 查 span：

```sql
select span_name, span_type, component, status, latency_ms, attributes
from trace_spans
where trace_id = '替换成你的 traceId'
order by started_at asc;
```

按 traceId 查 Token：

```sql
select model_name,
       provider_type,
       prompt_tokens,
       completion_tokens,
       total_tokens,
       estimated,
       created_at
from token_usage_logs
where trace_id = '替换成你的 traceId'
order by created_at asc;
```

按应用统计 Token：

```sql
select application_id,
       sum(prompt_tokens) as prompt_tokens,
       sum(completion_tokens) as completion_tokens,
       sum(total_tokens) as total_tokens
from token_usage_logs
group by application_id
order by total_tokens desc;
```

按模型统计 Token：

```sql
select model_name,
       provider_type,
       count(*) as request_count,
       sum(total_tokens) as total_tokens
from token_usage_logs
group by model_name, provider_type
order by total_tokens desc;
```

---

## 8. 和 SkyWalking / OTLP 的关系

阶段 2 先自研 PostgreSQL Trace：

```text
TraceService -> PostgreSQL
```

后续增强为：

```text
TraceService -> PostgreSQL
TraceExporter -> OTLP / SkyWalking / Jaeger
```

第一轮可以先定义空接口：

```java
public interface TraceExporter {
    void exportRoot(TraceRootDTO root);

    void exportSpan(TraceSpanDTO span);
}
```

默认实现：

```text
NoopTraceExporter
```

这样后面接 SkyWalking 时不需要推翻现有 TraceService。

参考项目取舍：

```text
AgentX-master 的 TraceCollector / TraceContext 可作为当前 TraceService 的设计参考。
AgentX-master 的前端 agent-trace-service.ts 可作为后续 Trace 页面接口参考。
Spring AI Alibaba 的 Hook / Interceptor 链、TraceId 透传、模型/工具调用限制只借鉴思想，不引入 Graph Core / Reactor。
JeecgBoot 的 OpenAPI 调用日志、菜单权限、Trace/监控页面只借鉴后台设计，不引入动态网关路由和低代码平台。
AI-Meeting-main 的 UniversalAiChatHandler 可作为后续 token 级真实流式参考。
smart-cs-multi-agent-main 的 AgentTracer.trace(agentName, method, Supplier<T>) 可作为 Span 包装式写法参考。
当前阶段不引入 Spring AI / WebFlux / Multi-Agent / single-flight，先保持 JDK HttpClient + PostgreSQL Trace。
```

阶段 2 查询接口设计见：

```text
考核设计/接口设计/02-阶段2接口设计.md
```

阶段 2 手工验收步骤见：

```text
实际开发/阶段2/03-阶段2测试指南.md
```

---

## 9. 阶段 2 第一轮验收结论模板

完成后在测试指南中记录：

```text
本次请求 traceId: tr_xxx
trace_roots: 1 条，状态 SUCCESS
trace_spans: n 条，包含 agent_runtime.run、context.build、model.invoke
token_usage_logs: 1 条，total_tokens > 0
mock-chat: estimated = true
DeepSeek: estimated = false，或 provider 未返回 usage 时 estimated = true
```

如果真实模型返回中文在 PowerShell 中乱码，仍按阶段 1 结论处理：这是终端显示问题，不作为后端失败。
