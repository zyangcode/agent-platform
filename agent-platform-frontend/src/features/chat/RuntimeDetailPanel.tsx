import { Activity, AlertTriangle, CheckCircle2, CircleDashed, ShieldCheck, TimerReset } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useI18n } from '@/lib/i18n/use-i18n'
import { TeamRunPanel } from './TeamRunPanel'
import { buildTeamRunSummary } from './team-run-utils'
import type { JsonObject, JsonValue } from '@/lib/api/types'
import type { ChatStreamEvent, PendingToolConfirmation, RuntimeStatus } from './types'

type RuntimeDetailPanelProps = {
  error: string | null
  events: ChatStreamEvent[]
  onConfirmTool?: (confirmation: PendingToolConfirmation) => void
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

function toolKey(event: ChatStreamEvent) {
  const metadataToolKey =
    event.metadata && typeof event.metadata === 'object' && !Array.isArray(event.metadata)
      ? event.metadata.toolKey
      : null
  if (typeof metadataToolKey === 'string' && metadataToolKey.length > 0) {
    return metadataToolKey
  }
  const metadataToolType =
    event.metadata && typeof event.metadata === 'object' && !Array.isArray(event.metadata)
      ? event.metadata.toolType
      : null
  const metadataToolName =
    event.metadata && typeof event.metadata === 'object' && !Array.isArray(event.metadata)
      ? event.metadata.toolName
      : null
  const toolType = typeof metadataToolType === 'string' ? metadataToolType : null
  const toolName = typeof metadataToolName === 'string' ? metadataToolName : event.toolName
  if (toolType && toolName) {
    return `${toolType}:${toolName}`
  }
  return null
}

function isJsonObject(value: unknown): value is JsonObject {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function isJsonValue(value: unknown): value is JsonValue {
  if (value === null) {
    return true
  }
  if (['boolean', 'number', 'string'].includes(typeof value)) {
    return true
  }
  if (Array.isArray(value)) {
    return value.every(isJsonValue)
  }
  if (isJsonObject(value)) {
    return Object.values(value).every(isJsonValue)
  }
  return false
}

function pendingToolConfirmation(event: ChatStreamEvent): PendingToolConfirmation | null {
  const key = toolKey(event)
  if (!key || !isJsonObject(event.metadata)) {
    return null
  }
  const pendingToolCall = event.metadata.pendingToolCall
  if (!isJsonObject(pendingToolCall)) {
    return null
  }
  if (pendingToolCall.sourceType !== 'SKILL' && pendingToolCall.sourceType !== 'MCP') {
    return null
  }
  if (typeof pendingToolCall.toolName !== 'string' || pendingToolCall.toolName.length === 0) {
    return null
  }
  const args = isJsonValue(pendingToolCall.arguments) ? pendingToolCall.arguments : undefined
  return {
    toolKey: key,
    pendingToolCall: {
      arguments: args,
      sourceType: pendingToolCall.sourceType,
      toolName: pendingToolCall.toolName,
    },
  }
}

export function RuntimeDetailPanel({ error, events, onConfirmTool, status }: RuntimeDetailPanelProps) {
  const { t } = useI18n()
  const teamSummary = buildTeamRunSummary(events)

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

      <TeamRunPanel summary={teamSummary} />

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
                  {event.type === 'tool_confirm_required' && pendingToolConfirmation(event) && status !== 'streaming' ? (
                    <Button
                      className="mt-3"
                      onClick={() => onConfirmTool?.(pendingToolConfirmation(event)!)}
                      size="sm"
                      type="button"
                      variant="secondary"
                    >
                      <ShieldCheck className="h-4 w-4" strokeWidth={1.75} />
                      {t('runtime.confirmTool')}
                    </Button>
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
