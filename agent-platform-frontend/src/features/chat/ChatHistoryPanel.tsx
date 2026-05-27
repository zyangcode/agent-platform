import { Clock3, MessageSquarePlus, RefreshCw, Trash2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
    <Card className="h-fit">
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle>{t('chat.history')}</CardTitle>
            <CardDescription>{t('chat.historyDescription')}</CardDescription>
          </div>
          <div className="flex items-center gap-1">
            <Button
              aria-label={t('chat.newConversation')}
              disabled={disabled || isLoading}
              onClick={onNewConversation}
              size="icon"
              variant="ghost"
            >
              <MessageSquarePlus className="h-4 w-4" strokeWidth={1.75} />
            </Button>
            <Button
              aria-label={t('common.refresh')}
              disabled={disabled || isLoading}
              onClick={onRefresh}
              size="icon"
              variant="ghost"
            >
              <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
          </div>
        ) : error ? (
          <Alert variant="danger">
            <AlertTitle>{t('chat.historyUnavailable')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : disabled ? (
          <Alert>
            <AlertTitle>{t('chat.noProfileSelected')}</AlertTitle>
            <AlertDescription>{t('chat.noProfileSelectedDescription')}</AlertDescription>
          </Alert>
        ) : conversations.length === 0 ? (
          <Alert>
            <AlertTitle>{t('chat.noConversations')}</AlertTitle>
            <AlertDescription>{t('chat.noConversationsDescription')}</AlertDescription>
          </Alert>
        ) : (
          <div className="space-y-2">
            {conversations.map((conversation) => {
              const selected = selectedConversationId === conversation.conversationId
              return (
                <div
                  className="group grid grid-cols-[minmax(0,1fr)_auto] gap-2 rounded-lg border border-white/10 bg-zinc-950/40 p-3 transition hover:border-cyan-300/40 hover:bg-cyan-300/5 data-[selected=true]:border-cyan-300/60 data-[selected=true]:bg-cyan-300/10"
                  data-selected={selected}
                  key={conversation.conversationId}
                >
                  <button className="min-w-0 text-left" onClick={() => onSelect(conversation)} type="button">
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate text-sm font-medium text-white">
                        {conversation.title || t('chat.conversationFallbackTitle', { id: conversation.conversationId })}
                      </p>
                      <Badge variant={conversation.status === 'ACTIVE' ? 'success' : 'muted'}>
                        {conversation.status}
                      </Badge>
                    </div>
                    <div className="mt-2 flex items-center gap-2 text-xs text-zinc-500">
                      <Clock3 className="h-3.5 w-3.5" strokeWidth={1.75} />
                      <span>{formatTime(conversation.updatedAt || conversation.createdAt)}</span>
                    </div>
                  </button>
                  <Button
                    className="mt-0.5 h-8 w-8 opacity-80 transition group-hover:opacity-100"
                    aria-label={t('chat.deleteConversation')}
                    onClick={() => onArchive(conversation)}
                    size="icon"
                    variant="ghost"
                  >
                    <Trash2 className="h-3.5 w-3.5 text-zinc-400" strokeWidth={1.75} />
                  </Button>
                </div>
              )
            })}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function formatTime(value?: string | null) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}
