import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/use-auth'
import { useI18n } from '@/lib/i18n/use-i18n'
import { canAccessNavItem, navGroups } from './navigation'

export function Sidebar() {
  const { user } = useAuth()
  const { t } = useI18n()
  const [collapsed, setCollapsed] = useState(false)

  return (
    <aside
      className={`glass-panel flex flex-col p-3 transition-all duration-300 ${
        collapsed ? 'w-[56px]' : 'w-[260px]'
      }`}
    >
      {/* Logo + collapse toggle */}
      <div className="flex items-center gap-2 mb-4">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[rgba(56,189,248,0.25)] bg-[rgba(56,189,248,0.1)]">
          <span className="text-xs font-bold text-accent-cyan">N</span>
        </div>
        {!collapsed && (
          <span className="text-sm font-semibold text-text tracking-wide">
            Nexus
          </span>
        )}
        <Button
          variant="ghost"
          size="icon"
          className={`h-7 w-7 text-text-muted hover:text-text ${collapsed ? 'ml-auto' : 'ml-auto'}`}
          onClick={() => setCollapsed((c) => !c)}
        >
          {collapsed ? (
            <PanelLeftOpen className="h-4 w-4" strokeWidth={1.75} />
          ) : (
            <PanelLeftClose className="h-4 w-4" strokeWidth={1.75} />
          )}
        </Button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto space-y-5">
        {navGroups.map((group) => {
          const visibleItems = group.items.filter((item) => canAccessNavItem(user?.roles, item))
          if (visibleItems.length === 0) return null

          return (
            <div key={group.title}>
              {!collapsed && (
                <p className="mb-1.5 px-2 text-[10px] uppercase tracking-[0.15em] text-text-faint">
                  {t(group.labelKey)}
                </p>
              )}
              <div className="space-y-0.5">
                {visibleItems.map((item) => {
                  const Icon = item.icon
                  return (
                    <NavLink
                      className={({ isActive }) =>
                        [
                          'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition',
                          isActive
                            ? 'border border-[rgba(96,165,250,0.26)] bg-[rgba(59,130,246,0.15)] text-text'
                            : 'border border-transparent text-text-muted hover:border-[rgba(148,163,184,0.1)] hover:bg-surface-soft hover:text-text',
                          collapsed ? 'justify-center' : '',
                        ].join(' ')
                      }
                      end={item.path === '/'}
                      key={item.path}
                      to={item.path}
                      title={collapsed ? t(item.labelKey) : undefined}
                    >
                      <Icon className="h-4 w-4 shrink-0" strokeWidth={1.75} />
                      {!collapsed && <span>{t(item.labelKey)}</span>}
                    </NavLink>
                  )
                })}
              </div>
            </div>
          )
        })}
      </nav>

      {/* Footer */}
      <div className="pt-3 border-t border-[rgba(148,163,184,0.1)]">
        {collapsed ? (
          <div className="flex justify-center">
            <span className="text-[10px] text-text-faint font-mono">
              {user?.roles?.[0]?.charAt(0) ?? 'U'}
            </span>
          </div>
        ) : (
          <p className="text-[10px] text-text-faint px-2 font-mono">
            {user?.roles?.join(', ') || 'USER'}
          </p>
        )}
      </div>
    </aside>
  )
}
