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

export type NavItem = {
  title: string
  path: string
  icon: LucideIcon
  roles?: string[]
}

export type NavGroup = {
  title: string
  items: NavItem[]
}

export const navGroups: NavGroup[] = [
  {
    title: 'Workspace',
    items: [
      { title: 'Dashboard', path: '/', icon: Gauge },
      { title: 'Applications', path: '/applications', icon: KeyRound },
      { title: 'Profiles', path: '/profiles', icon: Bot },
      { title: 'Agent Chat', path: '/chat', icon: TerminalSquare },
      { title: 'Tools', path: '/tools', icon: Wrench },
    ],
  },
  {
    title: 'Observability',
    items: [
      { title: 'Traces', path: '/traces', icon: Activity },
      { title: 'Token Usage', path: '/token-usage', icon: Boxes },
    ],
  },
  {
    title: 'Admin',
    items: [
      { title: 'Model Configs', path: '/admin/models', icon: Settings, roles: ['ADMIN'] },
      { title: 'Security', path: '/admin/security', icon: Shield, roles: ['ADMIN'] },
      { title: 'Users', path: '/admin/users', icon: UserCog, roles: ['ADMIN'] },
    ],
  },
]

export function canAccessNavItem(userRoles: string[] | undefined, item: NavItem) {
  if (!item.roles?.length) {
    return true
  }

  const normalizedRoles = new Set(
    (userRoles ?? []).flatMap((role) => [role.toUpperCase(), role.replace(/^ROLE_/, '').toUpperCase()]),
  )

  return item.roles.some((role) => normalizedRoles.has(role.toUpperCase()))
}
