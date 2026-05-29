import type { JsonValue } from '@/lib/api/types'

export type AgentMode = 'agent' | 'none'

export type ChatRole = 'assistant' | 'user'

export type ChatMessage = {
  id: string
  content: string
  role: ChatRole
}

export type TeamStreamEventType =
  | 'team_final'
  | 'team_plan'
  | 'team_retry'
  | 'team_review'
  | 'team_start'
  | 'team_task_result'
  | 'team_task_start'
  | 'team_tool_call'
  | 'team_tool_result'

export type ChatStreamEvent = {
  type:
    | 'action'
    | 'done'
    | 'error'
    | 'message'
    | 'observation'
    | 'thinking'
    | TeamStreamEventType
  traceId?: string | null
  conversationId?: number | null
  step?: number | null
  content?: string | null
  payload?: JsonValue
  role?: 'EXECUTOR' | 'ORCHESTRATOR' | 'PLANNER' | 'REVIEWER'
  status?: string | null
  taskId?: string | null
  toolName?: string | null
  metadata?: JsonValue
}

export type RuntimeStatus = 'idle' | 'streaming' | 'done' | 'error'
