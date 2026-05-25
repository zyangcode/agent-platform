import type { JsonValue } from '@/lib/api/types'

export type AgentMode = 'agent' | 'none'

export type ChatRole = 'assistant' | 'user'

export type ChatMessage = {
  id: string
  content: string
  role: ChatRole
}

export type ChatStreamEvent = {
  type: 'action' | 'done' | 'error' | 'message' | 'observation' | 'thinking'
  traceId?: string | null
  conversationId?: number | null
  step?: number | null
  content?: string | null
  metadata?: JsonValue
}

export type RuntimeStatus = 'idle' | 'streaming' | 'done' | 'error'
