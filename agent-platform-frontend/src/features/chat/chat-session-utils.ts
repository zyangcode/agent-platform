import type { ChatStreamEvent } from './types'

export function nextConversationId(currentConversationId: number | null, event: ChatStreamEvent) {
  return event.conversationId ?? currentConversationId
}
