import { NavLink } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { useAuth } from '@/features/auth/use-auth'
import { canAccessNavItem, navGroups } from './navigation'

export function Sidebar() {
  const { user } = useAuth()

  return (
    <Card className="lg:sticky lg:top-6 lg:h-[calc(100dvh-3rem)]">
      <aside className="flex h-full flex-col p-4">
        <div className="border-b border-white/10 pb-5">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10 text-sm font-semibold text-cyan-100">
            AP
          </div>
          <p className="mt-4 text-xs uppercase tracking-[0.22em] text-cyan-200/70">
            Agent Platform
          </p>
          <p className="mt-2 text-sm text-zinc-500">AI Infra Console</p>
        </div>

        <nav className="mt-5 flex-1 space-y-6 overflow-y-auto pr-1">
          {navGroups.map((group) => {
            const visibleItems = group.items.filter((item) => canAccessNavItem(user?.roles, item))

            if (visibleItems.length === 0) {
              return null
            }

            return (
              <div key={group.title}>
                <p className="mb-2 px-2 text-[11px] uppercase tracking-[0.18em] text-zinc-600">
                  {group.title}
                </p>
                <div className="space-y-1">
                  {visibleItems.map((item) => {
                    const Icon = item.icon

                    return (
                      <NavLink
                        className={({ isActive }) =>
                          [
                            'flex items-center gap-3 rounded-xl border px-3 py-2 text-sm transition-[background,border-color,color,transform] duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] active:translate-y-px',
                            isActive
                              ? 'border-cyan-200/20 bg-cyan-300/10 text-cyan-50'
                              : 'border-transparent text-zinc-400 hover:border-white/10 hover:bg-white/[0.045] hover:text-white',
                          ].join(' ')
                        }
                        end={item.path === '/'}
                        key={item.path}
                        to={item.path}
                      >
                        <Icon className="h-4 w-4" strokeWidth={1.75} />
                        <span>{item.title}</span>
                      </NavLink>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </nav>

        <div className="mt-5 border-t border-white/10 pt-4">
          <Badge variant="muted">{user?.roles.join(', ') || 'USER'}</Badge>
        </div>
      </aside>
    </Card>
  )
}
