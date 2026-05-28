import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { I18nContext, type I18nContextValue, type Locale } from './i18n-context-value'

type TranslationValue = string | Record<Locale, string>
type TranslationDictionary = Record<string, TranslationValue>

const LANGUAGE_STORAGE_KEY = 'agent-platform.locale'

const defaultDictionary: TranslationDictionary = {
  'common.allApplications': { en: 'All applications', zh: '全部应用' },
  'common.allEntrypoints': { en: 'All entrypoints', zh: '全部入口' },
  'common.allStatuses': { en: 'All statuses', zh: '全部状态' },
  'common.copy': { en: 'Copy', zh: '复制' },
  'common.copied': { en: 'Copied', zh: '已复制' },
  'common.estimated': { en: 'estimated', zh: '估算' },
  'common.failed': { en: 'Failed', zh: '失败' },
  'common.loading': { en: 'Loading', zh: '加载中' },
  'common.real': { en: 'real', zh: '真实' },
  'common.refresh': { en: 'Refresh', zh: '刷新' },
  'common.running': { en: 'Running', zh: '运行中' },
  'common.signOut': { en: 'Sign out', zh: '退出登录' },
  'common.success': { en: 'Success', zh: '成功' },
  'common.tokens': { en: 'Tokens', zh: 'Token' },
  'common.tokensUnit': { en: 'tokens', zh: 'tokens' },
  'common.webOnline': { en: 'Web online', zh: 'Web 在线' },
  'chat.agentMode': { en: 'Agent', zh: 'Agent 模式' },
  'chat.applicationRequired': { en: 'Create an application before starting chat.', zh: '开始聊天前需要先创建应用。' },
  'chat.archiveConfirm': { en: 'Delete "{title}"?', zh: '删除“{title}”？' },
  'chat.chatResourcesUnavailable': { en: 'Chat resources unavailable', zh: '聊天资源不可用' },
  'chat.conversation': { en: 'Conversation', zh: '当前对话' },
  'chat.conversationFallbackTitle': { en: 'Conversation {id}', zh: '对话 {id}' },
  'chat.conversationHelp': {
    en: 'SSE messages render as soon as Web forwards them.',
    zh: 'Web 转发 SSE 事件后，消息会立即显示在这里。',
  },
  'chat.deleteConversation': { en: 'Delete conversation', zh: '删除对话' },
  'chat.directModelMode': { en: 'Direct model', zh: '直连模型' },
  'chat.disabledNoModel': { en: 'Direct model mode needs an active model config.', zh: '直连模型模式需要一个可用模型配置。' },
  'chat.disabledNoProfile': {
    en: 'Agent mode needs a profile. Switch to Direct model or create a profile first.',
    zh: 'Agent 模式需要 Profile。可以切换到直连模型，或先创建 Profile。',
  },
  'chat.firstRunDescription': {
    en: 'Pick an application, choose a mode, then send a short prompt. Runtime events will appear on the right panel.',
    zh: '选择应用和运行模式后发送问题。右侧会显示本次运行的 Trace、会话和事件。',
  },
  'chat.firstRunTitle': { en: 'Start an Agent run', zh: '开始一次 Agent 对话' },
  'chat.history': { en: 'History', zh: '历史会话' },
  'chat.historyDescription': { en: 'Recent conversations under the selected profile.', zh: '当前 Profile 下最近的历史会话。' },
  'chat.historyUnavailable': { en: 'History unavailable', zh: '历史会话不可用' },
  'chat.loadingApplications': { en: 'Loading applications.', zh: '正在加载应用。' },
  'chat.mode': { en: 'Mode', zh: '模式' },
  'chat.modelConfig': { en: 'Model config', zh: '模型配置' },
  'chat.newConversation': { en: 'New conversation', zh: '新对话' },
  'chat.noApplication': { en: 'No application', zh: '暂无应用' },
  'chat.noApplicationDescription': { en: 'Create an Application first. Chat requests need application scope.', zh: '请先创建应用，聊天请求需要应用作用域。' },
  'chat.noConversations': { en: 'No conversations', zh: '暂无历史会话' },
  'chat.noConversationsDescription': { en: 'Send a message to create the first conversation.', zh: '发送第一条消息后，会自动创建历史会话。' },
  'chat.noModel': { en: 'No model', zh: '暂无模型' },
  'chat.noProfileSelected': { en: 'No profile selected', zh: '未选择 Profile' },
  'chat.noProfileSelectedDescription': { en: 'Select an application and profile to load chat history.', zh: '选择应用和 Profile 后加载历史会话。' },
  'chat.noRunnableProfile': { en: 'No runnable profile', zh: '暂无可运行 Profile' },
  'chat.noTimestamp': { en: 'No timestamp', zh: '无时间' },
  'chat.placeholder': {
    en: 'Ask the agent to summarize the current project status...',
    zh: '让 Agent 总结一下当前项目进展...',
  },
  'chat.profile': { en: 'Profile', zh: 'Profile' },
  'chat.quotaNotEnough': { en: 'Token quota is not enough for this request.', zh: '本次请求的 Token 配额不足。' },
  'chat.refreshResources': { en: 'Refresh resources', zh: '刷新资源' },
  'chat.requestBlocked': { en: 'The request was blocked by security policy or permission rules.', zh: '请求被安全策略或权限规则阻断。' },
  'chat.runSetup': { en: 'Run setup', zh: '运行配置' },
  'chat.runSetupDescription': { en: 'Use Direct model when no Profile exists yet.', zh: '没有 Profile 时，可以先使用直连模型模式。' },
  'chat.selectApplication': { en: 'Select application', zh: '选择应用' },
  'chat.selectModel': { en: 'Select model', zh: '选择模型' },
  'chat.selectProfile': { en: 'Select profile', zh: '选择 Profile' },
  'chat.send': { en: 'Send', zh: '发送' },
  'chat.stop': { en: 'Stop', zh: '停止' },
  'chat.streamFailed': { en: 'Chat stream failed.', zh: '聊天流请求失败。' },
  'chat.streamFailedBeforeMessage': {
    en: 'The stream failed before an assistant message was returned.',
    zh: '助手消息返回前，流式请求已经失败。',
  },
  'chat.streamStopped': { en: 'Chat request was stopped.', zh: '聊天请求已停止。' },
  'chat.title': { en: 'Agent Chat', zh: 'Agent 对话' },
  'chat.titleDescription': {
    en: 'Browser calls enter Web first, then Gateway applies trace, token quota, sensitive data checks, and forwards the run to core.',
    zh: '浏览器请求先进入 Web，再由 Gateway 执行 Trace、Token 配额、敏感数据检查，并转发到 Core 运行。',
  },
  'runtime.conversation': { en: 'Conversation', zh: '会话' },
  'runtime.description': { en: 'Gateway stream metadata for the current run.', zh: '当前运行的 Gateway 流式元数据。' },
  'runtime.eventTimeline': { en: 'Event timeline', zh: '事件时间线' },
  'runtime.eventTimelineDescription': {
    en: 'thinking, action, observation, message, done, error.',
    zh: 'thinking、action、observation、message、done、error。',
  },
  'runtime.noStream': { en: 'No stream yet', zh: '暂无流式事件' },
  'runtime.noStreamDescription': { en: 'Send a message to watch runtime events arrive.', zh: '发送消息后，这里会展示运行事件。' },
  'runtime.statusDone': { en: 'Done', zh: '完成' },
  'runtime.statusError': { en: 'Error', zh: '错误' },
  'runtime.statusIdle': { en: 'Idle', zh: '空闲' },
  'runtime.statusStreaming': { en: 'Streaming', zh: '运行中' },
  'runtime.streamFailed': { en: 'Stream failed', zh: '流式请求失败' },
  'runtime.title': { en: 'Runtime', zh: '运行详情' },
  'runtime.traceId': { en: 'Trace ID', zh: 'Trace ID' },
  'application.actions': { en: 'Actions', zh: '操作' },
  'application.apiKeys': { en: 'API keys', zh: 'API Key' },
  'application.apiKeysDescription': {
    en: 'Keys for {name}. Plaintext secrets are only shown at creation.',
    zh: '{name} 的 API Key。明文密钥只在创建时展示一次。',
  },
  'application.apiKeysUnavailable': { en: 'API keys unavailable', zh: 'API Key 不可用' },
  'application.applicationDisabled': { en: 'Application disabled', zh: '应用已禁用' },
  'application.applicationDisabledDescription': {
    en: 'This application is visible for audit, but API key operations are disabled.',
    zh: '该应用仍可用于审计查看，但 API Key 操作已禁用。',
  },
  'application.create': { en: 'Create', zh: '创建' },
  'application.createButton': { en: 'New application', zh: '新建应用' },
  'application.createDescription': {
    en: 'Applications own API keys and provide the scope for later Profile and usage views.',
    zh: '应用持有 API Key，并作为后续 Profile 和用量视图的作用域。',
  },
  'application.createFailed': { en: 'Create failed', zh: '创建失败' },
  'application.createFailedFallback': {
    en: 'Application could not be created.',
    zh: '应用创建失败。',
  },
  'application.createFirst': {
    en: 'Create the first application to receive an API key and unlock Profile setup.',
    zh: '创建第一个应用后会获得 API Key，并解锁 Profile 配置。',
  },
  'application.created': { en: 'Created', zh: '创建时间' },
  'application.creating': { en: 'Creating', zh: '创建中' },
  'application.description': { en: 'Description', zh: '描述' },
  'application.descriptionPlaceholder': {
    en: 'Internal application for Agent platform demos.',
    zh: '用于 Agent 平台演示的内部应用。',
  },
  'application.disable': { en: 'Disable', zh: '禁用' },
  'application.disableDescription': {
    en: '{name} will stay visible, but Chat, Profile, and API key operations will no longer use it.',
    zh: '{name} 会继续保留用于查看，但聊天、Profile 和 API Key 操作将不再使用它。',
  },
  'application.disableFailed': { en: 'Disable failed', zh: '禁用失败' },
  'application.disableFailedFallback': {
    en: 'Application could not be disabled.',
    zh: '应用禁用失败。',
  },
  'application.disableTitle': { en: 'Disable application', zh: '禁用应用' },
  'application.disabling': { en: 'Disabling', zh: '禁用中' },
  'application.edit': { en: 'Edit', zh: '编辑' },
  'application.editDescription': {
    en: 'Rename the application or update its usage note.',
    zh: '修改应用名称或用途说明。',
  },
  'application.editTitle': { en: 'Edit application', zh: '编辑应用' },
  'application.intro': {
    en: 'Applications own API keys. Later Profile, Chat, Trace, and Token Usage views use this scope for filtering and quota accounting.',
    zh: '应用持有 API Key。后续 Profile、聊天、Trace 和 Token 用量都会按应用作用域筛选和计量。',
  },
  'application.lastUsed': { en: 'Last used', zh: '最后使用' },
  'application.list': { en: 'Application list', zh: '应用列表' },
  'application.listDescription': {
    en: 'Current user applications returned by Web APIs.',
    zh: 'Web API 返回的当前用户应用。',
  },
  'application.name': { en: 'Name', zh: '名称' },
  'application.namePlaceholder': { en: 'Research console', zh: '研究控制台' },
  'application.noApplicationSelected': { en: 'No application selected', zh: '未选择应用' },
  'application.noApplicationSelectedDescription': {
    en: 'Create or select an application. Keys are scoped to a single application.',
    zh: '创建或选择一个应用。API Key 只归属于单个应用。',
  },
  'application.noApplications': { en: 'No applications', zh: '暂无应用' },
  'application.noApiKeys': { en: 'No API keys', zh: '暂无 API Key' },
  'application.noApiKeysDescription': {
    en: 'This application has no visible keys. Create a new application to receive an initial key.',
    zh: '该应用暂无可见 API Key。新建应用时会生成初始 Key。',
  },
  'application.noDescription': { en: 'No description', zh: '暂无描述' },
  'application.prefix': { en: 'Prefix', zh: '前缀' },
  'application.revoke': { en: 'Revoke', zh: '吊销' },
  'application.revoking': { en: 'Revoking', zh: '吊销中' },
  'application.saveChanges': { en: 'Save changes', zh: '保存修改' },
  'application.selectApiKeys': { en: 'Select an application to inspect API keys.', zh: '选择一个应用查看 API Key。' },
  'application.status': { en: 'Status', zh: '状态' },
  'application.title': { en: 'Applications', zh: '应用' },
  'application.unavailable': { en: 'Applications unavailable', zh: '应用不可用' },
  'application.updateFailed': { en: 'Update failed', zh: '更新失败' },
  'application.updateFailedFallback': {
    en: 'Application could not be updated.',
    zh: '应用更新失败。',
  },
  'dashboard.applications': { en: 'Applications', zh: '应用' },
  'dashboard.completion': { en: 'Completion', zh: 'Completion' },
  'dashboard.dataLoadFailed': { en: 'Dashboard data could not be loaded.', zh: '仪表盘数据加载失败。' },
  'dashboard.noApplicationsDescription': {
    en: 'Create an Application first. Profiles, Chat, Trace, and Token Usage all become more useful after an application exists.',
    zh: '请先创建应用。应用存在后，Profile、聊天、Trace 和 Token 用量才会更有意义。',
  },
  'dashboard.noApplicationsYet': { en: 'No applications yet', zh: '暂无应用' },
  'dashboard.noTraces': { en: 'No traces', zh: '暂无 Trace' },
  'dashboard.noTracesDescription': {
    en: 'Run an Agent Chat after creating an Application and Profile. Trace data will appear here when the backend writes trace roots.',
    zh: '创建应用和 Profile 后发起一次 Agent 对话。后端写入 Trace Root 后会显示在这里。',
  },
  'dashboard.profiles': { en: 'Profiles', zh: 'Profile' },
  'dashboard.prompt': { en: 'Prompt', zh: 'Prompt' },
  'dashboard.quotaExhausted': {
    en: 'Quota is exhausted. Dashboard data is still protected by the gateway policy.',
    zh: '配额已耗尽。仪表盘数据仍受 Gateway 策略保护。',
  },
  'dashboard.ready': { en: 'Ready', zh: '就绪' },
  'dashboard.recentTraces': { en: 'Recent traces', zh: '最近 Trace' },
  'dashboard.recentTracesDescription': {
    en: 'Latest gateway-governed requests visible to this user.',
    zh: '当前用户可见的最新 Gateway 治理请求。',
  },
  'dashboard.retry': { en: 'Retry', zh: '重试' },
  'dashboard.sessionExpired': { en: 'Session expired. Sign in again to continue.', zh: '登录已过期，请重新登录。' },
  'dashboard.started': { en: 'Started', zh: '开始时间' },
  'dashboard.tokenDataUnavailable': { en: 'Token data unavailable', zh: 'Token 数据不可用' },
  'dashboard.tokenDataUnavailableDescription': { en: 'Resolve the dashboard error and retry.', zh: '请处理仪表盘错误后重试。' },
  'dashboard.tokenSummary': { en: 'Token summary', zh: 'Token 汇总' },
  'dashboard.tokenSummaryDescription': {
    en: 'Prompt and completion tokens recorded by quota services.',
    zh: '配额服务记录的 Prompt 和 Completion Token。',
  },
  'dashboard.tokenUsage': { en: 'Token usage', zh: 'Token 用量' },
  'dashboard.totalTokens': { en: 'Total tokens', zh: '总 Token' },
  'dashboard.unavailable': { en: 'Dashboard unavailable', zh: '仪表盘不可用' },
  'model.baseUrl': { en: 'Base URL', zh: 'Base URL' },
  'model.capabilitiesJson': { en: 'Capabilities JSON', zh: '能力 JSON' },
  'model.context': { en: 'Context', zh: '上下文' },
  'model.createConfigDescription': {
    en: 'Bind a concrete model name to a provider. Profiles and Direct model chat can use active configs.',
    zh: '把具体模型名绑定到供应商，Profile 和直连模型聊天可使用启用的配置。',
  },
  'model.createConfigTitle': { en: 'Create model config', zh: '创建模型配置' },
  'model.createFailed': { en: 'Create failed', zh: '创建失败' },
  'model.createModel': { en: 'Create model', zh: '创建模型' },
  'model.createProviderDescription': {
    en: 'Add an OpenAI-compatible endpoint. API keys are stored encrypted and are not shown again.',
    zh: '添加 OpenAI 兼容端点。API Key 会加密保存，之后不再展示。',
  },
  'model.createProviderTitle': { en: 'Create model provider', zh: '创建模型供应商' },
  'model.defaultTemperature': { en: 'Default temperature', zh: '默认温度' },
  'model.displayName': { en: 'Display name', zh: '显示名' },
  'model.intro': {
    en: 'Register OpenAI-compatible providers, then create model configs used by Profiles and Direct model chat.',
    zh: '注册 OpenAI 兼容供应商，再创建 Profile 和直连模型聊天可使用的模型配置。',
  },
  'model.maxContextTokens': { en: 'Max context tokens', zh: '最大上下文 Token' },
  'model.model': { en: 'Model', zh: '模型' },
  'model.modelConfigs': { en: 'Model configs', zh: '模型配置' },
  'model.modelConfigsDescription': {
    en: 'Concrete model names available to runtime selection.',
    zh: '运行时可选择的具体模型名称。',
  },
  'model.modelName': { en: 'Model name', zh: '模型名' },
  'model.newModel': { en: 'New model', zh: '新建模型' },
  'model.newProvider': { en: 'New provider', zh: '新建供应商' },
  'model.noConfig': { en: 'No model configs', zh: '暂无模型配置' },
  'model.noConfigDescription': {
    en: 'Create a model config, then bind it to a Profile.',
    zh: '创建模型配置后，可以绑定到 Profile。',
  },
  'model.noProvider': { en: 'No provider', zh: '暂无供应商' },
  'model.noProviderDescription': {
    en: 'Create a provider before creating model configs.',
    zh: '创建模型配置前需要先创建供应商。',
  },
  'model.provider': { en: 'Provider', zh: '供应商' },
  'model.providerCreateFailedFallback': {
    en: 'Model provider could not be created.',
    zh: '模型供应商创建失败。',
  },
  'model.providerEndpoints': {
    en: 'Provider endpoints and encrypted API keys.',
    zh: '供应商端点和加密保存的 API Key。',
  },
  'model.providerType': { en: 'Provider type', zh: '供应商类型' },
  'model.providers': { en: 'Providers', zh: '供应商' },
  'model.selectProvider': { en: 'Select provider', zh: '选择供应商' },
  'model.settingsLoadFailed': { en: 'Model settings could not be loaded.', zh: '模型设置加载失败。' },
  'model.title': { en: 'Model Configs', zh: '模型配置' },
  'model.unavailable': { en: 'Models unavailable', zh: '模型不可用' },
  'tools.catalogFilters': { en: 'Catalog filters', zh: '目录筛选' },
  'tools.catalogFiltersDescription': {
    en: 'Filter server-side by status/scope and client-side by search.',
    zh: '按状态/范围进行服务端筛选，并支持本地搜索。',
  },
  'tools.intro': {
    en: 'Browse existing Skills and MCP tools that can be bound to Profiles. Stage 3 keeps upload and Jar hot-loading UI out of scope.',
    zh: '浏览可绑定到 Profile 的已有 Skill 和 MCP 工具。阶段 3 暂不包含上传和 Jar 热加载界面。',
  },
  'tools.mcpStatus': { en: 'MCP status', zh: 'MCP 状态' },
  'tools.mcpTools': { en: 'MCP tools', zh: 'MCP 工具' },
  'tools.noMcpTools': { en: 'No MCP tools', zh: '暂无 MCP 工具' },
  'tools.noMcpToolsDescription': { en: 'No MCP tools match the current filters.', zh: '没有符合当前筛选条件的 MCP 工具。' },
  'tools.noSkills': { en: 'No Skills', zh: '暂无 Skill' },
  'tools.noSkillsDescription': {
    en: 'No Skills match the current filters. Stage 3 does not expose upload UI.',
    zh: '没有符合当前筛选条件的 Skill。阶段 3 暂不开放上传界面。',
  },
  'tools.schema': { en: 'Schema', zh: 'Schema' },
  'tools.search': { en: 'Search', zh: '搜索' },
  'tools.searchPlaceholder': {
    en: 'Search name, code, description, status',
    zh: '搜索名称、编码、描述、状态',
  },
  'tools.server': { en: 'Server', zh: '服务' },
  'tools.skillScope': { en: 'Skill scope', zh: 'Skill 范围' },
  'tools.skillStatus': { en: 'Skill status', zh: 'Skill 状态' },
  'tools.skills': { en: 'Skills', zh: 'Skills' },
  'tools.title': { en: 'Tools', zh: '工具' },
  'tools.unavailable': { en: 'Tools unavailable', zh: '工具不可用' },
  'tools.visibleLoadedMcp': {
    en: '{visible} visible of {total} loaded MCP tools.',
    zh: '已加载 {total} 个 MCP 工具，当前显示 {visible} 个。',
  },
  'tools.visibleLoadedSkills': {
    en: '{visible} visible of {total} loaded Skills.',
    zh: '已加载 {total} 个 Skill，当前显示 {visible} 个。',
  },
  'admin.securityTitle': { en: 'Security', zh: '安全策略' },
  'admin.securityIntro': {
    en: 'Review the Gateway governance capabilities that are already active in the MVP.',
    zh: '查看 MVP 阶段已经生效的 Gateway 治理能力。',
  },
  'admin.securityChain': { en: 'Governance chain', zh: '治理链状态' },
  'admin.securityChainDescription': {
    en: 'These items reflect implemented backend behavior, not mock UI actions.',
    zh: '这些条目反映后端已实现行为，不是前端模拟操作。',
  },
  'admin.scanRules': { en: 'Sensitive scan rules', zh: '敏感扫描规则' },
  'admin.scanRulesDescription': {
    en: 'Request-side rules currently enforced before model execution.',
    zh: '当前在模型执行前生效的请求侧规则。',
  },
  'admin.mvpBoundary': { en: 'MVP boundary', zh: 'MVP 边界' },
  'admin.securityBoundaryDescription': {
    en: 'Editable security policy CRUD, response scanning UI, and alert policy configuration are planned after the main demo path is stable.',
    zh: '安全策略增删改查、响应扫描配置界面和告警策略配置会在主 Demo 链路稳定后再补。',
  },
  'admin.usersTitle': { en: 'Users', zh: '用户' },
  'admin.usersIntro': {
    en: 'Inspect the current account and role model. Full user administration is intentionally not exposed in this MVP console.',
    zh: '查看当前账号和角色模型。MVP 控制台暂不开放完整用户管理后台。',
  },
  'admin.currentAccount': { en: 'Current account', zh: '当前账号' },
  'admin.currentAccountDescription': {
    en: 'Derived from the active JWT and /api/auth/me.',
    zh: '来自当前 JWT 和 /api/auth/me。',
  },
  'admin.roleModel': { en: 'Role model', zh: '角色模型' },
  'admin.roleModelDescription': {
    en: 'Navigation and backend Admin APIs use role-based access control.',
    zh: '导航菜单和后端 Admin API 使用角色权限控制。',
  },
  'admin.userBoundaryDescription': {
    en: 'Registration and login are available. Admin-created users, role assignment UI, disable/reset flows, and audit screens are deferred.',
    zh: '注册和登录已可用。Admin 创建用户、角色分配界面、禁用/重置流程和审计页面暂缓。',
  },
  'admin.displayName': { en: 'Display name', zh: '显示名' },
  'admin.username': { en: 'Username', zh: '用户名' },
  'admin.userId': { en: 'User ID', zh: '用户 ID' },
  'admin.tenantId': { en: 'Tenant ID', zh: '租户 ID' },
  'admin.roles': { en: 'Roles', zh: '角色' },
  'admin.roleCount': { en: 'Role count', zh: '角色数量' },
  'admin.noRawSensitiveText': { en: 'No raw sensitive text', zh: '不保存敏感原文' },
  'admin.noRawSensitiveTextDescription': {
    en: 'Audit rows store hashes and masked samples only.',
    zh: '审计记录只保存哈希和脱敏样例。',
  },
  'admin.adminOnly': { en: 'Admin route', zh: '管理员路由' },
  'admin.adminOnlyDescription': {
    en: 'This page is designed for ADMIN users. Non-admin accounts should not see it in navigation.',
    zh: '该页面面向 ADMIN 用户。非管理员账号不会在导航中看到入口。',
  },
  'profile.application': { en: 'Application', zh: '应用' },
  'profile.applicationScope': { en: 'Application scope', zh: '应用范围' },
  'profile.applicationScopeDescription': {
    en: 'Profiles are listed under a single application.',
    zh: 'Profile 按单个应用归属展示。',
  },
  'profile.bindingsReadOnly': {
    en: 'Tool bindings are read-only because this profile is not editable.',
    zh: '当前 Profile 不可编辑，工具绑定只读。',
  },
  'profile.cancel': { en: 'Cancel', zh: '取消' },
  'profile.create': { en: 'Create', zh: '创建' },
  'profile.createButton': { en: 'New profile', zh: '新建 Profile' },
  'profile.createDescription': {
    en: 'MVP creates a private general profile with READ_WRITE memory and a single model config.',
    zh: 'MVP 会创建一个私有通用 Profile，默认启用 READ_WRITE 记忆并绑定一个模型配置。',
  },
  'profile.createFailed': { en: 'Create failed', zh: '创建失败' },
  'profile.createTitle': { en: 'Create profile', zh: '创建 Profile' },
  'profile.creating': { en: 'Creating', zh: '创建中' },
  'profile.description': { en: 'Description', zh: '描述' },
  'profile.descriptionPlaceholder': {
    en: 'A focused assistant for project research and summary tasks.',
    zh: '例如：用于生活建议、简单计算和日常信息整理的助手。',
  },
  'profile.detail': { en: 'Profile detail', zh: 'Profile 详情' },
  'profile.detailDescription': {
    en: 'Inspect style prompt, memory, model, and bound tools.',
    zh: '查看风格提示词、记忆策略、模型和已绑定工具。',
  },
  'profile.disableButton': { en: 'Disable profile', zh: '禁用 Profile' },
  'profile.disableDescription': {
    en: '{name} will stay visible for review, but it will no longer be used for Chat or tool binding changes.',
    zh: '{name} 仍会保留用于查看，但不能再用于聊天或修改工具绑定。',
  },
  'profile.disableFailed': { en: 'Disable failed', zh: '禁用失败' },
  'profile.disableTitle': { en: 'Disable profile', zh: '禁用 Profile' },
  'profile.disabling': { en: 'Disabling', zh: '禁用中' },
  'profile.editButton': { en: 'Edit profile', zh: '编辑 Profile' },
  'profile.editDescription': {
    en: 'Update the profile model, style prompt, and runtime step limit.',
    zh: '修改模型配置、风格提示词和运行步数限制。',
  },
  'profile.editTitle': { en: 'Edit profile', zh: '编辑 Profile' },
  'profile.executionMode': { en: 'Execution mode', zh: '运行模式' },
  'profile.executionModeBasic': { en: 'Basic Agent', zh: '基础 Agent' },
  'profile.executionModeTeam': { en: 'Team Agent', zh: 'Team Agent' },
  'profile.intro': {
    en: 'Configure a single Agent profile for an application, bind enabled tools, then use it in Chat.',
    zh: '为应用配置单 Agent Profile，绑定可用工具后在聊天中使用。',
  },
  'profile.list': { en: 'Profile list', zh: 'Profile 列表' },
  'profile.listDescription': { en: 'Click a row to inspect and bind tools.', zh: '点击一行查看详情并绑定工具。' },
  'profile.maxSteps': { en: 'Max steps', zh: '最大步数' },
  'profile.mcpBindings': { en: 'MCP bindings', zh: 'MCP 绑定' },
  'profile.mcpTools': { en: 'MCP tools', zh: 'MCP 工具' },
  'profile.model': { en: 'Model', zh: '模型' },
  'profile.modelConfig': { en: 'Model config', zh: '模型配置' },
  'profile.name': { en: 'Name', zh: '名称' },
  'profile.namePlaceholder': { en: 'Research assistant', zh: '生活小助手' },
  'profile.noApplication': { en: 'No application', zh: '暂无应用' },
  'profile.noApplicationDescription': {
    en: 'Create an Application before creating Profiles.',
    zh: '创建 Profile 前需要先创建应用。',
  },
  'profile.noDescription': { en: 'No description', zh: '暂无描述' },
  'profile.noEnabledMcpTools': { en: 'No enabled MCP tools.', zh: '暂无可用 MCP 工具。' },
  'profile.noEnabledSkills': { en: 'No enabled Skills.', zh: '暂无可用 Skill。' },
  'profile.noMcpToolsBound': { en: 'No MCP tools bound.', zh: '未绑定 MCP 工具。' },
  'profile.noProfileSelected': { en: 'No profile selected', zh: '未选择 Profile' },
  'profile.noProfileSelectedBindingDescription': {
    en: 'Select a profile before binding tools.',
    zh: '选择 Profile 后才能绑定工具。',
  },
  'profile.noProfileSelectedDescription': {
    en: 'Create or select a profile to inspect its runtime setup.',
    zh: '创建或选择一个 Profile 查看运行配置。',
  },
  'profile.noProfiles': { en: 'No profiles', zh: '暂无 Profile' },
  'profile.noProfilesDescription': {
    en: 'Create a profile for this application, then bind tools.',
    zh: '为当前应用创建 Profile 后再绑定工具。',
  },
  'profile.noSkillsBound': { en: 'No Skills bound.', zh: '未绑定 Skill。' },
  'profile.noStylePrompt': { en: 'No style prompt configured.', zh: '未配置风格提示词。' },
  'profile.profileDisabled': { en: 'Profile disabled', zh: 'Profile 不可编辑' },
  'profile.profilesUnavailable': { en: 'Profiles unavailable', zh: 'Profiles 不可用' },
  'profile.saveBindings': { en: 'Save bindings', zh: '保存绑定' },
  'profile.saveChanges': { en: 'Save changes', zh: '保存修改' },
  'profile.saveFailed': { en: 'Save failed', zh: '保存失败' },
  'profile.saving': { en: 'Saving', zh: '保存中' },
  'profile.seedDataHint': {
    en: 'Seed data or later upload flows can populate this list.',
    zh: '可通过种子数据或后续上传流程补充列表。',
  },
  'profile.selectApplication': { en: 'Select application', zh: '选择应用' },
  'profile.selectExecutionMode': { en: 'Select execution mode', zh: '选择运行模式' },
  'profile.selectModel': { en: 'Select model', zh: '选择模型' },
  'profile.skillBindings': { en: 'Skill bindings', zh: 'Skill 绑定' },
  'profile.skills': { en: 'Skills', zh: 'Skills' },
  'profile.status': { en: 'Status', zh: '状态' },
  'profile.stylePrompt': { en: 'Style prompt', zh: '风格提示词' },
  'profile.stylePromptPlaceholder': {
    en: 'Example: Answer in Chinese, lead with the conclusion, keep the tone concise and practical.',
    zh: '例如：使用中文回答，先给结论，再给必要解释；语气简洁，适合日常生活助手。',
  },
  'profile.title': { en: 'Profiles', zh: 'Profiles' },
  'profile.toolBindings': { en: 'Tool bindings', zh: '工具绑定' },
  'profile.toolBindingsDescription': {
    en: 'Bind existing Skills and MCP tools. Upload UI is deferred.',
    zh: '绑定已有 Skill 和 MCP 工具；上传功能暂缓。',
  },
  'profile.tools': { en: 'Tools', zh: '工具' },
  'profile.toolsUnavailable': { en: 'Tools unavailable', zh: '工具不可用' },
  'profile.type': { en: 'Type', zh: '类型' },
  'profile.updateFailed': { en: 'Update failed', zh: '更新失败' },
  'layout.consoleEyebrow': { en: 'Console', zh: '控制台' },
  'layout.operationsWorkspace': { en: 'Operations workspace', zh: '运行工作台' },
  'layout.userLine': { en: '{displayName} / {username}', zh: '{displayName} / {username}' },
  'nav.admin': { en: 'Admin', zh: '管理' },
  'nav.agentChat': { en: 'Agent Chat', zh: 'Agent 对话' },
  'nav.applications': { en: 'Applications', zh: '应用' },
  'nav.dashboard': { en: 'Dashboard', zh: '仪表盘' },
  'nav.modelConfigs': { en: 'Model Configs', zh: '模型配置' },
  'nav.observability': { en: 'Observability', zh: '可观测性' },
  'nav.profiles': { en: 'Profiles', zh: 'Profile 配置' },
  'nav.security': { en: 'Security', zh: '安全策略' },
  'nav.tokenUsage': { en: 'Token Usage', zh: 'Token 用量' },
  'nav.tools': { en: 'Tools', zh: '工具' },
  'nav.traces': { en: 'Traces', zh: 'Trace 追踪' },
  'nav.users': { en: 'Users', zh: '用户' },
  'nav.workspace': { en: 'Workspace', zh: '工作区' },
  'trace.attributes': { en: 'Attributes', zh: '属性' },
  'trace.detail': { en: 'Trace detail', zh: 'Trace 详情' },
  'trace.detailMissing': { en: 'Trace detail missing', zh: 'Trace 详情缺失' },
  'trace.detailMissingDescription': {
    en: 'This trace detail is not available yet.',
    zh: '当前 Trace 详情暂不可用。',
  },
  'trace.detailUnavailable': { en: 'Trace detail unavailable', zh: 'Trace 详情不可用' },
  'trace.detailUnavailableDescription': {
    en: 'This request may not have produced a trace root yet.',
    zh: '这次请求可能还没有生成 Trace Root。',
  },
  'trace.emptyDescription': {
    en: 'Run Chat from the browser or API Key entrypoint. Trace roots will appear here when Gateway writes them successfully.',
    zh: '从浏览器聊天或 API Key 入口发起调用。Gateway 成功写入后，Trace Root 会显示在这里。',
  },
  'trace.emptyTitle': { en: 'No traces', zh: '暂无 Trace' },
  'trace.entrypoint': { en: 'Entrypoint', zh: '入口' },
  'trace.errorCode': { en: 'Error code', zh: '错误码' },
  'trace.filterDescription': {
    en: 'Server-side filters mapped to the Stage 2 Trace API.',
    zh: '基于阶段 2 Trace API 的服务端筛选。',
  },
  'trace.filters': { en: 'Trace filters', zh: 'Trace 筛选' },
  'trace.intro': {
    en: 'Inspect gateway-governed AI requests, span timelines, and token records. Missing trace roots are treated as normal empty/error states while Stage 2 hardens MODEL_ERROR paths.',
    zh: '查看经过 Gateway 治理的 AI 请求、Span 时间线和 Token 记录。MODEL_ERROR 场景下缺失的 Trace Root 会按空状态或错误状态展示。',
  },
  'trace.latency': { en: 'Latency', zh: '耗时' },
  'trace.list': { en: 'Trace list', zh: 'Trace 列表' },
  'trace.listFallback': { en: 'Latest traces', zh: '最新 Trace' },
  'trace.listMatched': { en: '{total} traces matched', zh: '匹配到 {total} 条 Trace' },
  'trace.mode': { en: 'Mode', zh: '模式' },
  'trace.noSpanSelected': { en: 'No span selected', zh: '未选择 Span' },
  'trace.noSpanSelectedDescription': { en: 'Click a span in the timeline.', zh: '点击时间线中的一个 Span 查看详情。' },
  'trace.noSpans': { en: 'No spans', zh: '暂无 Span' },
  'trace.noSpansDescription': { en: 'This trace root has no span details yet.', zh: '当前 Trace Root 暂无 Span 明细。' },
  'trace.noTokenUsage': { en: 'No token usage linked to this trace.', zh: '当前 Trace 没有关联的 Token 用量记录。' },
  'trace.parent': { en: 'Parent', zh: '父级' },
  'trace.selectTraceDescription': { en: 'Select a trace row from the list.', zh: '从列表中选择一条 Trace。' },
  'trace.selectTraceTitle': { en: 'No trace selected', zh: '未选择 Trace' },
  'trace.selectTraceToInspect': { en: 'Select a trace to inspect spans.', zh: '选择一条 Trace 查看 Span。' },
  'trace.spanDetail': { en: 'Span detail', zh: 'Span 详情' },
  'trace.spanError': { en: 'Span error', zh: 'Span 错误' },
  'trace.spanId': { en: 'Span ID', zh: 'Span ID' },
  'trace.spanTimeline': { en: 'Span timeline', zh: 'Span 时间线' },
  'trace.started': { en: 'Started', zh: '开始时间' },
  'trace.ended': { en: 'Ended', zh: '结束时间' },
  'trace.status': { en: 'Status', zh: '状态' },
  'trace.title': { en: 'Traces', zh: 'Trace 追踪' },
  'trace.tokenUsageRecords': { en: 'Token usage records', zh: 'Token 用量记录' },
  'trace.traceFailed': { en: 'Trace failed', zh: 'Trace 失败' },
  'trace.traceLabel': { en: 'Trace', zh: 'Trace' },
  'trace.traceUnavailable': { en: 'Traces unavailable', zh: 'Trace 不可用' },
  'usage.estimatedRatio': { en: 'Estimated ratio', zh: '估算占比' },
  'usage.estimatedRatioDescription': {
    en: 'Share of requests where the model did not return token usage and the platform estimated it.',
    zh: '模型没有返回真实 token usage 时，平台会按文本长度估算；这里表示估算记录占全部请求的比例。',
  },
  'usage.title': { en: 'Token Usage', zh: 'Token 用量' },
}

function readInitialLocale(): Locale {
  if (typeof window === 'undefined') {
    return 'zh'
  }

  const storedLocale = window.localStorage.getItem(LANGUAGE_STORAGE_KEY)
  return storedLocale === 'en' || storedLocale === 'zh' ? storedLocale : 'zh'
}

function interpolate(text: string, params?: Record<string, string | number | null | undefined>) {
  if (!params) {
    return text
  }

  return Object.entries(params).reduce((nextText, [key, value]) => {
    return nextText.replaceAll(`{${key}}`, value === null || value === undefined ? '' : String(value))
  }, text)
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(readInitialLocale)

  const setLocale = useCallback((nextLocale: Locale) => {
    setLocaleState(nextLocale)
  }, [])

  const toggleLocale = useCallback(() => {
    setLocaleState((currentLocale) => (currentLocale === 'zh' ? 'en' : 'zh'))
  }, [])

  useEffect(() => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, locale)
    document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en'
  }, [locale])

  const value = useMemo<I18nContextValue>(() => {
    return {
      locale,
      setLocale,
      t(key, params) {
        const item = defaultDictionary[key]
        const text = typeof item === 'string' ? item : item?.[locale] ?? key
        return interpolate(text, params)
      },
      toggleLocale,
    }
  }, [locale, setLocale, toggleLocale])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
