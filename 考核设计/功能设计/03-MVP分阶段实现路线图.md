# MVP 实现路线图

## 1. 路线图目标

本路线图用于控制实现范围，避免直接按照完整功能清单一次性开发 200 多个功能点。

核心原则：

```text
先跑通主链路，再补高分项。
先保证可演示，再追求完整生产化。
先真实实现核心能力，外围系统允许 mock 或轻量适配。
```

最终目标是形成一条稳定 Demo 链路：

```text
用户请求
-> AI Infra Gateway
-> Trace / 脱敏 / Token 配额
-> Agent Runtime
-> Skill / MCP 调用
-> 模型回复
-> Trace / Token 记录
-> 异常飞书告警
-> 前端可视化展示
```

## 2. 阶段总览

建议分 5 个阶段实现。

| 阶段 | 目标 | 结果 |
|------|------|------|
| 阶段 1 | 项目 1 基础 Agent 闭环 | 能对话、能切模型、能调 Skill/MCP、能保存记忆；先建立 Gateway 空壳保持最终路由 |
| 阶段 2 | 项目 3 AI Infra 网关治理 | OpenAI 兼容入口、Trace、Token、脱敏、飞书告警跑通 |
| 阶段 3 | 前端控制台 MVP | 能配置、能对话、能看 Trace/Token/Skill |
| 阶段 4 | Agent Team 高分项 | Planner / Executor / Reviewer 协作和 Mermaid 展示 |
| 阶段 5 | 飞书机器人扩展 | 基础指令、告警接口、Git/CR 和 DevOps mock 演示 |

## 3. 阶段 1：项目 1 基础 Agent 闭环

### 3.1 必做功能

先实现基础 Agent，不做 Team。

功能：

- Gateway 空壳：先保留 `Web -> Gateway -> Core` 调用路径，阶段 1 只做请求转发，不做完整治理链。
- 模型供应商配置。
- Profile 管理。
- 基础 Agent Runtime。
- 内置 Skill 调用。
- 至少 1 个 MCP Client 示例工具。
- 短期记忆。
- 长期记忆持久化。
- 对话接口。
- SSE 流式输出。

### 3.2 Skill 范围

MVP 只做 3 个内置 Skill：

```text
weather
calculator
search
```

其中：

- `calculator` 必须真实实现。
- `weather` 可以接免费天气 API，也可以先 mock 固定城市结果。
- `search` 可以接搜索 API，也可以 mock 返回结构化搜索结果。

MCP 示例至少做 1 个：

```text
filesystem 只读工具，或 readonly-sql 查询工具
```

MVP 阶段 MCP 只要求“能发现和调用一个外部工具”，不要求把平台 Skill 暴露成 MCP Server。

### 3.3 暂缓内容

本阶段暂缓：

- Jar Skill 热加载完整能力。
- 用户上传 Jar Skill。
- MCP Server 暴露平台 Skill。
- Planner / Executor / Reviewer。
- 完整权限管理页面。

### 3.4 阶段完成标准

完成后可以演示：

```text
选择一个 Agent Profile
输入问题
Agent 调用 weather / calculator / search
Agent 至少调用 1 个 MCP 示例工具
返回最终答案
历史对话可以查询
长期记忆有持久化记录
浏览器 AI 请求已按 Web -> Gateway -> Core 路径进入，只是 Gateway 暂不启用完整治理链
```

## 4. 阶段 2：项目 3 AI Infra 网关治理

### 4.1 必做功能

在阶段 1 的 Gateway 空壳基础上补全治理能力。

功能：

- OpenAI 兼容接口：`POST /v1/chat/completions`
- API Key 鉴权。
- tenant_id / app_id / user_id 上下文。
- TraceID 生成。
- Span 记录。
- 本地 Trace 查询接口。
- Token 统计。
- Token 展示接口：当前用量、调用明细、简单 TOP 消费者。
- 用户级 / 应用级 Token 配额。
- 数据库事务或 Redis 原子预扣和修正。
- 敏感数据检测。
- 脱敏模式。
- 阻断模式。
- 飞书最小告警接口调用：优先调用真实 feishu-bot 内部告警接口，未接入飞书时允许 mock 接口记录告警。

### 4.2 Trace 范围

MVP 至少记录：

```text
gateway.receive
auth.check
sensitive.scan.request
quota.check
profile.load
memory.load
agent.run
skill.call
llm.call
sensitive.scan.response
token.record
trace.finish
```

### 4.3 Token 策略

实现优先级：

```text
1. 模型返回 usage，直接使用。
2. 模型不返回 usage，用简单 tokenizer 或字符估算。
3. 估算结果标记 estimated=true。
```

配额策略：

```text
请求前预估 Token 并预扣。
请求完成后按真实 usage 修正。
请求失败后释放预扣额度。
```

### 4.4 敏感数据范围

MVP 至少扫描：

- 用户请求。
- Skill 入参。
- Skill 返回。
- LLM 响应。
- 长期记忆写入内容。
- Trace / 日志存储内容。

### 4.5 飞书告警范围

MVP 告警只做 3 类：

```text
模型调用失败
Token 超限
敏感数据阻断
```

告警内容包含：

- TraceID。
- app_id。
- user_id。
- 告警类型。
- 告警级别。
- 建议动作。

### 4.6 暂缓内容

本阶段暂缓：

- 错误率统计窗口。
- 告警升级策略。
- 告警静默复杂规则。
- Jaeger 深度查询页面。
- 多模型自动降级。
- 复杂 Token 趋势图、多维筛选、成本折算。

### 4.7 阶段完成标准

完成后可以演示：

```text
通过 /v1/chat/completions 调用 Agent
响应 Header 返回 X-Trace-Id
Trace 查询接口能看到完整链路
Token 用量被记录
Token 当前用量、调用明细、简单 TOP 可查询
超限请求被拦截
手机号/邮箱能脱敏或阻断
异常能发飞书告警
```

## 5. 阶段 3：前端控制台 MVP

### 5.1 页面范围

不要一开始做完整 19 个页面，先合并成 10 个 MVP 页面。

MVP 页面：

```text
1. 登录 / 注册页
2. Dashboard
3. 模型配置
4. Agent/Profile 配置
5. Skill 市场
6. Agent 对话页
7. Trace 详情页
8. Token / 告警页
9. API Key / Application 管理页
10. 飞书配置页
```

### 5.2 页面重点

对话页必须能展示：

- 当前 Agent。
- 当前模型。
- 本次启用 Skill。
- SSE 输出。
- 工具调用过程。
- TraceID。
- Token 用量。

Trace 页必须能展示：

- Span 时间线。
- 模型调用。
- Skill 调用。
- 错误信息。

Token / 告警页必须能展示：

- 当前用户 / 应用 Token 用量。
- 调用明细列表。
- 简单 TOP 消费者。
- 告警记录列表。

### 5.3 暂缓内容

本阶段暂缓：

- 完整 RBAC 用户管理 UI。
- 复杂租户管理 UI。
- 高级图表。
- 复杂 Token 趋势图和多维筛选。
- 全量告警规则配置。
- 完整 Skill 审核页面。

### 5.4 阶段完成标准

完成后可以演示：

```text
管理端配置模型和 Profile
用户端选择 Agent 发起对话
对话过程可视化
Trace 页面查看链路
Token 页面查看用量
飞书配置页面设置告警地址
用户可创建 Application、生成或吊销 API Key
```

## 6. 阶段 4：Agent Team 高分项

### 6.1 实现范围

只围绕一个稳定 Demo 场景实现：

```text
策划一场活动
```

使用 Skill：

```text
weather
search
calculator
```

角色：

```text
Planner：拆任务
Executor：调用 Skill 执行
Reviewer：审查结果
Orchestrator：仲裁和汇总
```

### 6.2 必做能力

- Planner 输出 TaskPlan JSON。
- TaskPlan Schema 校验。
- Executor 按 dependsOn 执行任务。
- ExecutionResult 记录。
- Reviewer 输出 ReviewResult。
- Reviewer 不通过时重试一次。
- 重试失败后 Orchestrator 仲裁。
- Mermaid 流程图展示。

### 6.3 容错策略

Planner 输出非法：

```text
第一次要求模型修正。
第二次降级为预置单任务计划。
```

Skill 调用失败：

```text
返回 failed ExecutionResult。
Reviewer 判断是否可接受。
Orchestrator 决定返回部分结果或失败原因。
```

Reviewer 反复不通过：

```text
最多重试一次。
仍失败则返回当前最优结果和风险说明。
```

### 6.4 暂缓内容

本阶段暂缓：

- 多个 Team 模板。
- 任意复杂任务动态协作。
- 多 Agent 并发执行。
- 复杂争议仲裁。

### 6.5 阶段完成标准

完成后可以演示：

```text
用户输入活动策划请求
Planner 输出任务计划
Executor 调 weather/search/calculator
Reviewer 审查通过
页面展示 Mermaid 协作流程
最终返回完整活动方案
Trace 中能看到三个 Agent 的 Span
```

## 7. 阶段 5：飞书机器人扩展

### 7.1 基础指令

必须完成：

```text
/weather
/schedule
/group
/search
/translate
/help
```

其中如果真实飞书 API 配置成本较高，可以采用：

```text
/weather：真实或 mock
/schedule：真实飞书日历优先，失败时 mock
/group：可 mock 创建结果
/search：mock 文档搜索结果
/translate：调用模型或简单翻译 API
/help：真实动态指令列表
```

### 7.2 告警接口

必须完成一个内部接口：

```text
POST /internal/alerts/feishu
```

项目 3 通过 HTTP 调用该接口发送告警。

### 7.3 Git / CR 演示

重点做：

- `/gitlog`
- `/gitdiff`
- `/review`
- Webhook 接收。
- CR 报告生成。
- 飞书卡片推送。

可以使用本地示例仓库或 mock diff。

### 7.4 DevOps 工具

以下功能允许 mock adapter：

```text
/deploy
/jira
/monitor
```

重点展示：

- 指令框架可扩展。
- 权限控制。
- 异步任务状态。
- 飞书卡片结果。

### 7.5 阶段完成标准

完成后可以演示：

```text
飞书群中执行基础指令
收到 AI Infra 告警
手动触发 /review
CR 报告推送到群
/deploy 返回异步任务状态
```

## 8. 总体容错策略

### 8.1 外部依赖容错

所有外部依赖都必须有降级方案：

| 外部依赖 | 降级方式 |
|------|------|
| LLM 调用失败 | 返回错误 Span，触发告警，可切换 mock 模型 |
| 天气 API 失败 | 返回 mock 天气 |
| 搜索 API 失败 | 返回 mock 搜索结果 |
| 飞书 API 失败 | 写入本地告警日志 |
| Git/Jenkins/Jira/Prometheus 不可用 | 使用 mock adapter |
| Jaeger 不可用 | 仍保留本地 Trace 查询 |

### 8.2 LLM 输出容错

涉及结构化输出的地方必须有兜底：

```text
JSON 解析失败 -> 要求模型修正
二次失败 -> 使用预置模板或降级基础 Agent
Schema 校验失败 -> 记录 TraceEvent
```

### 8.3 Demo 容错

Demo 场景必须固定：

```text
活动策划 Team Demo
Token 超限 Demo
敏感数据脱敏 / 阻断 Demo
飞书告警 Demo
代码审查 Demo
```

每个 Demo 都准备一套稳定输入和 mock 数据，避免现场依赖外部系统。

## 9. 最终推荐开发顺序

按下面顺序开发，容错率最高：

```text
1. 数据库表结构和基础配置
2. 模型配置和 ChatClient 调用
3. Profile 加载
4. 内置 Skill 调用
5. 基础 Agent 对话闭环
6. 记忆系统
7. Gateway + OpenAI 兼容接口
8. Trace / Token / 脱敏
9. 飞书告警接口
10. 前端 MVP
11. Agent Team
12. 飞书机器人扩展
13. Docker Compose 和 README
```

## 10. 判断是否可以进入下一阶段

每个阶段结束时，只看是否能完成对应 Demo，不追求功能全量完成。

阶段推进标准：

```text
阶段 1：Postman 能通过 Web -> Gateway -> Core 路径跑通 Agent 对话、Skill 调用、至少 1 个 MCP 示例工具。
阶段 2：OpenAI 接口能返回 TraceID，Token/脱敏/告警有效。
阶段 3：前端能完成配置、对话、Trace 查看。
阶段 4：Team Demo 稳定跑通。
阶段 5：飞书群能完成指令和告警演示。
```

如果某阶段卡住，优先降级或 mock，不要阻塞整条主链路。
