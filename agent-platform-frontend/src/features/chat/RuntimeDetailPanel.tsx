import { Activity, AlertTriangle, CheckCircle2, CircleDashed, TimerReset } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { ChatStreamEvent, RuntimeStatus } from './types'

type RuntimeDetailPanelProps = {
  error: string | null
  events: ChatStreamEvent[]
  status: RuntimeStatus
}

function statusLabel(status: RuntimeStatus) {
  if (status === 'streaming') {
    return 'Streaming'
  }
  if (status === 'done') {
    return 'Done'
  }
  if (status === 'error') {
    return 'Error'
  }

  return 'Idle'
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
  return (
    <aside className="space-y-4">
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-3">
            <div>
              <CardTitle>Runtime</CardTitle>
              <CardDescription>Gateway stream metadata for the current run.</CardDescription>
            </div>
            <Badge variant={statusVariant(status)}>{statusLabel(status)}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3">
            <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
              <div className="flex items-center gap-2 text-xs text-zinc-500">
                <Activity className="h-3.5 w-3.5" strokeWidth={1.75} />
                Trace ID
              </div>
              <p className="mt-2 break-all font-mono text-xs text-cyan-100">{getTraceId(events)}</p>
            </div>
            <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
              <div className="flex items-center gap-2 text-xs text-zinc-500">
                <TimerReset className="h-3.5 w-3.5" strokeWidth={1.75} />
                Conversation
              </div>
              <p className="mt-2 font-mono text-xs text-zinc-200">{getConversationId(events)}</p>
            </div>
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
              <AlertTitle>Stream failed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Event timeline</CardTitle>
          <CardDescription>thinking, action, observation, message, done, error.</CardDescription>
        </CardHeader>
        <CardContent>
          {events.length === 0 ? (
            <Alert>
              <CircleDashed className="mb-3 h-5 w-5 text-zinc-400" strokeWidth={1.75} />
              <AlertTitle>No stream yet</AlertTitle>
              <AlertDescription>Send a message to watch runtime events arrive.</AlertDescription>
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
