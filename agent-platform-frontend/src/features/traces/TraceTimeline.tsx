import { AlertTriangle, CheckCircle2, CircleDashed } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import type { TraceSpan } from '@/lib/api/types'
import { formatDateTime, formatLatency } from '@/lib/format/date'
import { getTraceStatusVariant, sortTraceSpans } from './trace-utils'

type TraceTimelineProps = {
  onSelectSpan: (span: TraceSpan) => void
  selectedSpanId?: number | null
  spans: TraceSpan[]
}

export function TraceTimeline({ onSelectSpan, selectedSpanId, spans }: TraceTimelineProps) {
  const sortedSpans = sortTraceSpans(spans)

  if (sortedSpans.length === 0) {
    return (
      <Alert>
        <AlertTitle>No spans</AlertTitle>
        <AlertDescription>This trace root has no span details yet.</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-3">
      {sortedSpans.map((span, index) => {
        const isSelected = selectedSpanId === span.id
        const isFailed = getTraceStatusVariant(span.status) === 'danger'

        return (
          <button
            className="grid w-full grid-cols-[1.5rem_minmax(0,1fr)] gap-3 text-left"
            key={span.id}
            onClick={() => onSelectSpan(span)}
            type="button"
          >
            <div className="flex flex-col items-center">
              <div className="flex h-6 w-6 items-center justify-center rounded-full border border-white/10 bg-zinc-950">
                {isFailed ? (
                  <AlertTriangle className="h-3.5 w-3.5 text-rose-200" strokeWidth={1.75} />
                ) : span.status.toUpperCase() === 'SUCCESS' ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-200" strokeWidth={1.75} />
                ) : (
                  <CircleDashed className="h-3.5 w-3.5 text-cyan-100" strokeWidth={1.75} />
                )}
              </div>
              {index < sortedSpans.length - 1 ? <div className="mt-2 h-full min-h-10 w-px bg-white/10" /> : null}
            </div>

            <div
              className={
                isSelected
                  ? 'rounded-2xl border border-cyan-200/30 bg-cyan-300/10 p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]'
                  : 'rounded-2xl border border-white/10 bg-white/[0.04] p-4 transition hover:bg-white/[0.065]'
              }
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-medium text-white">{span.spanName}</p>
                  <p className="mt-1 text-xs text-zinc-500">
                    {span.component} / {span.spanType}
                  </p>
                </div>
                <Badge variant={getTraceStatusVariant(span.status)}>{span.status}</Badge>
              </div>
              <div className="mt-3 flex flex-wrap gap-3 text-xs text-zinc-500">
                <span>{formatDateTime(span.startedAt)}</span>
                <span className="font-mono">{formatLatency(span.latencyMs)}</span>
              </div>
            </div>
          </button>
        )
      })}
    </div>
  )
}
