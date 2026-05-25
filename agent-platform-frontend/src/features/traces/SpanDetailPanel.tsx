import { Code2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { TraceSpan } from '@/lib/api/types'
import { formatDateTime, formatLatency } from '@/lib/format/date'
import { getTraceStatusVariant } from './trace-utils'

type SpanDetailPanelProps = {
  span: TraceSpan | null
}

export function SpanDetailPanel({ span }: SpanDetailPanelProps) {
  if (!span) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Span detail</CardTitle>
          <CardDescription>Select a timeline node to inspect span attributes.</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert>
            <AlertTitle>No span selected</AlertTitle>
            <AlertDescription>Click a span in the timeline.</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>{span.spanName}</CardTitle>
            <CardDescription>
              {span.component} / {span.spanType}
            </CardDescription>
          </div>
          <Badge variant={getTraceStatusVariant(span.status)}>{span.status}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3">
          <Field label="Span ID" value={String(span.id)} />
          <Field label="Parent" value={span.parentSpanId ? String(span.parentSpanId) : '-'} />
          <Field label="Started" value={formatDateTime(span.startedAt)} />
          <Field label="Ended" value={formatDateTime(span.endedAt)} />
          <Field label="Latency" value={formatLatency(span.latencyMs)} />
          <Field label="Error code" value={span.errorCode || '-'} />
        </div>

        {span.errorMessage ? (
          <Alert variant="danger">
            <AlertTitle>Span error</AlertTitle>
            <AlertDescription>{span.errorMessage}</AlertDescription>
          </Alert>
        ) : null}

        <div className="rounded-2xl border border-white/10 bg-zinc-950/60 p-4">
          <div className="flex items-center gap-2 text-xs uppercase tracking-[0.16em] text-zinc-500">
            <Code2 className="h-3.5 w-3.5" strokeWidth={1.75} />
            Attributes
          </div>
          <pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-words font-mono text-xs leading-5 text-zinc-300">
            {span.attributes ? JSON.stringify(span.attributes, null, 2) : '{}'}
          </pre>
        </div>
      </CardContent>
    </Card>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-white/[0.035] px-3 py-2">
      <span className="text-xs text-zinc-500">{label}</span>
      <span className="min-w-0 truncate font-mono text-xs text-zinc-200">{value}</span>
    </div>
  )
}
