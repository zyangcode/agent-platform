import { Bot, ExternalLink, Send, Square, UserRound } from 'lucide-react'
import { type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { useI18n } from '@/lib/i18n/use-i18n'
import { cn } from '@/lib/utils'
import type { ChatMessage, RagCitation, RuntimeStatus } from './types'

type ConversationPanelProps = {
  disabledReason: string | null
  input: string
  messages: ChatMessage[]
  ragCitations?: RagCitation[]
  onInputChange: (value: string) => void
  onStop: () => void
  onSubmit: () => void
  status: RuntimeStatus
}

export function ConversationPanel({
  disabledReason,
  input,
  messages,
  ragCitations,
  onInputChange,
  onStop,
  onSubmit,
  status,
}: ConversationPanelProps) {
  const { t } = useI18n()
  const isStreaming = status === 'streaming'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit()
  }

  return (
    <div className="flex min-h-[760px] flex-col rounded-2xl border border-white/10 bg-zinc-950/45 shadow-[inset_0_1px_0_rgba(255,255,255,0.06)]">
      <div className="border-b border-white/10 px-5 py-4">
        <p className="text-sm font-medium text-white">{t('chat.conversation')}</p>
        <p className="mt-1 text-xs text-zinc-500">{t('chat.conversationHelp')}</p>
      </div>

      <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
        {messages.length === 0 ? (
          <div className="flex h-full min-h-[320px] items-center justify-center">
            <div className="max-w-md text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
                <Bot className="h-6 w-6 text-cyan-100" strokeWidth={1.75} />
              </div>
              <h3 className="mt-5 text-base font-semibold text-white">{t('chat.firstRunTitle')}</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-500">
                {t('chat.firstRunDescription')}
              </p>
            </div>
          </div>
        ) : (
          <>
            {messages.map((message) => (
              <div
                className={cn(
                  'flex gap-3',
                  message.role === 'user' ? 'justify-end' : 'justify-start',
                )}
                key={message.id}
              >
                {message.role === 'assistant' ? (
                  <div className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-cyan-200/20 bg-cyan-300/10">
                    <Bot className="h-4 w-4 text-cyan-100" strokeWidth={1.75} />
                  </div>
                ) : null}
                <div
                  className={cn(
                    'max-w-[88%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-6',
                    message.role === 'user'
                      ? 'bg-cyan-200 text-zinc-950'
                      : 'border border-white/10 bg-white/[0.055] text-zinc-100',
                  )}
                >
                  {message.content}
                </div>
                {message.role === 'user' ? (
                  <div className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/[0.06]">
                    <UserRound className="h-4 w-4 text-zinc-300" strokeWidth={1.75} />
                  </div>
                ) : null}
              </div>
            ))}
            {ragCitations && ragCitations.length > 0 ? (
              <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
                <p className="text-xs font-medium text-zinc-400">{t('chat.references')}</p>
                <div className="mt-2 space-y-2">
                  {ragCitations.map((citation, index) => (
                    <a
                      className="flex items-center gap-2 text-xs text-cyan-300 hover:text-cyan-200"
                      href={citation.sourceUri || undefined}
                      key={`${citation.documentId}-${index}`}
                      rel="noreferrer"
                      target="_blank"
                    >
                      <ExternalLink className="h-3 w-3" strokeWidth={1.75} />
                      {citation.title || `Reference ${index + 1}`}
                    </a>
                  ))}
                </div>
              </div>
            ) : null}
          </>
        )}
      </div>

      <form className="border-t border-white/10 p-4" onSubmit={handleSubmit}>
        {disabledReason ? <p className="mb-3 text-xs text-amber-200">{disabledReason}</p> : null}
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
          <Textarea
            className="min-h-32 resize-none"
            disabled={isStreaming}
            onChange={(event) => onInputChange(event.target.value)}
            onKeyDown={(event) => {
              if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                onSubmit()
              }
            }}
            placeholder={t('chat.placeholder')}
            value={input}
          />
          {isStreaming ? (
            <Button className="lg:mb-1" onClick={onStop} type="button" variant="secondary">
              <Square className="h-4 w-4" strokeWidth={1.75} />
              {t('chat.stop')}
            </Button>
          ) : (
            <Button className="lg:mb-1" disabled={!!disabledReason || input.trim().length === 0} type="submit">
              <Send className="h-4 w-4" strokeWidth={1.75} />
              {t('chat.send')}
            </Button>
          )}
        </div>
      </form>
    </div>
  )
}
