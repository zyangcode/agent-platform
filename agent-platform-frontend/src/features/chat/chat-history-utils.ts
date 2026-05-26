import type { ConversationMessage } from '@/lib/api/types'
import type { ChatMessage, ChatRole } from './types'

export function conversationMessagesToChatMessages(messages: ConversationMessage[]): ChatMessage[] {
  return messages
    .filter((message): message is ConversationMessage & { role: ChatRole } => {
      return message.role === 'user' || message.role === 'assistant'
    })
    .map((message, index) => ({
      content: message.content,
      id: message.messageId ? `history_${message.messageId}` : `history_${index}`,
      role: message.role,
    }))
}
