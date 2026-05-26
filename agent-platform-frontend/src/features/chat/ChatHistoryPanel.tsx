import { Clock3, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import type { ConversationSummary } from '@/lib/api/types'

type ChatHistoryPanelProps = {
  conversations: ConversationSummary[]
  disabled: boolean
  error: string | null
  isLoading: boolean
  onRefresh: () => void
  onSelect: (conversation: ConversationSummary) => void
  selectedConversationId: number | null
}

export function ChatHistoryPanel({
  conversations,
  disabled,
  error,
  isLoading,
  onRefresh,
  onSelect,
  selectedConversationId,
}: ChatHistoryPanelProps) {
  return (
    <Card className="h-fit">
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle>History</CardTitle>
            <CardDescription>Recent conversations under the selected profile.</CardDescription>
          </div>
          <Button disabled={disabled || isLoading} onClick={onRefresh} size="icon" variant="ghost">
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          </Button>
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
            <AlertTitle>History unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : disabled ? (
          <Alert>
            <AlertTitle>No profile selected</AlertTitle>
            <AlertDescription>Select an application and profile to load chat history.</AlertDescription>
          </Alert>
        ) : conversations.length === 0 ? (
          <Alert>
            <AlertTitle>No conversations</AlertTitle>
            <AlertDescription>Send a message to create the first conversation.</AlertDescription>
          </Alert>
        ) : (
          <div className="space-y-2">
            {conversations.map((conversation) => {
              const selected = selectedConversationId === conversation.conversationId
              return (
                <button
                  className="w-full rounded-lg border border-white/10 bg-zinc-950/40 p-3 text-left transition hover:border-cyan-300/40 hover:bg-cyan-300/5 data-[selected=true]:border-cyan-300/60 data-[selected=true]:bg-cyan-300/10"
                  data-selected={selected}
                  key={conversation.conversationId}
                  onClick={() => onSelect(conversation)}
                  type="button"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate text-sm font-medium text-white">
                      {conversation.title || `Conversation ${conversation.conversationId}`}
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
    return 'No timestamp'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}
