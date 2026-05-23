# JeecgBoot 设计借鉴记录

> 参考目录：`D:\study\蓝山最终考核项目\JeecgBoot-main`。本文只记录对本项目有参考价值的后台平台、权限、多租户、OpenAPI、低代码和 AI 应用设计方法，不直接照搬 JeecgBoot 的 Shiro、低代码引擎、报表、大屏、工作流和庞大系统表。

---

## 1. 参考结论

JeecgBoot 是企业级 AI 低代码平台，最值得本项目借鉴的不是具体代码，而是后台平台能力的组织方式：

```text
1. RBAC + 菜单权限 + 按钮权限 + 数据权限的分层权限模型。
2. 多租户、租户套餐、用户-租户关系的后台管理思路。
3. OpenAPI 的 AK/SK、IP 白名单、接口授权、调用日志。
4. 前端后台动态菜单、动态路由、权限码和按钮级权限控制。
5. AI 应用平台中模型、知识库、MCP、流程编排、应用调试的管理页面划分。
6. 低代码生成的“生成代码 + 菜单权限 SQL + 手工合并”方法。
```

对当前项目的直接结论：

```text
阶段 2 可借鉴 OpenAPI 调用日志、Trace/监控页面思路，但不要引入 Jeecg 的网关动态路由。
阶段 3 前端控制台可借鉴菜单权限、按钮权限、管理页面信息架构。
阶段 4/5 可借鉴 AI 应用配置、MCP 管理、消息中心和告警通知页面。
```

不建议直接引入：

```text
JeecgBoot 的 Shiro 权限框架。
完整 Online 表单、代码生成器、报表、大屏、Flowable 工作流。
JimuReport/JimuBI。
Nacos/Gateway 动态路由方案。
复杂多数据库兼容层。
庞大的系统基础表。
```

原因：

```text
本项目是 Agent 平台考核项目，不是通用低代码平台。
当前技术路线已经确定为 Spring Security + JWT + API Key + MyBatis-Plus + PostgreSQL。
JeecgBoot 的低代码能力很强，但引入会显著扩大范围，削弱 Agent/Gateway/Trace 主线。
```

---

## 2. 项目结构参考

JeecgBoot 后端主要分为：

```text
jeecg-boot-base-core       公共基础能力、异常、权限切面、租户、签名、MyBatis 拦截器
jeecg-module-system        用户、角色、菜单、租户、字典、日志、OpenAPI、消息等系统模块
jeecg-boot-module          demo、AI/RAG 等业务模块
jeecg-server-cloud         微服务网关、Nacos、监控、任务等云化模块
```

前端主要分为：

```text
jeecgboot-vue3             Vue3 + Vite + Ant Design Vue 管理后台
src/store/modules          用户、权限、菜单、标签页、布局状态
src/router                 动态路由、守卫、菜单转换
src/views/system           用户、角色、菜单、租户、字典、消息等后台页面
src/views/super/airag      AI 应用、模型、OCR、海报、Word 模板等页面
src/views/super/online     Online 表单、报表、图表配置页面
```

本项目可以参考的信息架构：

```text
Admin 后台：
  用户管理
  角色管理
  应用管理
  模型供应商
  模型配置
  Skill 管理
  MCP 管理
  Trace/Token 看板
  安全策略
  告警通知

User 控制台：
  对话
  Profile 选择
  Application/API Key
  历史会话
  Trace/Token 明细
  个人 Skill
```

---

## 3. 借鉴点一：统一响应和异常处理

参考文件：

```text
jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/common/api/vo/Result.java
jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/common/exception/JeecgBootExceptionHandler.java
```

JeecgBoot 的统一响应包含：

```text
success
message
code
result
timestamp
```

异常处理覆盖：

```text
参数校验失败
业务异常
401
404
权限异常
数据库唯一键冲突
文件上传过大
SQL 注入风险
Redis 连接异常
Sentinel 限流异常
```

本项目已实现：

```text
common.response.ApiResponse
common.response.PageResult
common.error.BizException
web.error.GlobalExceptionHandler
gateway.error.GatewayExceptionHandler
```

可借鉴点：

```text
1. 阶段 2 Trace 查询接口返回中可加入 timestamp 和 traceId。
2. 异常处理器可以按错误类型给更明确 message，尤其是参数校验、权限、限流、配额超限。
3. 异常日志可以关联 traceId，便于和 trace_roots 对齐。
```

不建议照搬：

```text
不要引入 JeecgBoot 的 Result 命名和 success/code 语义，保留本项目 ApiResponse。
不要在异常处理里直接写大量系统日志表，阶段 2 先统一写 trace_roots/trace_spans。
```

---

## 4. 借鉴点二：菜单权限、按钮权限和数据权限

参考文件：

```text
jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/common/aspect/PermissionDataAspect.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/entity/SysPermission.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/entity/SysPermissionDataRule.java
jeecgboot-vue3/src/store/modules/permission.ts
jeecgboot-vue3/src/directives/permission.ts
jeecgboot-vue3/src/views/system/menu
```

JeecgBoot 权限分层：

```text
菜单权限：控制用户能看到哪些菜单和路由。
按钮权限：通过权限码控制按钮显隐或禁用。
数据权限：按页面、接口、字段、规则对数据进行过滤。
租户权限：控制用户所属租户和租户套餐可用菜单。
```

本项目当前阶段已设计角色：

```text
Admin
Developer
User
```

本项目可借鉴落地方式：

```text
阶段 3 前端：
  后端返回当前用户菜单列表和权限码列表。
  前端根据权限码控制按钮显隐。
  管理页面路由由后端菜单配置或前端静态配置 + 权限过滤生成。

后端：
  Admin 才能管理模型供应商、全局 Skill、MCP Server、安全策略、配额。
  Developer 管理自己创建的 Profile 和应用。
  User 只访问自己的 Application、Conversation、Trace、Token 明细。
```

阶段 3 建议表或接口：

```text
GET /api/auth/me              返回 roles + permissions
GET /api/menus                返回当前用户可见菜单
GET /api/permissions          返回按钮权限码
```

不建议阶段 3 做完整数据权限引擎。原因：

```text
本项目已有 tenant_id / user_id / application_id 作为硬隔离字段。
阶段 3 只需保证资源归属校验和角色授权，不需要页面级动态 SQL 数据规则。
```

---

## 5. 借鉴点三：多租户与租户套餐

参考文件：

```text
jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/common/constant/TenantConstant.java
jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/config/mybatis/TenantContext.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/entity/SysTenant.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/entity/SysTenantPack.java
jeecgboot-vue3/src/views/system/tenant
```

JeecgBoot 的租户能力包含：

```text
租户表
用户-租户关系
租户套餐
租户菜单授权
租户页面管理
MyBatis 层租户条件注入
```

本项目已有：

```text
tenants
users.tenant_id
applications.tenant_id
agent_profiles.tenant_id
业务表保留 tenant_id
```

可借鉴但不照搬：

```text
可以借鉴租户管理页面、租户套餐/配额页面的概念。
Token 配额可以看作本项目的轻量“租户/应用套餐”。
不要现在引入自动 SQL 租户拦截器，当前先在 Service 层显式校验 tenantId。
```

阶段 2/3 可落地：

```text
Admin 配置 tenant/application 的日/月 Token 额度。
Trace 和 Token 查询必须按 tenantId/applicationId 过滤。
前端在个人中心展示当前租户、应用和配额状态。
```

后续增强：

```text
增加 tenant_plan / quota_policy 表。
多租户 Admin 可以切换租户查看统计。
开发者视角只看自己租户和应用数据。
```

---

## 6. 借鉴点四：OpenAPI AK/SK、白名单和调用日志

参考文件：

```text
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/openapi/filter/ApiAuthFilter.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/openapi/entity/OpenApi.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/openapi/entity/OpenApiAuth.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/openapi/entity/OpenApiPermission.java
jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/openapi/entity/OpenApiLog.java
```

JeecgBoot OpenAPI 做了：

```text
appkey
signature
timestamp
AK/SK 校验
签名 5 分钟有效期
IP 白名单，支持精确 IP、CIDR、通配符
接口授权关系
调用耗时日志
```

本项目当前外部 AI 调用使用：

```text
Authorization: Bearer sk-xxx
ApiKeyService.authenticate
API Key 绑定 Application
Gateway -> Core
```

可借鉴点：

```text
1. API Key 可以增加 IP 白名单字段。
2. Gateway 可以记录外部 API 调用耗时、状态、错误原因。
3. 后续开放 OpenAI 兼容接口时，可增加 client_request_id、timestamp、签名作为增强模式。
4. API Key 管理页面展示 prefix、创建时间、最后使用时间、状态、白名单。
```

阶段 2 不建议改为 AK/SK 签名。原因：

```text
OpenAI 兼容生态默认 Bearer token。
当前 API Key 绑定 Application 的设计更适合兼容模式。
签名模式可以作为企业增强模式，不应阻塞 MVP。
```

阶段 2 可先做：

```text
token_usage_logs + trace_roots 记录 entrypoint=API_KEY。
ApiKeyEntity 后续补 last_used_at、last_used_ip、allow_ips。
Gateway 失败也写 trace_roots FAILED。
```

---

## 7. 借鉴点五：动态网关路由

参考文件：

```text
jeecg-boot/jeecg-server-cloud/jeecg-cloud-gateway/src/main/java/org/jeecg/loader/DynamicRouteLoader.java
jeecg-boot/jeecg-server-cloud/jeecg-cloud-gateway/src/main/java/org/jeecg/loader/repository/DynamicRouteService.java
```

JeecgBoot 网关支持：

```text
从 yml / Nacos / Redis / 数据库加载路由。
动态新增、更新、删除路由。
发布 RefreshRoutesEvent 刷新 Spring Cloud Gateway 路由。
后台管理 sys_gateway_route。
```

本项目的取舍：

```text
不借鉴动态路由实现。
本项目 Gateway 是 AI Infra 治理网关，不是通用微服务网关。
阶段 2 固定入口足够：/internal/ai/chat/stream 和 /api/ai/chat/stream。
```

可以借鉴的只有管理思路：

```text
后续 Admin 页面可以配置 Gateway 安全策略、限流策略、脱敏规则、告警规则。
这些配置不是 HTTP 路由，而是 AI 调用治理策略。
```

---

## 8. 借鉴点六：前端后台动态菜单和权限码

参考文件：

```text
jeecgboot-vue3/src/store/modules/permission.ts
jeecgboot-vue3/src/directives/permission.ts
jeecgboot-vue3/src/router
jeecgboot-vue3/src/views/system/menu
```

JeecgBoot 前端做法：

```text
登录后请求后端菜单和权限码。
Pinia 保存 backMenuList、permCodeList、authList。
动态构造路由。
v-auth 指令按权限码删除无权限按钮。
```

本项目阶段 3 可借鉴：

```text
权限 store 保存 roles、permissionCodes、menus。
菜单按角色和后端返回权限过滤。
按钮用 v-permission 或组件级 PermissionGate 控制显隐。
Trace、Token、模型、Skill、MCP 管理页面按角色显示。
```

建议本项目权限码命名：

```text
model:provider:create
model:config:create
profile:create
profile:bind-skill
skill:admin
mcp:admin
trace:view
token:view
quota:manage
alert:manage
```

不建议：

```text
不需要阶段 3 做完整菜单管理 CRUD。
菜单可先静态配置，后端只返回权限码。
等 Admin 后台稳定后，再考虑菜单可配置。
```

---

## 9. 借鉴点七：AI/RAG 模块的信息架构

参考文件：

```text
README-AI.md
jeecg-boot/jeecg-boot-module/jeecg-boot-module-airag/src/main/java/org/jeecg/modules/airag
jeecg-boot/jeecg-boot-module/jeecg-boot-module-airag/src/main/java/org/jeecg/modules/airag/llm/handler/AIChatHandler.java
jeecgboot-vue3/src/views/super/airag
```

JeecgBoot AI 模块覆盖：

```text
AI 应用管理
AI 模型管理
AI 知识库
AI 流程编排
AI 聊天
MCP 配置
文档解析
多模态生成
嵌入第三方
```

对本项目的启发：

```text
本项目的 Agent Profile 可以对应 Jeecg 的 AI 应用。
本项目的 model_providers/model_configs 对应 AI 模型管理。
本项目的 Skill/MCP 对应工具与插件管理。
本项目长期记忆是轻量 RAG 的前置，不应阶段 1/2 引入完整知识库。
本项目阶段 4 Team 可以参考 AI 流程编排的节点化展示，但运行时仍保持自研。
```

可借鉴页面划分：

```text
模型配置页
Agent Profile 配置页
Skill 市场/管理页
MCP 工具页
对话调试页
Trace 运行详情页
Token 用量页
安全策略页
```

不建议阶段 2/3 引入：

```text
完整文档库、向量库、RAG 管道。
AI 建表、AI 报表、AI 大屏。
AI 流程设计器。
Word 模板、OCR、视频、语音生成。
```

---

## 10. 借鉴点八：模型调用兼容与降级

参考文件：

```text
jeecg-boot/jeecg-boot-module/jeecg-boot-module-airag/src/main/java/org/jeecg/modules/airag/llm/handler/AIChatHandler.java
```

JeecgBoot 的 AIChatHandler 有几个务实处理：

```text
模型未激活时走默认模型。
合并模型配置和运行时参数。
对 DeepSeek 推理模型的 reasoning_content 做兼容兜底。
工具调用异常转换为友好提示。
模型异常翻译为业务异常。
```

本项目可借鉴：

```text
mock-chat 永远可用，保证主链路演示不依赖外部模型。
真实模型不可用时不要影响阶段验收，可提示用户切换模型或使用 mock。
ModelInvokeResult 里保留 estimated usage。
后续如果支持 DeepSeek reasoning_content，需要保存或过滤 reasoning 字段，不要把 reasoning 原文写入长期记忆。
```

阶段 2 不做模型自动降级到默认模型。原因：

```text
Trace/Token 统计需要准确知道实际使用的 model_config_id。
自动降级容易让用户误以为调用了指定模型。
```

建议：

```text
模型不可用时明确失败，trace_roots 标记 FAILED。
仅在前端调试页提供“一键改用 mock-chat 重试”。
```

---

## 11. 本项目后续落地建议

### 11.1 阶段 2

吸收：

```text
Trace/Token 查询页面后端接口字段要服务前端看板。
API Key 调用日志维度：applicationId、userId、ip、latency、status。
失败异常要写 trace_roots FAILED。
配额超限返回明确错误码。
```

不吸收：

```text
动态网关路由。
Nacos / Redis 路由配置。
完整 OpenAPI AK/SK 签名。
```

### 11.2 阶段 3

吸收：

```text
后台动态菜单/权限码模式。
按钮权限。
Admin/User 页面分组。
租户和应用视角切换。
Trace/Token 管理页面。
```

不吸收：

```text
Online 表单。
报表和大屏设计器。
完整菜单管理 CRUD。
```

### 11.3 阶段 4/5

吸收：

```text
AI 应用配置的信息架构。
流程编排页面的节点化展示方法。
消息中心/通知中心作为飞书告警补充。
MCP 工具管理页面。
```

不吸收：

```text
Flowable。
完整 AI 流程引擎。
多端 APP 框架。
```

---

## 12. 最终判断

JeecgBoot 对本项目最有价值的是“企业后台平台化经验”：

```text
怎么组织 Admin/User 控制台。
怎么做菜单和按钮权限。
怎么做多租户管理和配额展示。
怎么做 OpenAPI 授权和调用日志。
怎么把 AI 模型、应用、工具、知识库放到后台信息架构里。
```

本项目应该吸收这些后台设计方法，但继续坚持自己的技术边界：

```text
Spring Security + JWT + API Key。
固定 AI Gateway 治理入口。
PostgreSQL Trace/Token 自研表。
Agent Profile / Skill / MCP / Memory 作为核心业务模型。
前端先做可演示 MVP，不做通用低代码平台。
```

阶段 3 前端控制台设计时，应优先回看本文档。
