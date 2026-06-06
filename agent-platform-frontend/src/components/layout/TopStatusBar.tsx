import { Languages, LogOut } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/use-auth'
import { useI18n } from '@/lib/i18n/use-i18n'

export function TopStatusBar() {
  const { logout, user } = useAuth()
  const { locale, toggleLocale } = useI18n()
  const displayName = user?.displayName ?? user?.username ?? ''

  return (
    <div className="flex items-center justify-between h-8 shrink-0">
      <span className="text-xs text-text-muted truncate">
        {displayName}
      </span>
      <div className="flex items-center gap-1 shrink-0">
        <Button
          aria-label={locale === 'zh' ? 'Switch to English' : '切换到中文'}
          onClick={toggleLocale}
          size="sm"
          variant="ghost"
          className="h-7 px-2 text-xs text-text-muted hover:text-text"
        >
          <Languages className="h-3.5 w-3.5" strokeWidth={1.75} />
        </Button>
        <Button
          onClick={logout}
          size="sm"
          variant="ghost"
          className="h-7 px-2 text-xs text-text-muted hover:text-text"
        >
          <LogOut className="h-3.5 w-3.5" strokeWidth={1.75} />
        </Button>
      </div>
    </div>
  )
}
