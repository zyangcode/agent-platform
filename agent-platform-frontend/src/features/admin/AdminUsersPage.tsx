import { Fingerprint, Shield, UserCog, UsersRound } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/use-auth'
import { useI18n } from '@/lib/i18n/use-i18n'
import {
  getAdminStatusLabel,
  getAdminStatusVariant,
  roleCapabilityItems,
  summarizeCurrentUser,
} from './admin-static-data'

export function AdminUsersPage() {
  const { user } = useAuth()
  const { locale, t } = useI18n()
  const summary = summarizeCurrentUser(user)

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">{t('admin.usersTitle')}</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">{t('admin.usersIntro')}</p>
        </div>
        <Badge variant="success">{t('admin.adminOnly')}</Badge>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(320px,0.82fr)_minmax(0,1.18fr)]">
        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
                <Fingerprint className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
              </div>
              <div>
                <CardTitle>{t('admin.currentAccount')}</CardTitle>
                <CardDescription>{t('admin.currentAccountDescription')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid gap-3 sm:grid-cols-2">
              <UserFact label={t('admin.displayName')} value={summary.displayName} />
              <UserFact label={t('admin.username')} value={summary.username} />
              <UserFact label={t('admin.userId')} value={summary.userId} />
              <UserFact label={t('admin.tenantId')} value={summary.tenantId} />
              <UserFact label={t('admin.roleCount')} value={String(summary.roleCount)} />
              <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
                <p className="text-xs text-zinc-500">{t('admin.roles')}</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {summary.roles.map((role) => (
                    <Badge key={role} variant={role === 'ADMIN' ? 'success' : 'muted'}>
                      {role}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-emerald-200/20 bg-emerald-300/10">
                <UsersRound className="h-5 w-5 text-emerald-100" strokeWidth={1.75} />
              </div>
              <div>
                <CardTitle>{t('admin.roleModel')}</CardTitle>
                <CardDescription>{t('admin.roleModelDescription')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="divide-y divide-white/10 rounded-2xl border border-white/10 bg-zinc-950/40">
              {roleCapabilityItems.map((item) => (
                <div className="grid gap-3 p-4 md:grid-cols-[1fr_auto]" key={item.key}>
                  <div>
                    <p className="font-medium text-white">{item.title[locale]}</p>
                    <p className="mt-1 text-sm leading-6 text-zinc-500">{item.description[locale]}</p>
                  </div>
                  <div className="flex items-start">
                    <Badge variant={getAdminStatusVariant(item.status)}>
                      {getAdminStatusLabel(item.status, locale)}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Alert>
          <Shield className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
          <AlertTitle>{t('admin.adminOnly')}</AlertTitle>
          <AlertDescription>{t('admin.adminOnlyDescription')}</AlertDescription>
        </Alert>
        <Alert variant="warning">
          <UserCog className="mb-3 h-5 w-5 text-amber-100" strokeWidth={1.75} />
          <AlertTitle>{t('admin.mvpBoundary')}</AlertTitle>
          <AlertDescription>{t('admin.userBoundaryDescription')}</AlertDescription>
        </Alert>
      </div>
    </section>
  )
}

function UserFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
      <p className="text-xs text-zinc-500">{label}</p>
      <p className="mt-2 truncate font-mono text-sm text-white">{value}</p>
    </div>
  )
}
