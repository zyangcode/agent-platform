import { useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  BarChart3,
  Database,
  Gauge,
  RefreshCw,
  Sigma,
} from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { listApplications } from '@/features/applications/api'
import { listModelConfigs } from '@/lib/api/model-configs'
import { ApiError } from '@/lib/api/errors'
import type { Application, ModelConfig, PageResult, TokenUsage, TokenUsageSummary } from '@/lib/api/types'
import { formatDateTime } from '@/lib/format/date'
import { formatCompact, formatInteger } from '@/lib/format/number'
import { getTokenUsageSummary, listTokenUsages } from './api'
import {
  buildTokenSplitData,
  buildTopModelRows,
  formatTokenShare,
  getEstimateRatio,
  getUsageEstimateVariant,
} from './token-usage-utils'

type TokenUsageState =
  | {
      applications: PageResult<Application>
      error: null
      modelConfigs: ModelConfig[]
      status: 'ready'
      summary: TokenUsageSummary
      usages: PageResult<TokenUsage>
    }
  | {
      applications: null
      error: string | null
      modelConfigs: null
      status: 'error' | 'loading'
      summary: null
      usages: null
    }

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message
  }

  return 'Token usage data could not be loaded.'
}

function normalizeIdFilter(value: string) {
  return value === 'ALL' ? null : Number(value)
}

async function fetchTokenUsageForFilters(applicationFilter: string, modelConfigFilter: string) {
  try {
    const applicationId = normalizeIdFilter(applicationFilter)
    const modelConfigId = normalizeIdFilter(modelConfigFilter)
    const [applications, modelConfigs, summary, usages] = await Promise.all([
      listApplications(1, 50),
      listModelConfigs(),
      getTokenUsageSummary({ applicationId }),
      listTokenUsages({
        applicationId,
        modelConfigId,
        pageNo: 1,
        pageSize: 20,
      }),
    ])

    return {
      applications,
      error: null,
      modelConfigs,
      status: 'ready',
      summary,
      usages,
    } satisfies TokenUsageState
  } catch (error) {
    return {
      applications: null,
      error: getErrorMessage(error),
      modelConfigs: null,
      status: 'error',
      summary: null,
      usages: null,
    } satisfies TokenUsageState
  }
}

export function TokenUsagePage() {
  const [applicationFilter, setApplicationFilter] = useState('ALL')
  const [modelConfigFilter, setModelConfigFilter] = useState('ALL')
  const [state, setState] = useState<TokenUsageState>({
    applications: null,
    error: null,
    modelConfigs: null,
    status: 'loading',
    summary: null,
    usages: null,
  })

  async function loadTokenUsage(setLoading: boolean) {
    if (setLoading) {
      setState({
        applications: null,
        error: null,
        modelConfigs: null,
        status: 'loading',
        summary: null,
        usages: null,
      })
    }

    setState(await fetchTokenUsageForFilters(applicationFilter, modelConfigFilter))
  }

  useEffect(() => {
    let isMounted = true

    async function initializeTokenUsage() {
      const nextState = await fetchTokenUsageForFilters(applicationFilter, modelConfigFilter)

      if (isMounted) {
        setState(nextState)
      }
    }

    void initializeTokenUsage()

    return () => {
      isMounted = false
    }
  }, [applicationFilter, modelConfigFilter])

  const tokenSplit = useMemo(() => {
    return state.summary ? buildTokenSplitData(state.summary) : []
  }, [state.summary])

  const topModels = useMemo(() => {
    return state.summary ? buildTopModelRows(state.summary) : []
  }, [state.summary])

  const estimateRatio = state.summary ? getEstimateRatio(state.summary) : 0
  const hasUsage = state.status === 'ready' && state.usages.records.length > 0

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">Token Usage</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">
            Review quota service records, estimated usage, and model-level token concentration.
            Summary filters follow the Stage 2 Token Usage API.
          </p>
        </div>
        <Button onClick={() => loadTokenUsage(true)} variant="secondary">
          <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          Refresh
        </Button>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
          <AlertTitle>Token usage unavailable</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Usage filters</CardTitle>
          <CardDescription>
            Application affects both summary and detail. Model config filters detail records.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2">
            <FilterSelect
              label="Application"
              onChange={setApplicationFilter}
              options={[
                { label: 'All applications', value: 'ALL' },
                ...(state.applications?.records.map((application) => ({
                  label: application.name,
                  value: String(application.applicationId),
                })) ?? []),
              ]}
              value={applicationFilter}
            />
            <FilterSelect
              label="Model config"
              onChange={setModelConfigFilter}
              options={[
                { label: 'All models', value: 'ALL' },
                ...(state.modelConfigs?.map((model) => ({
                  label: `${model.displayName || model.modelName} · ${model.modelName}`,
                  value: String(model.modelConfigId),
                })) ?? []),
              ]}
              value={modelConfigFilter}
            />
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryMetric
          icon={Sigma}
          label="Total tokens"
          loading={state.status === 'loading'}
          value={state.summary ? formatCompact(state.summary.totalTokens) : '-'}
        />
        <SummaryMetric
          icon={Database}
          label="Requests"
          loading={state.status === 'loading'}
          value={state.summary ? formatInteger(state.summary.requestCount) : '-'}
        />
        <SummaryMetric
          icon={BarChart3}
          label="Prompt tokens"
          loading={state.status === 'loading'}
          value={state.summary ? formatCompact(state.summary.promptTokens) : '-'}
        />
        <SummaryMetric
          icon={Gauge}
          label="Estimated ratio"
          loading={state.status === 'loading'}
          value={`${estimateRatio}%`}
        />
      </div>

      <div className="grid gap-6 xl:grid-cols-[0.85fr_1.15fr]">
        <Card>
          <CardHeader>
            <CardTitle>Token composition</CardTitle>
            <CardDescription>Prompt versus completion usage for the selected application.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {state.status === 'loading' ? (
              <>
                <Skeleton className="h-24" />
                <Skeleton className="h-20" />
              </>
            ) : state.summary ? (
              <>
                <div className="rounded-2xl border border-white/10 bg-zinc-950/45 p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-sm text-zinc-500">Total tokens</p>
                      <p className="mt-2 font-mono text-3xl text-white">
                        {formatInteger(state.summary.totalTokens)}
                      </p>
                    </div>
                    <Badge variant={getUsageEstimateVariant(estimateRatio)}>
                      {state.summary.estimatedCount} estimated
                    </Badge>
                  </div>
                </div>

                <div className="space-y-3">
                  {tokenSplit.map((item) => (
                    <div key={item.label}>
                      <div className="mb-2 flex items-center justify-between gap-4 text-sm">
                        <span className="text-zinc-300">{item.label}</span>
                        <span className="font-mono text-zinc-500">
                          {formatInteger(item.tokens)} · {item.percent}%
                        </span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-white/[0.06]">
                        <div
                          className={
                            item.label === 'Prompt'
                              ? 'h-full rounded-full bg-cyan-200/70'
                              : 'h-full rounded-full bg-emerald-200/70'
                          }
                          style={{ width: `${item.percent}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <MiniMetric label="Real usage" value={formatInteger(state.summary.realUsageCount)} />
                  <MiniMetric label="Estimated" value={formatInteger(state.summary.estimatedCount)} />
                </div>
              </>
            ) : (
              <Alert variant="warning">
                <AlertTitle>Summary unavailable</AlertTitle>
                <AlertDescription>Resolve the API error and retry.</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Top models</CardTitle>
            <CardDescription>Models sorted by recorded token volume.</CardDescription>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-12" />
                <Skeleton className="h-12" />
                <Skeleton className="h-12" />
              </div>
            ) : topModels.length > 0 ? (
              <div className="space-y-3">
                {topModels.map((model) => (
                  <div
                    className="rounded-2xl border border-white/10 bg-zinc-950/35 p-4"
                    key={`${model.modelConfigId ?? 'model'}-${model.modelName ?? 'unknown'}`}
                  >
                    <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-white">{model.modelName || '-'}</p>
                        <p className="mt-1 text-xs text-zinc-500">{model.providerType || '-'}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant="muted">{model.requestCount} requests</Badge>
                        <span className="font-mono text-sm text-zinc-300">
                          {formatInteger(model.totalTokens)}
                        </span>
                      </div>
                    </div>
                    <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/[0.06]">
                      <div className="h-full rounded-full bg-cyan-200/70" style={{ width: `${model.share}%` }} />
                    </div>
                    <p className="mt-2 text-right font-mono text-xs text-zinc-500">{model.share}%</p>
                  </div>
                ))}
              </div>
            ) : (
              <Alert>
                <AlertTitle>No model usage yet</AlertTitle>
                <AlertDescription>
                  Start a Chat request after creating an Application and Profile. Model token
                  records will appear after Gateway settles usage.
                </AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Usage records</CardTitle>
          <CardDescription>
            {state.usages ? `${state.usages.total} token usage records matched` : 'Latest records'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {state.status === 'loading' ? (
            <div className="space-y-3">
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
            </div>
          ) : hasUsage ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Trace</TableHead>
                  <TableHead>Model</TableHead>
                  <TableHead>Prompt</TableHead>
                  <TableHead>Completion</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {state.usages.records.map((usage) => (
                  <TableRow key={usage.id}>
                    <TableCell>
                      <p className="max-w-[220px] truncate font-mono text-xs text-zinc-300">
                        {usage.traceId}
                      </p>
                    </TableCell>
                    <TableCell>
                      <p className="max-w-[190px] truncate text-sm text-zinc-200">
                        {usage.modelName || '-'}
                      </p>
                      <p className="mt-1 text-xs text-zinc-500">{usage.providerType || '-'}</p>
                    </TableCell>
                    <TableCell className="font-mono text-zinc-300">
                      {formatInteger(usage.promptTokens)}
                    </TableCell>
                    <TableCell className="font-mono text-zinc-300">
                      {formatInteger(usage.completionTokens)}
                    </TableCell>
                    <TableCell>
                      <span className="font-mono text-zinc-100">{formatInteger(usage.totalTokens)}</span>
                      <span className="ml-2 text-xs text-zinc-500">
                        {formatTokenShare(usage.totalTokens, state.summary?.totalTokens ?? 0)}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant={usage.estimated ? 'warning' : 'success'}>
                        {usage.estimated ? 'estimated' : 'real'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-zinc-400">{formatDateTime(usage.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Alert>
              <AlertTitle>No token usage records</AlertTitle>
              <AlertDescription>
                Browser Chat and API Key calls share the same quota settlement path. Records will
                appear after a successful or estimated model invocation.
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </section>
  )
}

type SummaryMetricProps = {
  icon: typeof Sigma
  label: string
  loading: boolean
  value: string
}

function SummaryMetric({ icon: Icon, label, loading, value }: SummaryMetricProps) {
  return (
    <Card className="bg-zinc-950/45">
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm text-zinc-400">{label}</span>
          <Icon className="h-4 w-4 text-cyan-200/80" strokeWidth={1.75} />
        </div>
        {loading ? (
          <Skeleton className="mt-6 h-8 w-24" />
        ) : (
          <p className="mt-5 font-mono text-3xl text-white">{value}</p>
        )}
      </CardContent>
    </Card>
  )
}

function MiniMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.035] p-4">
      <p className="text-xs text-zinc-500">{label}</p>
      <p className="mt-2 font-mono text-lg text-zinc-100">{value}</p>
    </div>
  )
}

type FilterSelectProps = {
  label: string
  onChange: (value: string) => void
  options: Array<{ label: string; value: string }>
  value: string
}

function FilterSelect({ label, onChange, options, value }: FilterSelectProps) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Select onValueChange={onChange} value={value}>
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
