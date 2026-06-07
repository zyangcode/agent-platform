import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import { Activity, AlertTriangle, Check, Copy, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { FilterSelect } from '@/components/ui/filter-select'
import { PaginationControls } from '@/components/ui/pagination-controls'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { listApplications } from '@/features/applications/api'
import { ApiError } from '@/lib/api/errors'
import type { Application, PageResult, TraceDetail, TraceSpan, TraceSummary } from '@/lib/api/types'
import { copyTextToClipboard } from '@/lib/clipboard'
import { formatDateTime, formatLatency } from '@/lib/format/date'
import { useI18n } from '@/lib/i18n/use-i18n'
import { getTrace, listTraces, type TraceEntrypointFilter, type TraceStatusFilter } from './api'
import { SpanDetailPanel } from './SpanDetailPanel'
import { TraceTimeline } from './TraceTimeline'
import {
  findTokenUsageForSpan,
  formatTraceStatus,
  formatTraceTokenCount,
  getTraceModelCallSummary,
  getTraceStatusVariant,
  sortTraceSpans,
} from './trace-utils'

type TraceListState =
  | {
      applications: PageResult<Application>
      error: null
      status: 'ready'
      traces: PageResult<TraceSummary>
    }
  | {
      applications: null
      error: string | null
      status: 'error' | 'loading'
      traces: null
    }

type TraceDetailState =
  | { detail: TraceDetail; error: null; status: 'ready' }
  | { detail: null; error: string | null; status: 'error' | 'idle' | 'loading' }

const STATUS_OPTIONS: Array<{ labelKey: string; value: TraceStatusFilter | 'ALL' }> = [
  { labelKey: 'common.allStatuses', value: 'ALL' },
  { labelKey: 'common.success', value: 'SUCCESS' },
  { labelKey: 'common.failed', value: 'FAILED' },
  { labelKey: 'common.running', value: 'RUNNING' },
]

const ENTRYPOINT_OPTIONS: Array<{ labelKey?: string; label?: string; value: TraceEntrypointFilter | 'ALL' }> = [
  { labelKey: 'common.allEntrypoints', value: 'ALL' },
  { label: 'Web', value: 'WEB' },
  { label: 'Internal Web', value: 'INTERNAL_WEB' },
  { label: 'API Key', value: 'API_KEY' },
]

function normalizeAllFilter<TValue extends string>(value: TValue | 'ALL') {
  return value === 'ALL' ? '' : value
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message
  }

  return 'Trace data could not be loaded.'
}

async function fetchTraceListForFilters(
  applicationFilter: string,
  entrypointFilter: TraceEntrypointFilter | 'ALL',
  statusFilter: TraceStatusFilter | 'ALL',
  pageNo: number,
) {
  try {
    const applicationId = applicationFilter === 'ALL' ? null : Number(applicationFilter)
    const [applications, traces] = await Promise.all([
      listApplications(1, 50),
      listTraces({
        applicationId,
        entrypoint: normalizeAllFilter(entrypointFilter) as TraceEntrypointFilter,
        pageNo,
        pageSize: 20,
        status: normalizeAllFilter(statusFilter) as TraceStatusFilter,
      }),
    ])

    return { applications, error: null, status: 'ready', traces } satisfies TraceListState
  } catch (error) {
    return {
      applications: null,
      error: getErrorMessage(error),
      status: 'error',
      traces: null,
    } satisfies TraceListState
  }
}

export function TraceListPage() {
  const { locale, t } = useI18n()
  const [applicationFilter, setApplicationFilter] = useState<string>('ALL')
  const [entrypointFilter, setEntrypointFilter] = useState<TraceEntrypointFilter | 'ALL'>('ALL')
  const [selectedSpan, setSelectedSpan] = useState<TraceSpan | null>(null)
  const [selectedTraceId, setSelectedTraceId] = useState<string | null>(null)
  const [pageNo, setPageNo] = useState(1)
  const [state, setState] = useState<TraceListState>({
    applications: null,
    error: null,
    status: 'loading',
    traces: null,
  })
  const [statusFilter, setStatusFilter] = useState<TraceStatusFilter | 'ALL'>('ALL')
  const [traceDetail, setTraceDetail] = useState<TraceDetailState>({
    detail: null,
    error: null,
    status: 'idle',
  })

  const selectedTrace = useMemo(() => {
    return state.traces?.records.find((trace) => trace.traceId === selectedTraceId) ?? null
  }, [selectedTraceId, state.traces])

  async function fetchTraceList(nextPageNo = pageNo) {
    return fetchTraceListForFilters(applicationFilter, entrypointFilter, statusFilter, nextPageNo)
  }

  async function loadTraceList(setLoading: boolean, nextPageNo = pageNo) {
    if (setLoading) {
      setState({ applications: null, error: null, status: 'loading', traces: null })
    }
    const nextState = await fetchTraceList(nextPageNo)
    setState(nextState)

    if (nextState.status === 'ready') {
      const nextTraceId = nextState.traces.records[0]?.traceId ?? null
      setSelectedTraceId(nextTraceId)

      if (nextTraceId) {
        await loadTraceDetail(nextTraceId)
      } else {
        setTraceDetail({ detail: null, error: null, status: 'idle' })
        setSelectedSpan(null)
      }
    }
  }

  async function loadTraceDetail(traceId: string) {
    setTraceDetail({ detail: null, error: null, status: 'loading' })
    setSelectedSpan(null)

    try {
      const detail = await getTrace(traceId)
      const firstSpan = sortTraceSpans(detail.spans)[0] ?? null
      setTraceDetail({ detail, error: null, status: 'ready' })
      setSelectedSpan(firstSpan)
    } catch (error) {
      setTraceDetail({ detail: null, error: getErrorMessage(error), status: 'error' })
      setSelectedSpan(null)
    }
  }

  function handleTraceSelect(traceId: string) {
    setSelectedTraceId(traceId)
    void loadTraceDetail(traceId)
  }

  function resetPageForFilter<TValue extends string>(onChange: Dispatch<SetStateAction<TValue>>) {
    return (value: string) => {
      setPageNo(1)
      onChange(value as TValue)
    }
  }

  function handlePageChange(nextPageNo: number) {
    setPageNo(nextPageNo)
  }

  useEffect(() => {
    let isMounted = true

    async function initializeTraces() {
      const nextState = await fetchTraceListForFilters(applicationFilter, entrypointFilter, statusFilter, pageNo)

      if (!isMounted) {
        return
      }

      setState(nextState)

      if (nextState.status === 'ready') {
        const firstTraceId = nextState.traces.records[0]?.traceId ?? null
        setSelectedTraceId(firstTraceId)

        if (firstTraceId) {
          try {
            const detail = await getTrace(firstTraceId)
            if (isMounted) {
              setTraceDetail({ detail, error: null, status: 'ready' })
              setSelectedSpan(sortTraceSpans(detail.spans)[0] ?? null)
            }
          } catch (error) {
            if (isMounted) {
              setTraceDetail({ detail: null, error: getErrorMessage(error), status: 'error' })
            }
          }
        }
      }
    }

    void initializeTraces()

    return () => {
      isMounted = false
    }
  }, [applicationFilter, entrypointFilter, pageNo, statusFilter])

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">{t('trace.title')}</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">
            {t('trace.intro')}
          </p>
        </div>
        <Button onClick={() => loadTraceList(true)} variant="secondary">
          <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          {t('common.refresh')}
        </Button>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
          <AlertTitle>{t('trace.traceUnavailable')}</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>{t('trace.filters')}</CardTitle>
          <CardDescription>{t('trace.filterDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-3">
            <FilterSelect
              label={t('nav.applications')}
              onChange={resetPageForFilter(setApplicationFilter)}
              options={[
                { label: t('common.allApplications'), value: 'ALL' },
                ...(state.applications?.records.map((application) => ({
                  label: application.name,
                  value: String(application.applicationId),
                })) ?? []),
              ]}
              value={applicationFilter}
            />
            <FilterSelect
              label={t('trace.status')}
              onChange={resetPageForFilter(setStatusFilter)}
              options={STATUS_OPTIONS.map((option) => ({ label: t(option.labelKey), value: option.value }))}
              value={statusFilter}
            />
            <FilterSelect
              label={t('trace.entrypoint')}
              onChange={resetPageForFilter(setEntrypointFilter)}
              options={ENTRYPOINT_OPTIONS.map((option) => ({
                label: option.labelKey ? t(option.labelKey) : option.label ?? option.value,
                value: option.value,
              }))}
              value={entrypointFilter}
            />
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <Card className="min-w-0">
          <CardHeader>
            <div className="flex items-start justify-between gap-4">
              <div>
                <CardTitle>{t('trace.list')}</CardTitle>
                <CardDescription>
                  {state.traces ? t('trace.listMatched', { total: state.traces.total }) : t('trace.listFallback')}
                </CardDescription>
              </div>
              <Activity className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
            </div>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : state.status === 'ready' && state.traces.records.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('trace.traceLabel')}</TableHead>
                    <TableHead>{t('trace.status')}</TableHead>
                    <TableHead>{t('trace.mode')}</TableHead>
                    <TableHead>{t('trace.latency')}</TableHead>
                    <TableHead>{t('trace.started')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.traces.records.map((trace) => (
                    <TableRow
                      data-state={selectedTraceId === trace.traceId ? 'selected' : undefined}
                      key={trace.traceId}
                      onClick={() => handleTraceSelect(trace.traceId)}
                    >
                      <TableCell>
                        <p className="max-w-[220px] truncate font-mono text-xs text-zinc-200">{trace.traceId}</p>
                        <p className="mt-1 text-xs text-zinc-500">{trace.entrypoint || '-'}</p>
                      </TableCell>
                      <TableCell>
                        <Badge variant={getTraceStatusVariant(trace.status)}>
                          {formatTraceStatus(trace.status, locale)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-zinc-400">{trace.agentMode || '-'}</TableCell>
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
                <AlertTitle>{t('trace.emptyTitle')}</AlertTitle>
                <AlertDescription>{t('trace.emptyDescription')}</AlertDescription>
              </Alert>
            )}
            {state.status === 'ready' && state.traces.total > 0 ? (
              <PaginationControls
                onPageChange={handlePageChange}
                pageNo={state.traces.pageNo}
                total={state.traces.total}
                totalPages={state.traces.totalPages}
              />
            ) : null}
          </CardContent>
        </Card>

        <TraceDetailView
          detailState={traceDetail}
          selectedTrace={selectedTrace}
          selectedSpan={selectedSpan}
          setSelectedSpan={setSelectedSpan}
        />
      </div>
    </section>
  )
}

type TraceDetailViewProps = {
  detailState: TraceDetailState
  selectedSpan: TraceSpan | null
  selectedTrace: TraceSummary | null
  setSelectedSpan: (span: TraceSpan) => void
}

function TraceDetailView({
  detailState,
  selectedSpan,
  selectedTrace,
  setSelectedSpan,
}: TraceDetailViewProps) {
  const { locale, t } = useI18n()
  const [copiedTraceId, setCopiedTraceId] = useState<string | null>(null)

  async function copyTraceId(traceId: string) {
    const copied = await copyTextToClipboard(traceId)

    if (copied) {
      setCopiedTraceId(traceId)
      window.setTimeout(() => setCopiedTraceId(null), 1200)
    }
  }

  if (detailState.status === 'idle') {
    return (
      <Card className="min-w-0">
        <CardHeader>
          <CardTitle>{t('trace.detail')}</CardTitle>
          <CardDescription>{t('trace.selectTraceToInspect')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert>
            <AlertTitle>{t('trace.selectTraceTitle')}</AlertTitle>
            <AlertDescription>{t('trace.selectTraceDescription')}</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  if (detailState.status === 'loading') {
    return (
      <Card className="min-w-0">
        <CardHeader>
          <CardTitle>{t('trace.detail')}</CardTitle>
          <CardDescription className="break-all font-mono">
            {selectedTrace?.traceId ?? t('common.loading')}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Skeleton className="h-20" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
        </CardContent>
      </Card>
    )
  }

  if (detailState.status === 'error') {
    return (
      <Card className="min-w-0">
        <CardHeader>
          <CardTitle>{t('trace.detail')}</CardTitle>
          <CardDescription className="break-all font-mono">{selectedTrace?.traceId ?? '-'}</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert variant="warning">
            <AlertTitle>{t('trace.detailUnavailable')}</AlertTitle>
            <AlertDescription>
              {detailState.error || t('trace.detailUnavailableDescription')}
            </AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  const detail = detailState.detail
  const selectedTokenUsage = findTokenUsageForSpan(selectedSpan, detail?.tokenUsages ?? [])
  const modelCallSummary = detail ? getTraceModelCallSummary(detail.spans, detail.tokenUsages) : null

  if (!detail) {
    return (
      <Card className="min-w-0">
        <CardHeader>
          <CardTitle>{t('trace.detail')}</CardTitle>
          <CardDescription className="break-all font-mono">{selectedTrace?.traceId ?? '-'}</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert variant="warning">
            <AlertTitle>{t('trace.detailMissing')}</AlertTitle>
            <AlertDescription>{t('trace.detailMissingDescription')}</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid min-w-0 gap-6 2xl:grid-cols-[minmax(0,1fr)_minmax(300px,360px)]">
      <Card className="min-w-0">
        <CardHeader>
          <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <CardTitle>{t('trace.detail')}</CardTitle>
              <div className="mt-1 flex min-w-0 items-start gap-2">
                <CardDescription className="min-w-0 break-all font-mono">{detail.traceId}</CardDescription>
                <Button
                  className="h-7 w-7 shrink-0"
                  onClick={() => copyTraceId(detail.traceId)}
                  size="icon"
                  variant="ghost"
                >
                  {copiedTraceId === detail.traceId ? (
                    <Check className="h-3.5 w-3.5 text-emerald-200" strokeWidth={1.75} />
                  ) : (
                    <Copy className="h-3.5 w-3.5" strokeWidth={1.75} />
                  )}
                </Button>
              </div>
            </div>
            <Badge className="w-fit shrink-0" variant={getTraceStatusVariant(detail.status)}>
              {formatTraceStatus(detail.status, locale)}
            </Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-3 md:grid-cols-3">
            <Metric label={t('trace.latency')} value={formatLatency(detail.latencyMs)} />
            <Metric label={t('common.tokens')} value={formatTraceTokenCount(detail.totalTokens)} />
            <Metric label={t('trace.mode')} value={detail.agentMode || '-'} />
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            <Metric label={t('trace.userId')} value={detail.userId ? `#${detail.userId}` : '-'} />
            <Metric label={t('trace.applicationId')} value={detail.applicationId ? `#${detail.applicationId}` : '-'} />
            <Metric label={t('trace.entrypoint')} value={detail.entrypoint || '-'} />
          </div>
          {modelCallSummary ? (
            <div className="grid gap-3 md:grid-cols-4">
              <Metric label={t('trace.modelCallCount')} value={String(modelCallSummary.modelCallCount)} />
              <Metric label={t('trace.modelTotalTokens')} value={formatTraceTokenCount(modelCallSummary.totalTokens)} />
              <Metric label={t('trace.estimatedRatio')} value={`${modelCallSummary.estimatedRatio}%`} />
              <Metric label={t('trace.slowestModelCall')} value={formatLatency(modelCallSummary.slowestLatencyMs)} />
            </div>
          ) : null}

          {detail.errorMessage ? (
            <Alert variant="danger">
              <AlertTitle className="break-words">{detail.errorCode || t('trace.traceFailed')}</AlertTitle>
              <AlertDescription className="break-words">{detail.errorMessage}</AlertDescription>
            </Alert>
          ) : null}

          <div className="min-w-0 rounded-2xl border border-white/10 bg-zinc-950/45 p-4">
            <p className="mb-4 text-sm font-medium text-white">{t('trace.spanTimeline')}</p>
            <TraceTimeline
              onSelectSpan={setSelectedSpan}
              selectedSpanId={selectedSpan?.id}
              spans={detail.spans}
              tokenUsages={detail.tokenUsages}
            />
          </div>

          <div className="min-w-0 rounded-2xl border border-white/10 bg-zinc-950/45 p-4">
            <p className="text-sm font-medium text-white">{t('trace.tokenUsageRecords')}</p>
            {detail.tokenUsages.length === 0 ? (
              <p className="mt-3 text-sm text-zinc-500">{t('trace.noTokenUsage')}</p>
            ) : (
              <div className="mt-3 space-y-2">
                {detail.tokenUsages.map((usage) => (
                  <div
                    className="grid min-w-0 gap-2 rounded-xl border border-white/10 bg-white/[0.035] p-3 text-xs md:grid-cols-[minmax(0,1.2fr)_minmax(0,0.9fr)_minmax(0,0.9fr)_auto]"
                    key={usage.id}
                  >
                    <span className="break-all font-mono text-zinc-300">{usage.modelName || '-'}</span>
                    <span className="break-all text-zinc-500">{usage.providerType || '-'}</span>
                    <span className="font-mono text-zinc-300">
                      {formatTraceTokenCount(usage.totalTokens)} {t('common.tokensUnit')}
                    </span>
                    <span className="text-zinc-500">{usage.estimated ? t('common.estimated') : t('common.real')}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      <SpanDetailPanel span={selectedSpan} tokenUsage={selectedTokenUsage} />
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-xl border border-white/10 bg-white/[0.035] p-3">
      <p className="text-xs text-zinc-500">{label}</p>
      <p className="mt-2 break-all font-mono text-sm text-white">{value}</p>
    </div>
  )
}
