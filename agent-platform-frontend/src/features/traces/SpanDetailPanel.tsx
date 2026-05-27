import { Check, Code2, Copy } from 'lucide-react'
import { useState } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { TraceSpan } from '@/lib/api/types'
import { copyTextToClipboard } from '@/lib/clipboard'
import { formatDateTime, formatLatency } from '@/lib/format/date'
import { useI18n } from '@/lib/i18n/use-i18n'
import { formatTraceStatus, getTraceStatusVariant } from './trace-utils'

type SpanDetailPanelProps = {
  span: TraceSpan | null
}

export function SpanDetailPanel({ span }: SpanDetailPanelProps) {
  const { locale, t } = useI18n()
  const [copiedValue, setCopiedValue] = useState<string | null>(null)

  async function copyValue(value: string) {
    const copied = await copyTextToClipboard(value)

    if (copied) {
      setCopiedValue(value)
      window.setTimeout(() => setCopiedValue(null), 1200)
    }
  }

  if (!span) {
    return (
      <Card className="min-w-0">
        <CardHeader>
          <CardTitle>{t('trace.spanDetail')}</CardTitle>
          <CardDescription>{t('trace.noSpanSelectedDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert>
            <AlertTitle>{t('trace.noSpanSelected')}</AlertTitle>
            <AlertDescription>{t('trace.noSpanSelectedDescription')}</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="min-w-0">
      <CardHeader>
        <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <CardTitle className="break-words">{span.spanName}</CardTitle>
            <CardDescription className="break-words">
              {span.component} / {span.spanType}
            </CardDescription>
          </div>
          <Badge className="w-fit shrink-0" variant={getTraceStatusVariant(span.status)}>
            {formatTraceStatus(span.status, locale)}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3">
          <Field
            copied={copiedValue === String(span.id)}
            label={t('trace.spanId')}
            onCopy={() => copyValue(String(span.id))}
            value={String(span.id)}
          />
          <Field label={t('trace.parent')} value={span.parentSpanId ? String(span.parentSpanId) : '-'} />
          <Field label={t('trace.started')} value={formatDateTime(span.startedAt)} />
          <Field label={t('trace.ended')} value={formatDateTime(span.endedAt)} />
          <Field label={t('trace.latency')} value={formatLatency(span.latencyMs)} />
          <Field label={t('trace.errorCode')} value={span.errorCode || '-'} />
        </div>

        {span.errorMessage ? (
          <Alert variant="danger">
            <AlertTitle>{t('trace.spanError')}</AlertTitle>
            <AlertDescription className="break-words">{span.errorMessage}</AlertDescription>
          </Alert>
        ) : null}

        <div className="min-w-0 rounded-2xl border border-white/10 bg-zinc-950/60 p-4">
          <div className="flex items-center gap-2 text-xs uppercase tracking-[0.16em] text-zinc-500">
            <Code2 className="h-3.5 w-3.5" strokeWidth={1.75} />
            {t('trace.attributes')}
          </div>
          <pre className="mt-3 max-h-80 max-w-full overflow-auto rounded-xl bg-black/20 p-3 font-mono text-xs leading-5 text-zinc-300">
            {span.attributes ? JSON.stringify(span.attributes, null, 2) : '{}'}
          </pre>
        </div>
      </CardContent>
    </Card>
  )
}

function Field({
  copied = false,
  label,
  onCopy,
  value,
}: {
  copied?: boolean
  label: string
  onCopy?: () => void
  value: string
}) {
  return (
    <div className="grid min-w-0 gap-2 rounded-xl border border-white/10 bg-white/[0.035] px-3 py-2 sm:grid-cols-[5.5rem_minmax(0,1fr)] sm:items-center">
      <span className="text-xs text-zinc-500">{label}</span>
      <span className="flex min-w-0 items-center justify-between gap-2">
        <span className="min-w-0 break-all font-mono text-xs text-zinc-200">{value}</span>
        {onCopy ? (
          <Button className="h-7 w-7 shrink-0" onClick={onCopy} size="icon" variant="ghost">
            {copied ? (
              <Check className="h-3.5 w-3.5 text-emerald-200" strokeWidth={1.75} />
            ) : (
              <Copy className="h-3.5 w-3.5" strokeWidth={1.75} />
            )}
          </Button>
        ) : null}
      </span>
    </div>
  )
}
