import { Activity, AlertTriangle, CheckCircle2, CircleDashed, ShieldCheck, TimerReset } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
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
  if (status === 'streaming') return t('runtime.statusStreaming')
  if (status === 'done') return t('runtime.statusDone')
  if (status === 'error') return t('runtime.statusError')
  return t('runtime.statusIdle')
}

function statusBadgeClass(status: RuntimeStatus) {
  if (status === 'done') return 'tag-green'
  if (status === 'error') return 'tag-red'
  if (status === 'streaming') return 'tag-amber'
  return 'tag-blue'
}

function getTraceId(events: ChatStreamEvent[]) {
  return events.find((event) => event.traceId)?.traceId ?? '-'
}

function getConversationId(events: ChatStreamEvent[]) {
  return events.find((event) => event.conversationId)?.conversationId ?? '-'
}

function isJsonObject(value: unknown): value is JsonObject {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function isJsonValue(value: unknown): value is JsonValue {
  if (value === null) return true
  if (['boolean', 'number', 'string'].includes(typeof value)) return true
  if (Array.isArray(value)) return value.every(isJsonValue)
  if (isJsonObject(value)) return Object.values(value).every(isJsonValue)
  return false
}

function pendingToolConfirmation(event: ChatStreamEvent): PendingToolConfirmation | null {
  if (!isJsonObject(event.metadata)) return null
  const key = `${event.metadata?.toolType ?? ''}:${event.metadata?.toolName ?? event.toolName ?? ''}`
  if (!key || key === ':') return null
  const pendingToolCall = event.metadata.pendingToolCall
  if (!isJsonObject(pendingToolCall)) return null
  if (pendingToolCall.sourceType !== 'SKILL' && pendingToolCall.sourceType !== 'MCP') return null
  if (typeof pendingToolCall.toolName !== 'string' || pendingToolCall.toolName.length === 0) return null
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
    <aside className="flex flex-col gap-3 min-h-0 overflow-y-auto">
      {/* Status header */}
      <div className="glass-panel p-3">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-text">{t('runtime.title')}</p>
          <span className={statusBadgeClass(status)}>{statusLabel(status, t)}</span>
        </div>
        <div className="mt-3 space-y-2">
          <div className="flex items-center gap-2 text-xs text-text-faint">
            <Activity className="h-3.5 w-3.5" strokeWidth={1.75} />
            {t('runtime.traceId')}
          </div>
          <p className="font-mono text-xs text-accent-cyan break-all">{getTraceId(events)}</p>
        </div>
        <div className="mt-2 space-y-2">
          <div className="flex items-center gap-2 text-xs text-text-faint">
            <TimerReset className="h-3.5 w-3.5" strokeWidth={1.75} />
            {t('runtime.conversation')}
          </div>
          <p className="font-mono text-xs text-text-muted">{getConversationId(events)}</p>
        </div>
      </div>

      {error ? (
        <Alert variant="danger">
          <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
          <AlertTitle>{t('runtime.streamFailed')}</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      <TeamRunPanel summary={teamSummary} />

      {/* Event timeline */}
      <div className="glass-panel p-3 flex-1 min-h-0 overflow-hidden flex flex-col">
        <p className="text-sm font-semibold text-text mb-2">{t('runtime.eventTimeline')}</p>
        {events.length === 0 ? (
          <div className="flex-1 flex items-center justify-center">
            <p className="text-xs text-text-faint text-center">{t('runtime.noStream')}</p>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto space-y-2">
            {events.map((event, index) => (
              <div
                className="rounded-lg border border-[rgba(148,163,184,0.1)] bg-surface-soft p-2.5"
                key={`${event.type}-${index}`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    {event.type === 'done' ? (
                      <CheckCircle2 className="h-3.5 w-3.5 text-success shrink-0" strokeWidth={1.75} />
                    ) : (
                      <CircleDashed className="h-3.5 w-3.5 text-accent-cyan shrink-0" strokeWidth={1.75} />
                    )}
                    <span className="text-xs font-medium text-text truncate">{event.type}</span>
                  </div>
                  <span className="font-mono text-[10px] text-text-faint shrink-0">#{event.step ?? index + 1}</span>
                </div>
                {event.content ? (
                  <p className="mt-1.5 line-clamp-3 text-[11px] leading-5 text-text-muted">{event.content}</p>
                ) : null}
                {event.type === 'tool_confirm_required' && status !== 'streaming' ? (
                  <Button
                    className="mt-2 h-7 text-xs"
                    onClick={() => {
                      const c = pendingToolConfirmation(event)
                      if (c && onConfirmTool) onConfirmTool(c)
                    }}
                    size="sm"
                    variant="secondary"
                  >
                    <ShieldCheck className="h-3.5 w-3.5" strokeWidth={1.75} />
                    {t('runtime.confirmTool')}
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>
    </aside>
  )
}
