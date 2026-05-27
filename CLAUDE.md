# CLAUDE.md

## 项目定位

三个考核项目，递进关系：

```
项目1 Agent 平台（地基）
  ├── 项目3 基于它加 Gateway 层（全链路 Trace + Token 配额 + 脱敏 + 告警）
  └── 项目3 通过 HTTP 调项目2飞书接口发告警
项目2 飞书机器人（独立服务，有自己的 DB，不依赖项目1代码）
```

## 技术选型

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.4.x（当前 POM 固定 3.4.13）+ Java 17+ |
| 权限 | Spring Security + JWT + RBAC + API Key |
| LLM 调用 | Spring AI ChatClient / OpenAI 兼容 Client 封装在 core.model，自研 ReAct while 循环 |
| 数据库 | PostgreSQL |
| ORM | MyBatis-Plus |
| Skill 热加载 | URLClassLoader + 双亲委派反转 |
| Agent 通信 | BlockingQueue 内存通道 |
| 构建 | Maven 多模块 |
| 前端 | React + Vite + Tailwind CSS + shadcn/ui |
| 容器化 | Docker Compose（项目3 交付） |

## MVP 暂缓 / 不优先实现清单

以下内容不是永久不做，而是当前 MVP 阶段不优先。实现时先保证主链路和考核硬性要求稳定跑通，再逐步补全增强能力。

- 极简登录页（用户名+密码）做在 MVP 里；找回密码、美化登录页、OAuth 第三方登录等暂缓。
- 用户通过登录页注册或登录；API Key 绑定到 Application（应用），用户创建应用时自动生成，一个用户可有多个应用，每个应用一个 Key。
- 复杂人工审核流暂缓；MVP 阶段 Skill 上传后先自动校验，通过后可用，保留 status 字段，后续补 Admin 审核、发布、回滚流程。
- 支付/真实计费暂缓；注册充值页面暂缓；MVP 先做 Token 配额（Admin 后台配置日/月额度），后续可扩展为真实成本和充值计费。
- 完整单元测试体系暂缓；MVP 仍需做接口验证、手工验证和 Demo 验证，后续补充单元测试和集成测试。
- 完整 RAG 知识库暂缓；MVP 必须体现长期记忆持久化，后续再扩展向量库和知识库问答。
- 普通 User 上传任意 Jar Skill 暂缓；MVP 优先支持普通 User 上传配置型 Skill，Jar Skill 主要由 Developer/Admin 上传，后续再做用户 Jar 沙箱和审核。
- Team Profile 配置暂缓到阶段 4；MVP 的 Developer 只配置单 Agent Profile。

## 角色体系

| 角色 | 职责 |
|------|------|
| Admin | 管理模型供应商、全局 Skill 校验/上架/下架、MCP Server、全局安全策略、Token 配额、飞书告警、用户管理 |
| Developer | 创建垂直领域 Agent Profile、配置领域 Prompt 和 Skill、上传领域 Skill、查看自己应用的 Trace/Token |
| User | 选择 Agent 发起对话、勾选可用 Skill、上传个人私有 Skill、保存个人偏好、查看自己的 Trace/Token/历史 |

User 不能：修改公共 Profile、修改核心 System Prompt、启用未授权高危 Skill、绕过安全检查。

权限落地方式：Spring Security + JWT + API Key。登录注册页面做极简版（用户名+密码）。用户登录后在个人中心创建 Application，每个应用自动生成一个 API Key，可创建多个应用、单独吊销。Admin 后台可手动创建用户作为兜底。

系统保留 tenant_id / app_id / user_id，满足多租户隔离、应用级配额、用户级配额和 Trace/Token 数据隔离。

## 双通道入口设计

用户注册登录后，通过两个入口访问平台。最终路由按“请求类型”分流，而不是按“角色”分流：

- AI 执行请求统一进入 Gateway 治理链，再进入 core Agent Runtime。
- 管理、配置、统计、查询类请求走 Web -> Core，不经过 Gateway。
- 两类 AI 入口共用同一 Agent Runtime 和同一 Token 配额。
- 阶段 1 先建立 Gateway 空壳，保持 `Web -> Gateway -> Core` 路由；阶段 2 再补 Trace、Token、脱敏、告警治理链。

```
用户注册（极简页：用户名+密码）
  → 登录拿 JWT
  → 创建 Application → 自动生成 API Key（sk- 开头）
                         一个用户可创建多个应用，每个应用一个 Key，可单独吊销
  │
  ├── 通道 A：JWT + 浏览器
  │    鉴权：登录后浏览器自动带 JWT
  │    用途1：人在网页上选 Agent、勾 Skill、对话
  │    体验：SSE 实时流（thinking/action/observation/message/done）
  │    路由：AI 对话/测试调用 → Web → Gateway → Core
  │    用途2：管理端/个人中心/Profile/Skill/Trace/Token 查询
  │    路由：管理配置查询 → Web → Core
  │
  └── 通道 B：API Key + 代码/工具
       鉴权：Authorization: Bearer sk-xxx
       用途：开发者在自己的代码/脚本/工具里调 /v1/chat/completions
       体验：OpenAI 兼容格式，现有生态工具直接接入
       路由：外部 AI 调用 → Gateway → Core
  │
  └── AI 调用统一经过 Gateway 治理链
       │
       ├── agent_mode=agent（默认）→ 完整链路：Profile + Skill + ReAct + 记忆
       └── agent_mode=none（透传）→ 纯模型转发，不走 Agent
       │
       └── 同一 Token 配额扣减 → 超限返回 HTTP 429
```

Application 与 API Key 的关系：Key 绑定应用而非直接绑用户。用户创建应用时自动生成 Key，不同应用用不同 Key，Token 消耗按应用分别统计。Key 泄露只吊销一个，不影响其他应用。

Agent 模式 vs 透传模式：请求参数 `agent_mode` 控制。`agent` 走完整 Agent Runtime（Profile+Skill+ReAct），`none` 纯转发模型、不做 Agent 处理。默认 `agent`。浏览器端 AI 对话/测试请求始终走 Agent 模式；管理配置查询请求不进入 Agent Runtime。

支付与配额：MVP 不做真实支付和充值页面。Token 配额由 Admin 后台按用户/应用配置日限额和月限额。超限返回 429，配额用尽触发飞书告警。后续可扩展真实充值计费。

## 使用模式

基础 Agent 是必做能力，Team 模式是高分项。

- **通用 Agent**：平台内置通用 System Prompt，用户自选全局 Skill + 个人 Skill
- **垂直领域 Agent**：Developer 预配置领域 Profile（领域 Prompt + 领域 Skill），User 开箱即用
- **Team 执行链路**：复杂任务可启用 Planner→Executor→Reviewer；基础 Agent 和 Team 共用模型调用、Skill 合成、记忆、Trace、Token、脱敏和告警能力

MVP 的 Developer = 配单 Agent Profile；阶段 4 的 Developer = 在 Profile 上启用 Team 模式并配置团队策略。

最终 Prompt = 核心 System Prompt（平台锁定）+ Profile 补充 Prompt（可配置）+ 当前任务上下文（运行时注入）

## 11 个功能域

```
0. 角色与权限体系        → Spring Security + JWT + API Key
1. 模型与统一调用入口     → 多供应商管理 + 多模态能力标记 + 运行时切换 + OpenAI 兼容 API
2. Agent Profile 体系    → 通用/垂直 Profile 管理 + Prompt 分层
3. Skill / Tool 市场      → 三层 Skill（全局/垂直/个人）+ 配置型/代码型 Skill + 热加载 + 状态流转
4. MCP 工具体系          → MCP Client + 至少1个示例工具，MCP Server 为增强项
5. Agent Runtime 执行引擎 → 基础 Agent + ReAct while 循环 + Team 协作（Planner/Executor/Reviewer）
6. 记忆系统              → 短期窗口 + 长期持久化 + 记忆策略（DISABLED/READ_ONLY/READ_WRITE/SESSION_ONLY）
7. AI Infra 治理能力     → Trace + Token 配额 + 敏感数据脱敏 + 飞书告警联动
8. 飞书超级助手机器人     → 6个基础指令 + Git/CR/CI 扩展 + 可扩展指令框架
9. 前端控制台            → Admin 端（管理）+ User 端（对话+可视化）
10. 部署与交付           → Docker Compose + README + 架构图 + Demo 脚本 + 飞书测试群
```

## 实现顺序

具体开发落地顺序以 `考核设计/功能设计/03-MVP分阶段实现路线图.md` 为准，`考核设计/功能设计/01-项目完整功能清单.md` 只用于确认功能全集，不代表一次性全部实现。

参考项目分析文档：

- `考核设计/参考项目分析/01-AgentX设计借鉴记录.md`
- `考核设计/参考项目分析/02-SpringAIAlibaba设计借鉴记录.md`
- `考核设计/参考项目分析/03-InterviewGuide设计借鉴记录.md`
- `考核设计/参考项目分析/04-JeecgBoot设计借鉴记录.md`
- `考核设计/参考项目分析/05-emo设计借鉴记录.md`
- `考核设计/参考项目分析/06-MVPClaudeCode设计借鉴记录.md`

阶段 2/3/4/5 设计和开发前，应按任务类型回看对应参考记录。参考项目只借鉴设计方法、工程经验和取舍边界，不直接照搬其技术栈、运行时框架或复杂产品能力。

阶段 2 开发前重点参考 emo 的 LLM Provider Registry 思路；Pipeline 只作为阶段 2 后或阶段 3 前的重构方向，不作为阶段 2 前置任务。

阶段 4 Agent Team 和后续 Agent Runtime 增强前，重点参考 MVP Claude Code 的 token 级流式输出、ToolDispatcher/ToolRegistry、子 Agent 工具隔离、上下文压缩和高危工具确认思路。该参考项目只作为 Agent Harness 设计来源，不引入 LangChain4j、Picocli、本地 File/Bash 裸工具能力或文件型 Memory。

阶段 3 前端开发采用 `agent-platform-frontend` 独立工程，技术栈固定为 React + Vite + TypeScript + Tailwind CSS + shadcn/ui + ECharts。本地开发阶段不引入 nginx，先使用 Vite dev server 代理 `/api` 到 Web 后端；nginx 放到最终 Docker Compose / 交付阶段再补。

阶段 3 涉及审美和页面体验选择时，必须先给用户 2-3 个明确方案并等待确认，再写页面代码。需要确认的内容包括但不限于：整体视觉风格、导航布局、Dashboard 信息密度、对话页布局、Trace 时间线表现、图表风格、动效强度、颜色主题、登录页视觉方向。接口封装、类型定义、目录搭建、API Client、路由骨架等非审美基础工程可以直接按既定技术栈推进。

阶段 1 开发时必须同步对照：`考核设计/开发检查清单/01-阶段1开发检查清单.md`。每完成一个小阶段，都按检查清单回查一次，避免忘记 Gateway、ArchUnit、SSE、Context、Quota 等关键护栏。

阶段 1 实际开发还必须同步对照：`实际开发/阶段1/02-阶段1执行顺序.md`。当前代码已完成 Step 15 阶段 1 总体验收，并通过全量 `mvn.cmd -s .mvn/settings.xml test`；当前全量测试数 79 个。阶段 1 手工验收步骤见 `实际开发/阶段1/03-阶段1测试指南.md`。

开发协作节奏：

- 每开始一个 Step，先说明本 Step 要实现的接口、核心类、测试范围和关键设计点，确认方向后再写代码。
- 中间 DTO、Mapper、测试修补等小改动可以连续完成，不要求逐文件确认。
- 每完成一个 Step，必须运行对应测试和全量 `mvn test`，再总结修改文件、验证结果、遗留问题。
- 用户在每个 Step 完成后集中审查代码；不要等阶段 1 全部写完才审查，也不需要每个小文件都打断审查。
- Spring AI 封装、模型 API Key 加密、Agent 循环事件、SSE 事件格式、Context token 裁剪等关键设计点，必须在实现前单独说明。

Git 提交与标签约定：

- Step 级别进度只写清楚 commit message，不再为每个 Step 打 tag。
- 阶段级别完成后再打 tag，例如 `phase-1-complete`、`phase-2-complete`。
- MVP 可演示版本完成后打 `mvp-demo`。
- 已存在的 `phase-1`、`phase-1-step-11` 标签保留，不再调整。

必须按阶段推进：

1. 阶段 1：项目 1 基础 Agent 闭环
2. 阶段 2：项目 3 AI Infra 网关治理
3. 阶段 3：前端控制台 MVP
4. 阶段 4：Agent Team 高分项
5. 阶段 5：飞书机器人扩展

每个阶段只以该阶段 Demo 能否稳定跑通作为完成标准。遇到外部依赖或增强功能阻塞时，优先 mock、降级或暂缓，不允许为了可选增强阻塞主链路。

## 架构与模块设计约束

模块拆分、数据库访问边界、代码实现必须参考：`考核设计/架构与模块设计/01-架构与模块设计原则.md`

具体工程目录、Java 包结构、表归属与 Entity/Mapper 位置，以 `考核设计/架构与模块设计/04-模块目录与包结构设计.md` 为准。

技术选型、工程约定、Flyway、错误码、SSE、ArchUnit 和测试验证规则，以 `考核设计/技术选型与工程约定/01-技术选型与工程约定.md` 为准。

阶段 1 URL、请求/响应、SSE 事件、Web->Gateway 内部接口和 Core Command 字段，以 `考核设计/接口设计/01-阶段1接口设计.md` 为准。

强约束：

- MVP 采用少量工程单元：common/core/web/gateway/frontend/feishu-bot；其中 frontend 是前端工程，feishu-bot 是独立服务，core 内部保留逻辑包边界。
- 禁止跨模块直接查表，每张表有唯一归属模块，其他模块只能通过接口访问。
- DTO 和 Entity 必须分离，common 不放业务 Entity / DTO。
- Web（JWT 浏览器入口）与 Gateway（统一 AI 调用治理入口）必须拆成两个模块；Web 的 AI 对话/测试请求转发到 Gateway，管理配置查询直接调用 Core。
- Web 转发 Gateway 走 HTTP 内部接口，不做代码强依赖；Gateway 必须校验内部服务 Token 或签名 Header。
- Gateway 只做鉴权、Trace、Token、脱敏、告警、协议转换和调用适配，不组装 Agent 执行上下文。
- Agent 执行上下文组装、上下文 token 预算、压缩和裁剪放在 `core.context`。
- Spring AI 只作为模型调用适配层，保留请求构建、响应解析、usage 读取、流式 token 适配和多供应商 ChatClient 封装；不接管 ReAct 循环、Skill/MCP 执行或多 Agent 调度。
- GatewayContext 只保存请求治理信息，必须显式转换成 `AgentRunCommand` / `ModelInvokeCommand`，core 不读取 Gateway ThreadLocal。
- `web/gateway` 禁止访问任何 `core.*.mapper`；`core` 内部跨业务包只能访问 `api/dto/command`，禁止访问其他包的 `entity/mapper/internal`。
- 生成 Entity / Mapper 后必须立刻加入 ArchUnit 或等价架构测试，防止包边界只靠人工纪律。
- Gateway 空壳完成后必须先验证 `web -> gateway` 固定 SSE 流透传，确认不缓冲、不超时、错误事件可传递。
- SSE 基于 Spring MVC，优先使用 `SseEmitter` 或 `StreamingResponseBody`；不引入 WebFlux / Flux 作为阶段 1 技术栈。
- Token 配额、预扣、结算必须同步或可靠写入，不可异步丢失。
- Token 预扣必须使用 version CAS 或 `SELECT ... FOR UPDATE` 行锁；释放和结算必须按 `trace_id + status + version` 幂等更新。
- Token 基础看板（当前用量、调用明细、简单 TOP）属于 MVP；复杂趋势图、多维筛选和成本折算属于增强。
- Trace Span 明细、趋势统计、告警通知可以异步处理。
- 飞书告警失败不能影响用户 AI 响应，只能更新告警通知状态并重试。
- 阶段 2 只要求 feishu-bot 最小告警接口或 mock 告警接口；完整飞书指令、Git/CR/DevOps 放阶段 5。
- 项目 1 阶段必须包含至少 1 个 MCP Client 示例工具，MCP Server 暴露平台 Skill 是增强项。
- 外部依赖必须可降级、可 Mock，不能因为外部不可用拖垮主链路。
- 每解决一个设计问题或关键决策，同步追加到 `考核设计/问题记录/01-考核设计问题台账.md`，格式：问题/核心思路/核心方案三段式，已有的话题不重复。

## 数据库设计约束

正式数据库模型以 `考核设计/数据库模型设计/01-数据库模型设计.md` 为准。
数据库关系图见 `考核设计/数据库模型设计/02-数据库关系图.drawio`。

强约束：

- 平台主库使用 PostgreSQL，MVP 也保留 `tenant_id` / `application_id` 等隔离字段。
- `web` 和 `gateway` 不拥有业务表，只做入口、鉴权、协议转换和治理链。
- 平台核心业务表归 `core` 内部逻辑包管理；`feishu-bot` 使用独立配置库，不直接访问平台核心业务表。
- API Key 绑定 Application，不直接绑定 User；只保存 hash，明文只在创建时展示一次。
- Token 配额、预扣、结算必须可靠写入；Trace Root 同步创建，Trace Span 明细允许异步批量落库。
- Entity / Mapper / Service 必须按表归属包落地，禁止为了方便在其他包直接查表。
- Entity 中所有 `JsonNode` 字段必须显式声明 `JsonNodeTypeHandler`，对应 `@TableName` 必须启用 `autoResultMap = true`；新增 jsonb 字段后必须更新 `JsonNodeMappingTest`。
- 写 Entity、Mapper、Service、DTO 或初始化 SQL 前，必须先查阅正式数据库模型文档，不要凭记忆补字段。

## 安全策略（贯穿全域）

| 策略项 | 说明 |
|------|------|
| 敏感数据规则 | 手机号/身份证/邮箱/银行卡/IP/自定义关键词正则 |
| 调用频率限制 | 单个用户每分钟/每小时最大调用次数 |
| 高危 Skill 确认 | 部署/创建分支等 Skill 调用前需二次确认 |
| Skill 黑白名单 | 可按 Profile 禁止特定 Skill |
| 单次最大 Token | 超限截断或拒绝 |
| 脱敏/阻断模式 | 命中敏感数据后：占位符替代继续 或 直接拒绝 |

全局安全策略由 Admin 配置，Profile 可覆盖部分规则（只能收紧不能放宽）。

## 项目1 与项目2 的 Skill/指令关系

项目2 飞书指令（/weather、/translate 等）有自己独立的轻量实现，不依赖项目1的 Skill 引擎。功能上重叠但代码不依赖。唯一跨项目调用：项目3 Gateway → 项目2 内部接口发飞书告警。

## Agent Team 关键约束

- Planner：只拆任务，不调工具，不生成最终答案
- Executor：只执行分配的子任务，调用授权 Skill/Tool，不重新规划
- Reviewer：只审查结果，不规划不执行
- Skill **不**分别绑给三个角色，统一绑到任务/领域/用户，仅 Executor 拿到调用权限
- 每个角色的输出必须通过 JSON Schema 校验（TaskPlan / ExecutionResult / ReviewResult）
- Planner 规划失败：首次要求修正，二次失败降级为单任务
- Reviewer 不通过：先让 Executor 重试；仍失败则由 Orchestrator 仲裁，决定继续重试、降级基础 Agent、返回部分结果或失败原因

## 网关拦截器链（项目3）

```
TenantFilter → TraceFilter → SensitiveDataFilter → QuotaFilter
→ RuntimeInvokeFilter → SensitiveResponseFilter → TokenRecordFilter → AlertFilter
```

GatewayContext 用 ThreadLocal 贯穿，请求结束 finally 清理。

## 关键设计备忘

- SSE 事件格式：`{type: thinking|action|observation|message|done|error, step, ...}`
- Token 截断：从最新消息往前数 token，超阈值截断，异步摘要存长期记忆
- Token 配额：请求前通过数据库事务或 Redis 原子预扣，完成后按真实 usage 可靠修正，失败释放预扣额度
- Token 来源：优先使用模型 usage；模型不返回时 tokenizer 估算，并标记 estimated=true
- 模型切换：从 Profile 读 model_provider_id → 查配置 → 动态创建 ChatClient
- 模型调用阶段先 Mock First：`mock-chat` 必须能无外部网络/API Key 跑通主链路；真实 Spring AI/OpenAI 调用后续只在 `core.model` 内部替换实现。
- 模型供应商密钥通过 `core.support.security.SecretEncryptor` 统一处理；阶段 1 实现只能作为开发占位，后续可替换 AES/KMS，不改变业务接口。
- Profile 绑定 Skill/MCP 时，`core.profile` 只能调用 `core.skill.api.SkillQueryService` / `core.mcp.api.McpToolQueryService` 校验可绑定性，不能直接访问 `skill.mapper` 或 `mcp.mapper`。
- Skill 状态流转：UPLOADED → VALIDATING → INSTALLED → ENABLED → LOADED（可到 DISABLED/FAILED/UNINSTALLED）
- Skill 安全：配置型 Skill 普通 User 可上传；代码型 Jar Skill 主要由 Developer/Admin 上传，ClassLoader 只做依赖隔离，不等价于安全沙箱
- 敏感数据不入长期记忆原文
- 敏感扫描范围：用户请求、Planner 输出、工具入参、Skill/MCP 返回、Reviewer 输出、最终响应、记忆写入、Trace/日志
- OpenAI 兼容接口 TraceID：严格兼容模式用 `X-Trace-Id` 响应头，增强模式可在 body 额外返回 trace_id
- 前端 User 端 9 个主要页面 + Admin 端 10 个管理页面
