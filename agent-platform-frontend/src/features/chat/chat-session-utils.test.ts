import { describe, expect, it } from 'vitest'
import { nextConversationId } from './chat-session-utils'

describe('nextConversationId', () => {
  it('uses conversation id from stream event when present', () => {
    expect(nextConversationId(null, { type: 'message', conversationId: 27 })).toBe(27)
  })

  it('keeps current conversation id when event has none', () => {
    expect(nextConversationId(27, { type: 'thinking', conversationId: null })).toBe(27)
  })
})
