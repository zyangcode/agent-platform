import { Activity, AlertTriangle, CheckCircle2, CircleDashed, TimerReset } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useI18n } from '@/lib/i18n/use-i18n'
import type { ChatStreamEvent, RuntimeStatus } from './types'

type RuntimeDetailPanelProps = {
  error: string | null
  events: ChatStreamEvent[]
  status: RuntimeStatus
}

function statusLabel(status: RuntimeStatus, t: (key: string) => string) {
  if (status === 'streaming') {
    return t('runtime.statusStreaming')
  }
  if (status === 'done') {
    return t('runtime.statusDone')
  }
  if (status === 'error') {
    return t('runtime.statusError')
  }

  return t('runtime.statusIdle')
}

function statusVariant(status: RuntimeStatus) {
  if (status === 'done') {
    return 'success'
  }
  if (status === 'error') {
    return 'danger'
  }
  if (status === 'streaming') {
    return 'warning'
  }

  return 'muted'
}

function getTraceId(events: ChatStreamEvent[]) {
  return events.find((event) => event.traceId)?.traceId ?? '-'
}

function getConversationId(events: ChatStreamEvent[]) {
  return events.find((event) => event.conversationId)?.conversationId ?? '-'
}

export function RuntimeDetailPanel({ error, events, status }: RuntimeDetailPanelProps) {
  const { t } = useI18n()

  return (
    <aside className="space-y-4">
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-3">
            <div>
              <CardTitle>{t('runtime.title')}</CardTitle>
              <CardDescription>{t('runtime.description')}</CardDescription>
            </div>
            <Badge variant={statusVariant(status)}>{statusLabel(status, t)}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3">
            <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
              <div className="flex items-center gap-2 text-xs text-zinc-500">
                <Activity className="h-3.5 w-3.5" strokeWidth={1.75} />
                {t('runtime.traceId')}
              </div>
              <p className="mt-2 break-all font-mono text-xs text-cyan-100">{getTraceId(events)}</p>
            </div>
            <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
              <div className="flex items-center gap-2 text-xs text-zinc-500">
                <TimerReset className="h-3.5 w-3.5" strokeWidth={1.75} />
                {t('runtime.conversation')}
              </div>
              <p className="mt-2 font-mono text-xs text-zinc-200">{getConversationId(events)}</p>
            </div>
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
              <AlertTitle>{t('runtime.streamFailed')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('runtime.eventTimeline')}</CardTitle>
          <CardDescription>{t('runtime.eventTimelineDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {events.length === 0 ? (
            <Alert>
              <CircleDashed className="mb-3 h-5 w-5 text-zinc-400" strokeWidth={1.75} />
              <AlertTitle>{t('runtime.noStream')}</AlertTitle>
              <AlertDescription>{t('runtime.noStreamDescription')}</AlertDescription>
            </Alert>
          ) : (
            <div className="space-y-3">
              {events.map((event, index) => (
                <div className="rounded-xl border border-white/10 bg-white/[0.04] p-3" key={`${event.type}-${index}`}>
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      {event.type === 'done' ? (
                        <CheckCircle2 className="h-4 w-4 text-emerald-200" strokeWidth={1.75} />
                      ) : (
                        <CircleDashed className="h-4 w-4 text-cyan-100" strokeWidth={1.75} />
                      )}
                      <span className="text-sm font-medium text-white">{event.type}</span>
                    </div>
                    <span className="font-mono text-xs text-zinc-500">#{event.step ?? index + 1}</span>
                  </div>
                  {event.content ? (
                    <p className="mt-2 line-clamp-4 text-xs leading-5 text-zinc-400">{event.content}</p>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </aside>
  )
}
