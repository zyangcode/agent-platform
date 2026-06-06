import { Clock3, MessageSquarePlus, RefreshCw, Trash2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import type { ConversationSummary } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'

type ChatHistoryPanelProps = {
  conversations: ConversationSummary[]
  disabled: boolean
  error: string | null
  isLoading: boolean
  onArchive: (conversation: ConversationSummary) => void
  onNewConversation: () => void
  onRefresh: () => void
  onSelect: (conversation: ConversationSummary) => void
  selectedConversationId: number | null
}

export function ChatHistoryPanel({
  conversations,
  disabled,
  error,
  isLoading,
  onArchive,
  onNewConversation,
  onRefresh,
  onSelect,
  selectedConversationId,
}: ChatHistoryPanelProps) {
  const { t } = useI18n()

  return (
    <div className="flex flex-col glass-panel p-3 gap-2 min-h-0 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-1">
        <p className="text-xs font-semibold uppercase tracking-wider text-text-muted">
          {t('chat.history')}
        </p>
        <div className="flex items-center gap-0.5">
          <Button
            aria-label={t('chat.newConversation')}
            disabled={disabled || isLoading}
            onClick={onNewConversation}
            size="icon"
            variant="ghost"
            className="h-7 w-7 text-text-muted hover:text-text"
          >
            <MessageSquarePlus className="h-4 w-4" strokeWidth={1.75} />
          </Button>
          <Button
            aria-label={t('common.refresh')}
            disabled={disabled || isLoading}
            onClick={onRefresh}
            size="icon"
            variant="ghost"
            className="h-7 w-7 text-text-muted hover:text-text"
          >
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          </Button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto space-y-2">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </div>
        ) : error ? (
          <Alert variant="danger">
            <AlertTitle>{t('chat.historyUnavailable')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : disabled ? (
          <p className="text-xs text-text-muted px-2 py-4 text-center">{t('chat.noProfileSelected')}</p>
        ) : conversations.length === 0 ? (
          <p className="text-xs text-text-muted px-2 py-4 text-center">{t('chat.noConversations')}</p>
        ) : (
          conversations.map((conversation) => {
            const selected = selectedConversationId === conversation.conversationId
            return (
              <div
                className="group grid grid-cols-[minmax(0,1fr)_auto] gap-1 rounded-lg border border-[rgba(148,163,184,0.1)] bg-[rgba(255,255,255,0.035)] p-2.5 transition hover:border-[rgba(96,165,250,0.22)] data-[selected=true]:border-[rgba(96,165,250,0.28)] data-[selected=true]:bg-[linear-gradient(90deg,rgba(59,130,246,0.18),rgba(255,255,255,0.04))]"
                data-selected={selected}
                key={conversation.conversationId}
              >
                <button
                  className="min-w-0 text-left"
                  onClick={() => onSelect(conversation)}
                  type="button"
                >
                  <p className="truncate text-sm text-[#cbd5e1] group-data-[selected=true]:text-[#eff6ff]">
                    {conversation.title || t('chat.conversationFallbackTitle', { id: conversation.conversationId })}
                  </p>
                  <div className="mt-1.5 flex items-center gap-1.5 text-xs text-text-faint">
                    <Clock3 className="h-3 w-3" strokeWidth={1.75} />
                    <span>{formatTime(conversation.updatedAt || conversation.createdAt)}</span>
                  </div>
                </button>
                <Button
                  className="mt-0.5 h-7 w-7 opacity-60 transition group-hover:opacity-100"
                  aria-label={t('chat.deleteConversation')}
                  onClick={() => onArchive(conversation)}
                  size="icon"
                  variant="ghost"
                >
                  <Trash2 className="h-3.5 w-3.5 text-text-muted" strokeWidth={1.75} />
                </Button>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
