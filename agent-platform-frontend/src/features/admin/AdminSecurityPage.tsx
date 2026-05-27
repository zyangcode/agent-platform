import { DatabaseZap, LockKeyhole, Radar, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useI18n } from '@/lib/i18n/use-i18n'
import { getAdminStatusLabel, getAdminStatusVariant, securityPolicyItems } from './admin-static-data'

const scanRules = ['PHONE', 'EMAIL', 'ID_CARD', 'API_KEY_PATTERN']

export function AdminSecurityPage() {
  const { locale, t } = useI18n()

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">{t('admin.securityTitle')}</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">{t('admin.securityIntro')}</p>
        </div>
        <Badge variant="success">{t('admin.adminOnly')}</Badge>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)]">
        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
                <ShieldCheck className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
              </div>
              <div>
                <CardTitle>{t('admin.securityChain')}</CardTitle>
                <CardDescription>{t('admin.securityChainDescription')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="divide-y divide-white/10 rounded-2xl border border-white/10 bg-zinc-950/40">
              {securityPolicyItems.map((item) => (
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

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-emerald-200/20 bg-emerald-300/10">
                  <Radar className="h-5 w-5 text-emerald-100" strokeWidth={1.75} />
                </div>
                <div>
                  <CardTitle>{t('admin.scanRules')}</CardTitle>
                  <CardDescription>{t('admin.scanRulesDescription')}</CardDescription>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {scanRules.map((rule) => (
                  <Badge key={rule} variant="muted">
                    {rule}
                  </Badge>
                ))}
              </div>
            </CardContent>
          </Card>

          <Alert>
            <DatabaseZap className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
            <AlertTitle>{t('admin.noRawSensitiveText')}</AlertTitle>
            <AlertDescription>{t('admin.noRawSensitiveTextDescription')}</AlertDescription>
          </Alert>

          <Alert variant="warning">
            <LockKeyhole className="mb-3 h-5 w-5 text-amber-100" strokeWidth={1.75} />
            <AlertTitle>{t('admin.mvpBoundary')}</AlertTitle>
            <AlertDescription>{t('admin.securityBoundaryDescription')}</AlertDescription>
          </Alert>
        </div>
      </div>
    </section>
  )
}
