# AgentX 设计借鉴记录

> 参考目录：`D:\study\蓝山最终考核项目\AgentX-master\docs`。本文只记录可借鉴的设计方法和需要补充到本项目的思路，不直接照搬 AgentX 的业务模型。

---

## 1. 参考结论

AgentX 文档中最值得借鉴的不是具体表结构，而是三类设计方法：

```text
1. 对复杂运行时问题单独成文，例如 Token 超限策略。
2. 对 Trace 按执行阶段拆解，明确每个阶段应该记录什么。
3. 对后端开发规范给出可执行的命名、校验、事务和查询约定。
```

不建议直接照搬：

```text
余额、订单、真实计费表。
完整 RAG 文件/文档/版本快照表。
Agent 市场完整审核和版本发布体系。
高可用模型调度网关。
容器模板、工作区等偏产品化能力。
```

原因：这些能力会明显扩大当前考核 MVP 范围。本项目当前重点是：

```text
基础 Agent 闭环
Gateway 治理链
Trace / Token / 脱敏 / 告警
Skill / MCP 示例
前端可视化 Demo
```

---

## 2. 借鉴点一：Token 超限策略单独设计

AgentX 的 `token_overflow_strategy.md` 把 Token 超限拆成：

```text
无策略
滑动窗口
摘要策略
策略配置
触发条件
上下文更新
前端配置
```

这个方法值得借鉴。

本项目已有结论是：上下文 token 预算、裁剪和压缩放在 `core.context`，不放 Gateway。后续建议补一份：

```text
考核设计/上下文与Token策略设计/01-Agent上下文与Token压缩策略.md
```

该文档重点写：

- `AgentContextBuilder` 如何组装上下文。
- System Prompt、Profile Prompt、用户输入、历史消息、长期记忆、Skill/MCP 描述的优先级。
- Token 预算如何分配。
- 超限时采用什么策略。
- 摘要何时写入 `memories`。
- 敏感内容如何避免进入摘要和长期记忆。

阶段 1 推荐策略：

```text
默认使用滑动窗口。
保留最新消息。
长期记忆只做 keywords 简单召回。
摘要压缩可先留接口，阶段 2 或阶段 3 再增强。
```

阶段 2 以后再增强：

```text
摘要策略。
按消息重要性裁剪。
工具描述按启用状态和任务相关性裁剪。
模型维度 token 预算。
```

---

## 3. 借鉴点二：Trace 按执行阶段设计

AgentX 的 Trace 文档按执行阶段拆得很细：

```text
请求接收
环境准备
消息处理
模型调用
工具调用
结果处理
错误恢复
```

这个方法适合本项目阶段 2 的 Gateway 治理链。

本项目可以映射成：

```text
gateway.receive
auth.check
security.scan.request
quota.reserve
context.build
profile.load
memory.recall
tool.list
agent.loop
model.call
skill.call
mcp.call
security.scan.response
message.save
memory.write
token.record
alert.emit
trace.finish
```

每个 Span 应至少记录：

```text
span_id
parent_span_id
name
span_type
status
start_time
end_time
latency_ms
attributes
error_code
error_message
```

需要注意：

- attributes 必须脱敏。
- 用户输入和模型响应不要完整原文进 Trace。
- 工具入参和工具返回需要做截断和脱敏。
- Token 记录是额度数据，不能只靠异步；Trace Span 明细可以异步。

后续建议补一份：

```text
考核设计/Trace与监控设计/01-Agent执行链路Trace设计.md
```

阶段 1 不必完整落库，但应该保留 TraceID 和关键日志。

---

## 4. 借鉴点三：开发规范中的可执行规则

AgentX 的 `develop_document.md` 有几个值得吸收的工程规则：

### 4.1 查询命名区分

建议本项目采用：

```text
getXxx：必须存在，不存在抛业务异常。
findXxx：允许不存在，返回 Optional 或 null。
existsXxx：只返回 boolean。
checkXxx：执行业务检查，不通过抛异常。
```

这样能减少“查不到到底是不是异常”的歧义。

### 4.2 三层校验

建议本项目采用：

```text
Controller 层：格式校验，使用 @Validated。
Application 层：业务规则、权限、状态流转校验。
Domain 层：领域不变条件和策略校验。
```

不要把所有校验堆在 Controller，也不要让 Mapper 查询结果决定业务语义。

### 4.3 事务边界

建议本项目继续坚持：

```text
事务放 application service。
Controller / Filter / DTO 转换层不开事务。
Domain 层不主动开启事务。
Token 预扣、释放、结算要有独立事务边界。
```

### 4.4 JSON 字段处理

本项目大量使用 `jsonb`，例如：

```text
memory_strategy
capabilities
permission_declaration
runtime_config
metadata
attributes
```

建议统一约定：

```text
Entity 中 jsonb 字段优先使用明确的配置对象。
不要到处使用 Map<String, Object>。
MyBatis TypeHandler 统一注册。
简单不稳定的 attributes 可使用 JsonNode。
```

---

## 5. 借鉴点四：Agent 状态与版本

AgentX 对 Agent 状态和版本的设计比较完整：

```text
私有
待审核
已上架
被拒绝
已下架
版本快照
发布
回滚
```

本项目可以借鉴“状态机 + 版本快照”的方法，但不能阶段 1 全做。

本项目当前对应关系：

```text
AgentX Agent
  -> 本项目 Agent Profile

AgentX Agent Version
  -> 本项目后续可扩展 Profile Version
```

阶段 1：

```text
agent_profiles.status 使用 DRAFT / PUBLISHED / DISABLED。
不做完整审核流。
不做 Profile 版本表。
```

阶段 3 或后续增强：

```text
增加 Profile 版本快照。
支持发布、回滚。
支持 Admin 审核公共 Profile。
```

不建议现在加入完整市场、评分、工作区和版本对比功能。

---

## 6. 借鉴点五：SQL 不是设计源头，但能校验遗漏

AgentX 的 `sql/01_init.sql` 覆盖范围很大，包括：

```text
accounts
agents
agent_versions
context
messages
models
providers
orders
products
rules
usage_records
rag_*
```

对本项目的启发：

- 每张表都应该有清晰注释和索引。
- 常用查询维度要提前建索引。
- 配置类表需要状态字段和更新时间。
- 运行记录表要围绕用户、会话、时间、trace 建索引。

但不能直接导入这些 SQL。原因：

- AgentX 使用真实余额计费，本项目 MVP 是 Token 配额，不做支付。
- AgentX RAG 表很多，本项目 MVP 只做长期记忆，不做完整知识库。
- AgentX Agent 市场和版本更产品化，本项目先做 Profile MVP。

---

## 7. 建议补充到本项目的文档

按收益排序，建议补：

```text
1. 考核设计/接口设计/01-阶段1接口设计.md
2. 考核设计/开发计划/01-阶段1任务拆解与验收计划.md
3. 考核设计/上下文与Token策略设计/01-Agent上下文与Token压缩策略.md
4. 考核设计/Trace与监控设计/01-Agent执行链路Trace设计.md
```

其中 1、2 是开工前最必要；3、4 可以在阶段 1 主链路开始实现前补，不必一开始写得过深。

---

## 8. 已吸收到当前工程约定的规则

已建议补入 `考核设计/技术选型与工程约定/01-技术选型与工程约定.md`：

```text
get/find/exists/check 查询命名规则。
Controller / Application / Domain 三层校验规则。
jsonb 字段对象化与 TypeHandler 约定。
事务放 application service，Filter/Controller 不开事务。
```

---

## 9. 最终判断

AgentX 的参考价值主要在“文档拆法”和“复杂点独立成策略文档”，不是具体业务功能。

本项目应该吸收它的方法：

```text
复杂运行时问题单独设计。
执行链路按阶段拆 Trace。
开发规范写成可检查的规则。
数据库表用索引和注释反推查询场景。
```

但继续坚持本项目自己的边界：

```text
不做真实余额支付。
不提前做完整 RAG。
不提前做完整 Agent 市场。
不让参考项目的产品复杂度拖垮考核 MVP。
```
