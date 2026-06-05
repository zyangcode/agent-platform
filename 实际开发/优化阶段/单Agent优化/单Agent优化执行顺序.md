# 单Agent优化执行顺序

本文记录 `single-agent-optimization-flow.drawio` 对应的完整落地顺序。实现时按 Step 递进，每个 Step 都必须能独立验证，不一次性混改整张图。

## Step 0. 现状梳理与基线验收

- 确认 `Web -> Gateway -> Core AgentRuntime` 主链路可运行。
- 确认 SSE、Trace、Quota、Memory、Skill/MCP 基础能力的当前测试状态。
- 记录已有未提交改动，避免覆盖用户工作区。

## Step 1. 工具链收口

- 将单 Agent Runtime 的工具执行入口收敛到 `ToolResolver -> ToolCallValidator -> ToolDispatcher`。
- 删除 Runtime 中对 `calculator/weather/search` 的硬编码预判。
- 非法工具名、未授权工具、坏 JSON、空参数等场景统一生成 tool error observation，让模型下一轮自修正。
- Runtime 不再直接依赖 `SkillExecutor` / `McpToolExecutor`。

## Step 2. API Messages 与持久化 Messages 分离

- `conversation_message` 只保存干净、可审计、可恢复的对话历史。
- `apiMessages` 作为本轮模型调用副本，允许注入 memory、tool specs、experience refs、RAG refs、provider 兼容字段。
- RAG 原文、临时压缩说明、provider hack 不原样落库。

## Step 3. Context Budget Snapshot

- 显式记录 system/profile/history/memory/tools/experience/rag/current input 的 token 估算。
- 输出可观测的 context budget snapshot，供 Trace 和前端详情页查看。

## Step 4. Micro Compact / Auto Compact

- 每轮模型调用前压缩过长 tool observation、RAG 片段、异常输出和大 JSON。
- 超过阈值时摘要旧历史，保留最近 N 条原文。
- 摘要失败时 fallback trim，并注入上下文已降级说明。

## Step 5. Token 级流式输出

- `core.model` 支持 streaming callback。
- Runtime 聚合完整 assistant message，同时把 token chunk 交给 Gateway。
- Gateway SSE 推送 `message_delta`，最终仍推送 `message` / `done`。

## Step 6. Final Answer Builder

- 清洗工具原始 JSON、内部执行标签、task 清单、mock search 结果和异常细节。
- 工具链失败时返回用户可读的降级答案，不泄露内部执行结构。

## Step 7. 多工具 Batch

- 支持模型一次输出多个工具请求。
- 将工具请求整理成 `Tool Request Batch`，交给校验和执行规划层。

## Step 8. ToolExecutionPlanner + Fork/Join

- 按 `readOnly`、`resourceKeys`、`riskLevel`、`maxParallelTools` 判断串行、并行或分批。
- 使用受控线程池执行可并行工具。
- Join 后按原 tool call 顺序合并 observation。

## Step 9. Risk Guard / 高危工具确认

- LOW 工具直接执行。
- MEDIUM 工具记录 Trace，可配置是否确认。
- HIGH 工具通过 `tool_confirm_required` SSE 等待前端确认或按策略阻断。

## Step 10. Experience Skill / Prompt Skill

- 新增经验型 Skill 的编辑、存储、召回和作用域控制。
- 经验型 Skill 只进入 `apiMessages` context block，不进入 tools，不走 ToolDispatcher。
- 召回结果受 token budget 约束。
- 经验型 Skill 不使用 Jar 热加载；它承载的是领域经验、回复风格、处理步骤、业务规则片段等可注入上下文的知识，不负责执行外部动作。

## Step 11. Jar Skill 热加载链路补强

- Jar Skill 定位为“插件型 Skill”，用于展示平台扩展机制，而不是承载天气这类外部工具 Demo。
- 管理侧上传 Jar 和 Manifest。
- 校验接口实现、参数 Schema、权限和风险等级。
- URLClassLoader 按版本隔离加载，失败只标记对应版本 FAILED。
- 注册 SkillDefinition 并切换 currentVersion，运行时通过 ToolResolver/Dispatcher 自然可见。
- 推荐 Demo 使用 `TextAuditSkill`、`CodeReviewSkill`、`JsonFormatterSkill` 或 `BusinessRuleSkill` 这类本地插件能力，证明 Jar 上传、校验、加载、注册、禁用/卸载闭环。
- 天气查询优先交给 MCP Tool 展示：它是典型外部工具协议接入场景，和 Jar Skill 热加载展示保持边界清晰。
- MCP 工具接入按“JSON-RPC 2.0 消息层 + 可替换传输层”设计：本地 Demo 先支持 stdio 子进程通信，远程 Demo 按 2025-03-26 Streamable HTTP 兼容子集预留 `POST /mcp` 单端点；普通调用可返回 JSON，流式调用返回 SSE。

## Step 12. Memory v2 + RAG / VectorStore

- 详细设计以 `实际开发/优化阶段/单Agent优化/单Agent记忆与RAG设计方案.md` 为准；本 Step 不再一次性实现完整混合 RAG，而是拆成 Memory v2 稳定化、Schema-driven Context、RAG MVP、Qdrant 接入和 Hybrid/Graph 增强五段。
- 先平台化借鉴 AGI-saber 记忆系统：扩展长期记忆的 category、tags、importance、slotHint、lastAccessedAt 和召回过滤器。
- 当前已完成 Memory v2：`memories` 新增 `memory_category/tags/importance/slot_hint/metadata/last_accessed_at/access_count/confidence/expires_at`，`MemoryRecallFilter` 支持 category/tag/topK/minScore/maxAge，Context Builder 已用 summary/preference/fact filter 注入长期记忆。
- 已补 `MemoryConsolidationConfig` 对应的去重、合并、衰减、过期淘汰链路；规则版 `PreferenceExtractor` 已支持中英文偏好提取；`memory.recall/write/consolidate` Trace Span 已接入。
- 已引入 Schema-driven Context：`ContextSlotKind`、`ContextSlot`、`ContextSlotFilter`、`ContextSchema`、`ContextSlotSource`，把 memory/experience/tools/rag 都变成可预算控制的 Slot。
- Preference 作为持久化记忆类别或独立键值能力，不采用内存 Map。
- 新增 MemoryConsolidationConfig，支持去重、合并、衰减和过期策略。
- 新增知识文档、chunk 元数据和权限关系。
- PostgreSQL 保存业务元数据、chunk 原文、租户和 Profile 作用域。
- RAG MVP 已抽象 VectorStore 接口并提供 Mock/PG keyword fallback；真实 Qdrant 已接入并通过后端冒烟，Milvus / ES / Neo4j / RRF 作为后续增强，不照搬 AGI-saber 的 InfrastructureService 大杂烩。
- Qdrant 保存 embedding 和 payload，按 tenant/application/profile/document/chunk 过滤召回 topK；业务 ID 写入 `payload.vector_id`，Qdrant point id 使用稳定 UUID。
- RAG Context Slot 将 topK 片段压缩为参考资料，并纳入 token budget。
- RAG 结果只进入 `apiMessages`，不污染持久化历史。

## Step 13. Hook / Trace Span 补强

- 覆盖 `context.build`、`experience.resolve`、`api.messages.compose`、`rag.search`、`compact.micro`、`compact.auto`、`model.invoke`、`tool.validate`、`tool.plan`、`tool.execute`、`final.answer.build`。
- 独立 Trace Span 已补强，`model.invoke`、`tool.execute`、`memory.*`、`rag.*` 均记录可排障的摘要 attributes。
- 已新增轻量 Hook API：`preModelCall/postModelCall/preToolCall/postToolCall/postFinalAnswer`，用于后续挂载 Trace、Token usage、敏感扫描、告警、memory sync 和成本统计。
- Hook 只做观察型扩展点，异常会被隔离，不反向接管主流程。

## Step 14. 前端联动

- 支持 `message_delta` 实时渲染。
- 支持工具确认事件。
- 展示 RAG 引用、Trace span、Context Budget 和 Tool 执行详情。
