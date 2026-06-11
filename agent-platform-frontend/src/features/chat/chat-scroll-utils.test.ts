import { describe, expect, it } from 'vitest'
import { isNearScrollBottom, shouldAutoScrollMessages } from './chat-scroll-utils'

describe('isNearScrollBottom', () => {
  it('treats a message list near the bottom as eligible for auto-scroll', () => {
    expect(isNearScrollBottom({ clientHeight: 400, scrollHeight: 1000, scrollTop: 540 })).toBe(true)
  })

  it('does not auto-scroll after the user has moved away from the bottom', () => {
    expect(isNearScrollBottom({ clientHeight: 400, scrollHeight: 1000, scrollTop: 420 })).toBe(false)
  })
})

describe('shouldAutoScrollMessages', () => {
  it('keeps the chat pinned to the latest streamed token even after the panel is no longer near the bottom', () => {
    expect(shouldAutoScrollMessages({ isStreaming: true, wasNearBottom: false })).toBe(true)
  })

  it('preserves manual scroll position when not streaming and the user has moved away from the bottom', () => {
    expect(shouldAutoScrollMessages({ isStreaming: false, wasNearBottom: false })).toBe(false)
  })
})
