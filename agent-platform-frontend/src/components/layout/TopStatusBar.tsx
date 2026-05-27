import { Languages, LogOut, RadioTower } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/use-auth'
import { useI18n } from '@/lib/i18n/use-i18n'

export function TopStatusBar() {
  const { logout, user } = useAuth()
  const { locale, t, toggleLocale } = useI18n()
  const displayName = user?.displayName ?? user?.username ?? ''
  const username = user?.username ?? ''

  return (
    <header className="flex flex-col gap-4 border-b border-white/10 pb-5 md:flex-row md:items-center md:justify-between">
      <div>
        <p className="text-xs uppercase tracking-[0.22em] text-cyan-200/70">
          {t('layout.consoleEyebrow')}
        </p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-white">
          {t('layout.operationsWorkspace')}
        </h1>
        <p className="mt-2 text-sm text-zinc-500">
          {t('layout.userLine', { displayName, username })}
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Badge variant="success">
          <RadioTower className="mr-1.5 h-3.5 w-3.5" strokeWidth={1.75} />
          {t('common.webOnline')}
        </Badge>
        <Button
          aria-label={locale === 'zh' ? 'Switch to English' : '切换到中文'}
          onClick={toggleLocale}
          size="sm"
          variant="secondary"
        >
          <Languages className="h-4 w-4" strokeWidth={1.75} />
          {locale === 'zh' ? '中文' : 'EN'}
        </Button>
        <Button onClick={logout} size="sm" variant="secondary">
          <LogOut className="h-4 w-4" strokeWidth={1.75} />
          {t('common.signOut')}
        </Button>
      </div>
    </header>
  )
}
