import { describe, expect, it } from 'vitest'
import {
  isAssistantContentEvent,
  nextAssistantContent,
  shouldShowRuntimeTimelineEvent,
} from './chat-stream-event-utils'

describe('nextAssistantContent', () => {
  it('appends message_delta content to the assistant draft', () => {
    expect(nextAssistantContent('hel', { type: 'message_delta', content: 'lo' })).toBe('hello')
  })

  it('ignores leading string null deltas from provider keepalive chunks', () => {
    expect(nextAssistantContent('', { type: 'message_delta', content: 'null' })).toBe('')
    expect(nextAssistantContent('answer: ', { type: 'message_delta', content: 'null' })).toBe('answer: null')
  })

  it('uses final message content as the authoritative assistant answer', () => {
    expect(nextAssistantContent('hello', { type: 'message', content: 'hello' })).toBe('hello')
  })

  it('keeps the streamed assistant draft when the final message is shorter', () => {
    expect(nextAssistantContent('hello, this is a complete streamed answer', { type: 'message', content: 'hello' })).toBe(
      'hello, this is a complete streamed answer',
    )
  })

  it('uses the final message when it is at least as complete as the streamed draft', () => {
    expect(nextAssistantContent('hello', { type: 'message', content: 'hello, finalized answer' })).toBe(
      'hello, finalized answer',
    )
  })

  it('uses team_final content as the assistant answer', () => {
    expect(nextAssistantContent('', { type: 'team_final', content: 'team summarized answer' })).toBe(
      'team summarized answer',
    )
  })

  it('ignores events without assistant content', () => {
    expect(nextAssistantContent('hello', { type: 'thinking', content: 'request accepted' })).toBe('hello')
    expect(
      nextAssistantContent('hello', {
        type: 'tool_confirm_required',
        content: '[tool confirm required] skill:deploy risk=HIGH',
      }),
    ).toBe('hello')
    expect(nextAssistantContent('hello', { type: 'done' })).toBe('hello')
  })
})

describe('isAssistantContentEvent', () => {
  it('allows only user-facing assistant answer events into the chat bubble', () => {
    expect(isAssistantContentEvent({ type: 'message', content: 'final' })).toBe(true)
    expect(isAssistantContentEvent({ type: 'message_delta', content: 'token' })).toBe(true)
    expect(isAssistantContentEvent({ type: 'team_final', content: 'team final' })).toBe(true)
    expect(isAssistantContentEvent({ type: 'error', content: 'failed' })).toBe(false)
    expect(isAssistantContentEvent({ type: 'done' })).toBe(false)
    expect(isAssistantContentEvent({ type: 'thinking', content: 'request accepted' })).toBe(false)
    expect(isAssistantContentEvent({ type: 'team_plan', content: 'plan' })).toBe(false)
    expect(isAssistantContentEvent({ type: 'tool_confirm_required', content: 'confirm' })).toBe(false)
  })
})

describe('shouldShowRuntimeTimelineEvent', () => {
  it('hides token-level message_delta events from the runtime timeline', () => {
    expect(shouldShowRuntimeTimelineEvent({ type: 'message_delta', content: 'hel', step: 2 })).toBe(false)
  })

  it('keeps lifecycle and tool events in the runtime timeline', () => {
    expect(shouldShowRuntimeTimelineEvent({ type: 'thinking', content: 'request accepted', step: 1 })).toBe(true)
    expect(shouldShowRuntimeTimelineEvent({ type: 'message', content: 'hello', step: 3 })).toBe(true)
    expect(shouldShowRuntimeTimelineEvent({ type: 'done', step: 4 })).toBe(true)
    expect(
      shouldShowRuntimeTimelineEvent({
        type: 'tool_confirm_required',
        content: '[tool confirm required] skill:deploy risk=HIGH',
        step: 2,
      }),
    ).toBe(true)
  })
})
