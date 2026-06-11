import { Bot, ExternalLink, Send, Square, UserRound } from 'lucide-react'
import { type FormEvent, useLayoutEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { useI18n } from '@/lib/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { isNearScrollBottom, shouldAutoScrollMessages } from './chat-scroll-utils'
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
  const bottomRef = useRef<HTMLDivElement | null>(null)
  const messagesRef = useRef<HTMLDivElement | null>(null)
  const shouldAutoScrollRef = useRef(true)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit()
  }

  function handleMessagesScroll() {
    const element = messagesRef.current
    if (!element) {
      return
    }
    shouldAutoScrollRef.current = isNearScrollBottom(element)
  }

  useLayoutEffect(() => {
    const element = messagesRef.current
    if (
      !element ||
      !shouldAutoScrollMessages({
        isStreaming,
        wasNearBottom: shouldAutoScrollRef.current,
      })
    ) {
      return
    }

    const frameId = window.requestAnimationFrame(() => {
      bottomRef.current?.scrollIntoView({ block: 'end' })
      element.scrollTop = element.scrollHeight
    })

    return () => window.cancelAnimationFrame(frameId)
  }, [isStreaming, messages, ragCitations])

  return (
    <div className="flex h-full min-h-0 flex-col glass-panel overflow-hidden">
      {/* Messages area */}
      <div
        className="flex-1 space-y-4 overflow-y-auto px-5 py-4"
        onScroll={handleMessagesScroll}
        ref={messagesRef}
      >
        {messages.length === 0 ? (
          <div className="flex h-full min-h-[400px] items-center justify-center">
            <div className="max-w-md text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl border border-[rgba(56,189,248,0.25)] bg-[rgba(56,189,248,0.08)]">
                <Bot className="h-7 w-7 text-accent-cyan" strokeWidth={1.5} />
              </div>
              <h3 className="mt-5 text-base font-semibold text-text">{t('chat.firstRunTitle')}</h3>
              <p className="mt-2 text-sm leading-6 text-text-muted">
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
                  <div className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-[rgba(56,189,248,0.2)] bg-[rgba(56,189,248,0.08)]">
                    <Bot className="h-3.5 w-3.5 text-accent-cyan" strokeWidth={1.75} />
                  </div>
                ) : null}
                <div
                  className={cn(
                    'max-w-[85%] whitespace-pre-wrap rounded-lg px-4 py-3 text-sm leading-6',
                    message.role === 'user'
                      ? 'bg-[linear-gradient(135deg,rgba(59,130,246,0.32),rgba(56,189,248,0.1))] border border-[rgba(96,165,250,0.28)] text-[#eaf4ff]'
                      : 'border border-[rgba(148,163,184,0.12)] bg-[rgba(255,255,255,0.05)] text-text',
                  )}
                >
                  {message.content}
                </div>
                {message.role === 'user' ? (
                  <div className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-[rgba(148,163,184,0.14)] bg-surface-soft">
                    <UserRound className="h-3.5 w-3.5 text-text-muted" strokeWidth={1.75} />
                  </div>
                ) : null}
              </div>
            ))}
            {ragCitations && ragCitations.length > 0 ? (
              <div className="rounded-lg border border-[rgba(148,163,184,0.12)] bg-surface-soft p-4">
                <p className="text-xs font-medium text-text-muted">{t('chat.references')}</p>
                <div className="mt-2 space-y-2">
                  {ragCitations.map((citation, index) => (
                    <a
                      className="flex items-center gap-2 text-xs text-accent-cyan hover:text-[#7dd3fc]"
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
            <div ref={bottomRef} />
          </>
        )}
      </div>

      {/* Input area */}
      <form
        className="border-t border-[rgba(148,163,184,0.12)] p-4"
        onSubmit={handleSubmit}
      >
        {disabledReason ? (
          <p className="mb-3 text-xs text-warning">{disabledReason}</p>
        ) : null}
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
          <Textarea
            className="min-h-24 resize-none glass-input text-sm placeholder:text-text-faint"
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
            <Button
              className="lg:mb-1 btn-accent"
              disabled={!!disabledReason || input.trim().length === 0}
              type="submit"
            >
              <Send className="h-4 w-4" strokeWidth={1.75} />
              {t('chat.send')}
            </Button>
          )}
        </div>
      </form>
    </div>
  )
}
