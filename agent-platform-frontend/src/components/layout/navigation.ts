import {
  Activity,
  Bot,
  Boxes,
  Gauge,
  KeyRound,
  Settings,
  Shield,
  TerminalSquare,
  UserCog,
  Wrench,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { hasAnyRequiredRole } from '@/app/role-guard-utils'

export type NavItem = {
  labelKey: string
  title: string
  path: string
  icon: LucideIcon
  roles?: string[]
}

export type NavGroup = {
  labelKey: string
  title: string
  items: NavItem[]
}

export const navGroups: NavGroup[] = [
  {
    labelKey: 'nav.workspace',
    title: 'Workspace',
    items: [
      { labelKey: 'nav.dashboard', title: 'Dashboard', path: '/', icon: Gauge },
      { labelKey: 'nav.applications', title: 'Applications', path: '/applications', icon: KeyRound },
      { labelKey: 'nav.profiles', title: 'Profiles', path: '/profiles', icon: Bot },
      { labelKey: 'nav.agentChat', title: 'Agent Chat', path: '/chat', icon: TerminalSquare },
      { labelKey: 'nav.tools', title: 'Tools', path: '/tools', icon: Wrench },
    ],
  },
  {
    labelKey: 'nav.observability',
    title: 'Observability',
    items: [
      { labelKey: 'nav.traces', title: 'Traces', path: '/traces', icon: Activity },
      { labelKey: 'nav.tokenUsage', title: 'Token Usage', path: '/token-usage', icon: Boxes },
    ],
  },
  {
    labelKey: 'nav.admin',
    title: 'Admin',
    items: [
      { labelKey: 'nav.modelConfigs', title: 'Model Configs', path: '/admin/models', icon: Settings, roles: ['ADMIN'] },
      { labelKey: 'nav.security', title: 'Security', path: '/admin/security', icon: Shield, roles: ['ADMIN'] },
      { labelKey: 'nav.users', title: 'Users', path: '/admin/users', icon: UserCog, roles: ['ADMIN'] },
    ],
  },
]

export function canAccessNavItem(userRoles: string[] | undefined, item: NavItem) {
  return hasAnyRequiredRole(userRoles, item.roles)
}
