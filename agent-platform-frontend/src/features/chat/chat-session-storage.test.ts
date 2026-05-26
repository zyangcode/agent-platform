import { describe, expect, it, vi } from 'vitest'
import { clearStoredChatSession, loadStoredChatSession, saveStoredChatSession } from './chat-session-storage'

describe('chat session storage', () => {
  it('saves and loads a current chat session', () => {
    const storage = createStorage()

    saveStoredChatSession(
      {
        agentMode: 'agent',
        applicationId: 3,
        conversationId: 27,
        messages: [{ content: 'hello', id: 'm1', role: 'user' }],
        modelConfigId: 1,
        profileId: 5,
      },
      storage,
    )

    expect(loadStoredChatSession(storage)).toEqual({
      agentMode: 'agent',
      applicationId: 3,
      conversationId: 27,
      messages: [{ content: 'hello', id: 'm1', role: 'user' }],
      modelConfigId: 1,
      profileId: 5,
    })
  })

  it('ignores malformed stored sessions', () => {
    const storage = createStorage()
    storage.setItem('agent-platform.chat.current-session', '{"agentMode":"broken"}')

    expect(loadStoredChatSession(storage)).toBeNull()
  })

  it('clears stored session', () => {
    const storage = createStorage()
    storage.setItem('agent-platform.chat.current-session', '{"agentMode":"broken"}')

    clearStoredChatSession(storage)

    expect(storage.removeItem).toHaveBeenCalledWith('agent-platform.chat.current-session')
    expect(loadStoredChatSession(storage)).toBeNull()
  })
})

function createStorage() {
  const values = new Map<string, string>()
  return {
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    removeItem: vi.fn((key: string) => values.delete(key)),
    setItem: vi.fn((key: string, value: string) => values.set(key, value)),
  }
}
