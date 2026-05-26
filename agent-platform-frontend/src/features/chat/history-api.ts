import { apiClient } from '@/lib/api/client'
import type { ConversationMessage, ConversationSummary } from '@/lib/api/types'

export function listConversations(applicationId?: number | null, profileId?: number | null, limit = 20) {
  return apiClient.get<ConversationSummary[]>('/conversations', {
    query: {
      applicationId: applicationId ?? undefined,
      limit,
      profileId: profileId ?? undefined,
    },
  })
}

export function listConversationMessages(
  conversationId: number,
  applicationId: number,
  profileId: number,
  limit = 50,
) {
  return apiClient.get<ConversationMessage[]>(`/conversations/${conversationId}/messages`, {
    query: {
      applicationId,
      limit,
      profileId,
    },
  })
}
