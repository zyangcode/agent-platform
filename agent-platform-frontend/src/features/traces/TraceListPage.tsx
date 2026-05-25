import { useEffect, useMemo, useState } from 'react'
import { Activity, AlertTriangle, RefreshCw } from 'lucide-react'
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
import { formatDateTime, formatLatency } from '@/lib/format/date'
import { getTrace, listTraces, type TraceEntrypointFilter, type TraceStatusFilter } from './api'
import { SpanDetailPanel } from './SpanDetailPanel'
import { TraceTimeline } from './TraceTimeline'
import { formatTraceTokenCount, getTraceStatusVariant, sortTraceSpans } from './trace-utils'

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

const STATUS_OPTIONS: Array<{ label: string; value: TraceStatusFilter | 'ALL' }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Success', value: 'SUCCESS' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Running', value: 'RUNNING' },
]

const ENTRYPOINT_OPTIONS: Array<{ label: string; value: TraceEntrypointFilter | 'ALL' }> = [
  { label: 'All entrypoints', value: 'ALL' },
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

  function resetPageForFilter(onChange: (value: string) => void) {
    return (value: string) => {
      setPageNo(1)
      onChange(value)
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
          <h2 className="text-2xl font-semibold tracking-tight text-white">Traces</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">
            Inspect gateway-governed AI requests, span timelines, and token records. Missing trace
            roots are treated as normal empty/error states while Stage 2 hardens MODEL_ERROR paths.
          </p>
        </div>
        <Button onClick={() => loadTraceList(true)} variant="secondary">
          <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          Refresh
        </Button>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
          <AlertTitle>Traces unavailable</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Trace filters</CardTitle>
          <CardDescription>Server-side filters mapped to the Stage 2 Trace API.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-3">
            <FilterSelect
              label="Application"
              onChange={resetPageForFilter(setApplicationFilter)}
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
              label="Status"
              onChange={resetPageForFilter((value) => setStatusFilter(value as TraceStatusFilter | 'ALL'))}
              options={STATUS_OPTIONS}
              value={statusFilter}
            />
            <FilterSelect
              label="Entrypoint"
              onChange={resetPageForFilter((value) => setEntrypointFilter(value as TraceEntrypointFilter | 'ALL'))}
              options={ENTRYPOINT_OPTIONS}
              value={entrypointFilter}
            />
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <Card>
          <CardHeader>
            <div className="flex items-start justify-between gap-4">
              <div>
                <CardTitle>Trace list</CardTitle>
                <CardDescription>
                  {state.traces ? `${state.traces.total} traces matched` : 'Latest traces'}
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
                    <TableHead>Trace</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Mode</TableHead>
                    <TableHead>Latency</TableHead>
                    <TableHead>Started</TableHead>
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
                        <Badge variant={getTraceStatusVariant(trace.status)}>{trace.status}</Badge>
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
                <AlertTitle>No traces</AlertTitle>
                <AlertDescription>
                  Run Chat from the browser or API Key entrypoint. Trace roots will appear here when
                  Gateway writes them successfully.
                </AlertDescription>
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
  if (detailState.status === 'idle') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Trace detail</CardTitle>
          <CardDescription>Select a trace to inspect spans.</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert>
            <AlertTitle>No trace selected</AlertTitle>
            <AlertDescription>Select a trace row from the list.</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  if (detailState.status === 'loading') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Trace detail</CardTitle>
          <CardDescription>{selectedTrace?.traceId ?? 'Loading detail'}</CardDescription>
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
      <Card>
        <CardHeader>
          <CardTitle>Trace detail</CardTitle>
          <CardDescription>{selectedTrace?.traceId ?? 'Unavailable'}</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert variant="warning">
            <AlertTitle>Trace detail unavailable</AlertTitle>
            <AlertDescription>
              {detailState.error || 'This request may not have produced a trace root yet.'}
            </AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  const detail = detailState.detail

  if (!detail) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Trace detail</CardTitle>
          <CardDescription>{selectedTrace?.traceId ?? 'Unavailable'}</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert variant="warning">
            <AlertTitle>Trace detail missing</AlertTitle>
            <AlertDescription>This trace detail is not available yet.</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid gap-6 2xl:grid-cols-[minmax(0,1fr)_360px]">
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div>
              <CardTitle>Trace detail</CardTitle>
              <CardDescription className="break-all font-mono">{detail.traceId}</CardDescription>
            </div>
            <Badge variant={getTraceStatusVariant(detail.status)}>{detail.status}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-3 md:grid-cols-3">
            <Metric label="Latency" value={formatLatency(detail.latencyMs)} />
            <Metric label="Tokens" value={formatTraceTokenCount(detail.totalTokens)} />
            <Metric label="Mode" value={detail.agentMode || '-'} />
          </div>

          {detail.errorMessage ? (
            <Alert variant="danger">
              <AlertTitle>{detail.errorCode || 'Trace failed'}</AlertTitle>
              <AlertDescription>{detail.errorMessage}</AlertDescription>
            </Alert>
          ) : null}

          <div className="rounded-2xl border border-white/10 bg-zinc-950/45 p-4">
            <p className="mb-4 text-sm font-medium text-white">Span timeline</p>
            <TraceTimeline
              onSelectSpan={setSelectedSpan}
              selectedSpanId={selectedSpan?.id}
              spans={detail.spans}
            />
          </div>

          <div className="rounded-2xl border border-white/10 bg-zinc-950/45 p-4">
            <p className="text-sm font-medium text-white">Token usage records</p>
            {detail.tokenUsages.length === 0 ? (
              <p className="mt-3 text-sm text-zinc-500">No token usage linked to this trace.</p>
            ) : (
              <div className="mt-3 space-y-2">
                {detail.tokenUsages.map((usage) => (
                  <div
                    className="grid gap-2 rounded-xl border border-white/10 bg-white/[0.035] p-3 text-xs md:grid-cols-4"
                    key={usage.id}
                  >
                    <span className="font-mono text-zinc-300">{usage.modelName || '-'}</span>
                    <span className="text-zinc-500">{usage.providerType || '-'}</span>
                    <span className="font-mono text-zinc-300">
                      {formatTraceTokenCount(usage.totalTokens)} tokens
                    </span>
                    <span className="text-zinc-500">{usage.estimated ? 'estimated' : 'real'}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      <SpanDetailPanel span={selectedSpan} />
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.035] p-3">
      <p className="text-xs text-zinc-500">{label}</p>
      <p className="mt-2 truncate font-mono text-sm text-white">{value}</p>
    </div>
  )
}
