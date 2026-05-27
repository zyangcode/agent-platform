import type { CurrentUser } from '@/lib/api/types'
import type { Locale } from '@/lib/i18n/i18n-context-value'

export type AdminPolicyStatus = 'active' | 'deferred' | 'manual'

export type AdminPolicyItem = {
  key: string
  status: AdminPolicyStatus
  title: Record<Locale, string>
  description: Record<Locale, string>
}

export const securityPolicyItems: AdminPolicyItem[] = [
  {
    key: 'gateway-chain',
    status: 'active',
    title: { en: 'Gateway enforcement chain', zh: 'Gateway 治理链' },
    description: {
      en: 'Trace, sensitive-data scan, token quota, runtime forwarding, and token recording are wired in Gateway.',
      zh: 'Gateway 已接入 Trace、敏感数据扫描、Token 配额、运行转发和 Token 记录。',
    },
  },
  {
    key: 'sensitive-request-scan',
    status: 'active',
    title: { en: 'Request sensitive-data blocking', zh: '请求敏感数据阻断' },
    description: {
      en: 'Phone, email, ID-card, and API-key patterns are scanned before model execution.',
      zh: '模型执行前会扫描手机号、邮箱、身份证号和 API Key 模式。',
    },
  },
  {
    key: 'security-events',
    status: 'active',
    title: { en: 'Security event audit', zh: '安全事件审计' },
    description: {
      en: 'Events store type, location, hash, and masked samples without persisting raw sensitive text.',
      zh: '安全事件只保存类型、位置、哈希和脱敏样例，不保存敏感原文。',
    },
  },
  {
    key: 'policy-admin-ui',
    status: 'deferred',
    title: { en: 'Editable policy console', zh: '可编辑安全策略后台' },
    description: {
      en: 'Full CRUD for policy rules is deferred; current MVP keeps rules in backend code and seed data.',
      zh: '策略规则完整增删改查暂缓；当前 MVP 由后端代码和种子数据固定。',
    },
  },
]

export const roleCapabilityItems: AdminPolicyItem[] = [
  {
    key: 'admin',
    status: 'active',
    title: { en: 'ADMIN', zh: 'ADMIN 管理员' },
    description: {
      en: 'Can access model settings and Admin routes. Full user CRUD is not exposed in the MVP console.',
      zh: '可访问模型配置和 Admin 路由。MVP 控制台暂不开放完整用户增删改查。',
    },
  },
  {
    key: 'developer',
    status: 'manual',
    title: { en: 'DEVELOPER', zh: 'DEVELOPER 开发者' },
    description: {
      en: 'Target role for profile and domain tool configuration. Assignment is currently handled by seed data or database updates.',
      zh: '目标职责是配置 Profile 和领域工具；当前角色分配通过种子数据或数据库更新完成。',
    },
  },
  {
    key: 'user',
    status: 'active',
    title: { en: 'USER', zh: 'USER 用户' },
    description: {
      en: 'Can use applications, profiles, chat, tools, traces, and token usage within owned scope.',
      zh: '可在自己的应用范围内使用应用、Profile、聊天、工具、Trace 和 Token 用量。',
    },
  },
]

export function getAdminStatusVariant(status: AdminPolicyStatus) {
  if (status === 'active') {
    return 'success'
  }

  if (status === 'manual') {
    return 'warning'
  }

  return 'muted'
}

export function getAdminStatusLabel(status: AdminPolicyStatus, locale: Locale) {
  const labels: Record<AdminPolicyStatus, Record<Locale, string>> = {
    active: { en: 'Active', zh: '已启用' },
    deferred: { en: 'Deferred', zh: '暂缓' },
    manual: { en: 'Manual', zh: '手动配置' },
  }

  return labels[status][locale]
}

export function summarizeCurrentUser(user: CurrentUser | null) {
  return {
    displayName: user?.displayName || '-',
    roleCount: user?.roles.length ?? 0,
    roles: user?.roles.length ? user.roles : ['-'],
    tenantId: user?.tenantId ? String(user.tenantId) : '-',
    userId: user?.userId ? String(user.userId) : '-',
    username: user?.username || '-',
  }
}
