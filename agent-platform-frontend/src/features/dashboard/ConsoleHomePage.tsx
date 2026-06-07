import { useCallback, useEffect, useMemo, useState } from 'react'
import { Activity, AlertTriangle, Database, KeyRound, RefreshCw, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/lib/api/errors'
import { formatDateTime, formatLatency } from '@/lib/format/date'
import { formatCompact, formatInteger } from '@/lib/format/number'
import { useI18n } from '@/lib/i18n/use-i18n'
import { getDashboardData, type DashboardData } from './api'

type DashboardState =
  | { data: DashboardData; error: null; status: 'ready' }
  | { data: null; error: string | null; status: 'error' | 'loading' }

function getErrorMessage(error: unknown, t: (key: string) => string) {
  if (error instanceof ApiError) {
    if (error.kind === 'unauthorized') {
      return t('dashboard.sessionExpired')
    }
    if (error.kind === 'quota_exceeded') {
      return t('dashboard.quotaExhausted')
    }

    return error.message
  }

  return t('dashboard.dataLoadFailed')
}

function getStatusVariant(status: string | null | undefined) {
  const normalized = status?.toUpperCase()

  if (normalized === 'SUCCESS' || normalized === 'COMPLETED') {
    return 'success'
  }
  if (normalized === 'FAILED' || normalized === 'ERROR' || normalized === 'MODEL_ERROR') {
    return 'danger'
  }
  if (normalized === 'RUNNING') {
    return 'default'
  }

  return 'muted'
}

export function ConsoleHomePage() {
  const { t } = useI18n()
  const [state, setState] = useState<DashboardState>({
    data: null,
    error: null,
    status: 'loading',
  })

  const fetchDashboardData = useCallback(async () => {
    try {
      const data = await getDashboardData()
      return { data, error: null, status: 'ready' } satisfies DashboardState
    } catch (error) {
      return { data: null, error: getErrorMessage(error, t), status: 'error' } satisfies DashboardState
    }
  }, [t])

  async function refreshDashboard(setLoading: boolean) {
    if (setLoading) {
      setState({ data: null, error: null, status: 'loading' })
    }
    setState(await fetchDashboardData())
  }

  async function loadDashboard() {
    await refreshDashboard(true)
  }

  useEffect(() => {
    let isMounted = true

    async function initializeDashboard() {
      if (isMounted) {
        setState({ data: null, error: null, status: 'loading' })
      }

      const nextState = await fetchDashboardData()

      if (isMounted) {
        setState(nextState)
      }
    }

    void initializeDashboard()

    return () => {
      isMounted = false
    }
  }, [fetchDashboardData])

  const successRate = useMemo(() => {
    const records = state.data?.recentTraces?.records ?? []
    if (records.length === 0) return '-'
    const succeeded = records.filter((trace) => trace.status === 'SUCCESS').length
    return Math.round((succeeded / records.length) * 100) + '%'
  }, [state.data])

  const metrics = useMemo(() => {
    const data = state.data

    return [
      {
        icon: KeyRound,
        label: t('dashboard.applications'),
        value: data ? formatInteger(data.applications.total) : '-',
      },
      {
        icon: Activity,
        label: t('dashboard.recentTraces'),
        value: data ? formatInteger(data.recentTraces.total) : '-',
      },
      {
        icon: ShieldCheck,
        label: t('dashboard.successRate'),
        value: successRate,
      },
      {
        icon: Database,
        label: t('dashboard.tokenUsage'),
        value: data ? formatCompact(data.tokenSummary.totalTokens) : '-',
      },
    ]
  }, [state.data, successRate, t])

  return (
    <section className="space-y-6">
      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
          <AlertTitle>{t('dashboard.unavailable')}</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
          <Button className="mt-4" onClick={loadDashboard} size="sm" variant="secondary">
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
            {t('dashboard.retry')}
          </Button>
        </Alert>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {metrics.map((metric) => {
          const Icon = metric.icon

          return (
            <Card className="bg-zinc-950/45" key={metric.label}>
              <CardContent className="p-5">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-zinc-400">{metric.label}</span>
                  <Icon className="h-4 w-4 text-cyan-200/80" strokeWidth={1.75} />
                </div>
                {state.status === 'loading' ? (
                  <Skeleton className="mt-6 h-8 w-24" />
                ) : (
                  <p className="mt-5 font-mono text-3xl text-white">{metric.value}</p>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>

      {state.status === 'ready' && state.data.applications.total === 0 ? (
        <Alert>
          <AlertTitle>{t('dashboard.noApplicationsYet')}</AlertTitle>
          <AlertDescription>{t('dashboard.noApplicationsDescription')}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[1.35fr_0.65fr]">
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.recentTraces')}</CardTitle>
            <CardDescription>{t('dashboard.recentTracesDescription')}</CardDescription>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : state.status === 'ready' && state.data.recentTraces.records.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('trace.traceLabel')}</TableHead>
                    <TableHead>{t('trace.status')}</TableHead>
                    <TableHead>{t('trace.latency')}</TableHead>
                    <TableHead>{t('dashboard.started')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.data.recentTraces.records.map((trace) => (
                    <TableRow key={trace.traceId}>
                      <TableCell className="max-w-[220px] truncate font-mono text-xs text-zinc-300">
                        {trace.traceId}
                      </TableCell>
                      <TableCell>
                        <Badge variant={getStatusVariant(trace.status)}>{trace.status}</Badge>
                      </TableCell>
                      <TableCell className="font-mono text-zinc-300">
                        {formatLatency(trace.latencyMs)}
                      </TableCell>
                      <TableCell className="text-zinc-400">{formatDateTime(trace.startedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <Alert>
                <AlertTitle>{t('dashboard.noTraces')}</AlertTitle>
                <AlertDescription>{t('dashboard.noTracesDescription')}</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.tokenSummary')}</CardTitle>
            <CardDescription>{t('dashboard.tokenSummaryDescription')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {state.status === 'loading' ? (
              <>
                <Skeleton className="h-16" />
                <Skeleton className="h-16" />
              </>
            ) : state.status === 'ready' ? (
              <>
                <div className="rounded-2xl border border-white/10 bg-zinc-950/45 p-4">
                  <p className="text-sm text-zinc-500">{t('dashboard.totalTokens')}</p>
                  <p className="mt-2 font-mono text-3xl text-white">
                    {formatInteger(state.data.tokenSummary.totalTokens)}
                  </p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="rounded-2xl border border-white/10 bg-white/[0.035] p-4">
                    <p className="text-xs text-zinc-500">{t('dashboard.prompt')}</p>
                    <p className="mt-2 font-mono text-lg text-zinc-100">
                      {formatInteger(state.data.tokenSummary.promptTokens)}
                    </p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/[0.035] p-4">
                    <p className="text-xs text-zinc-500">{t('dashboard.completion')}</p>
                    <p className="mt-2 font-mono text-lg text-zinc-100">
                      {formatInteger(state.data.tokenSummary.completionTokens)}
                    </p>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Badge variant="muted">{state.data.tokenSummary.requestCount} requests</Badge>
                  <Badge variant="default">{state.data.tokenSummary.estimatedCount} {t('common.estimated')}</Badge>
                  <Badge variant="success">{state.data.tokenSummary.realUsageCount} {t('common.real')}</Badge>
                </div>
              </>
            ) : (
              <Alert variant="warning">
                <AlertTitle>{t('dashboard.tokenDataUnavailable')}</AlertTitle>
                <AlertDescription>{t('dashboard.tokenDataUnavailableDescription')}</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  )
}
