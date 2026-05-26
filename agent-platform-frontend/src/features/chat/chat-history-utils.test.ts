import { describe, expect, it } from 'vitest'
import { conversationMessagesToChatMessages } from './chat-history-utils'

describe('conversationMessagesToChatMessages', () => {
  it('maps persisted user and assistant messages into chat messages', () => {
    expect(
      conversationMessagesToChatMessages([
        { content: 'hello', messageId: 1, role: 'user' },
        { content: 'hi', messageId: 2, role: 'assistant' },
        { content: 'tool output', messageId: 3, role: 'tool' },
      ]),
    ).toEqual([
      { content: 'hello', id: 'history_1', role: 'user' },
      { content: 'hi', id: 'history_2', role: 'assistant' },
    ])
  })
})
