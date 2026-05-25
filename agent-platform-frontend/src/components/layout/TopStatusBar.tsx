import { LogOut, RadioTower } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/use-auth'

export function TopStatusBar() {
  const { logout, user } = useAuth()

  return (
    <header className="flex flex-col gap-4 border-b border-white/10 pb-5 md:flex-row md:items-center md:justify-between">
      <div>
        <p className="text-xs uppercase tracking-[0.22em] text-cyan-200/70">Console</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-white">Operations workspace</h1>
        <p className="mt-2 text-sm text-zinc-500">
          {user?.displayName ?? user?.username} · {user?.username}
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Badge variant="success">
          <RadioTower className="mr-1.5 h-3.5 w-3.5" strokeWidth={1.75} />
          Web online
        </Badge>
        <Button onClick={logout} size="sm" variant="secondary">
          <LogOut className="h-4 w-4" strokeWidth={1.75} />
          Sign out
        </Button>
      </div>
    </header>
  )
}
