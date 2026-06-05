import type { AgentMode, ChatMessage } from './types'

const STORAGE_KEY = 'agent-platform.chat.current-session'

export type StoredChatSession = {
  agentMode: AgentMode
  applicationId: number | null
  conversationId: number | null
  messages: ChatMessage[]
  modelConfigId: number | null
  profileId: number | null
}

export function loadStoredChatSession(storage: Pick<Storage, 'getItem'> = sessionStorage) {
  try {
    const raw = storage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }

    const parsed = JSON.parse(raw) as Partial<StoredChatSession>
    if (!isStoredSession(parsed)) {
      return null
    }

    return parsed
  } catch {
    return null
  }
}

export function saveStoredChatSession(
  session: StoredChatSession,
  storage: Pick<Storage, 'setItem'> = sessionStorage,
) {
  storage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredChatSession(storage: Pick<Storage, 'removeItem'> = sessionStorage) {
  storage.removeItem(STORAGE_KEY)
}

function isStoredSession(value: Partial<StoredChatSession>): value is StoredChatSession {
  return (
    (value.agentMode === 'agent' || value.agentMode === 'team' || value.agentMode === 'none') &&
    isNullableNumber(value.applicationId) &&
    isNullableNumber(value.conversationId) &&
    Array.isArray(value.messages) &&
    value.messages.every(isChatMessage) &&
    isNullableNumber(value.modelConfigId) &&
    isNullableNumber(value.profileId)
  )
}

function isNullableNumber(value: unknown) {
  return value === null || typeof value === 'number'
}

function isChatMessage(value: unknown): value is ChatMessage {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<ChatMessage>
  return (
    typeof candidate.id === 'string' &&
    typeof candidate.content === 'string' &&
    (candidate.role === 'assistant' || candidate.role === 'user')
  )
}
