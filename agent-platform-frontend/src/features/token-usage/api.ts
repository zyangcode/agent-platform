import { apiClient } from '@/lib/api/client'
import type { PageResult, TokenUsage, TokenUsageSummary } from '@/lib/api/types'

export type ListTokenUsagesQuery = {
  applicationId?: number | null
  modelConfigId?: number | null
  pageNo?: number
  pageSize?: number
  providerId?: number | null
}

export type TokenUsageSummaryQuery = {
  applicationId?: number | null
  startedFrom?: string
  startedTo?: string
}

export function listTokenUsages(query: ListTokenUsagesQuery = {}) {
  return apiClient.get<PageResult<TokenUsage>>('/token-usages', {
    query: {
      applicationId: query.applicationId,
      modelConfigId: query.modelConfigId,
      pageNo: query.pageNo ?? 1,
      pageSize: query.pageSize ?? 20,
      providerId: query.providerId,
    },
  })
}

export function getTokenUsageSummary(query: TokenUsageSummaryQuery = {}) {
  return apiClient.get<TokenUsageSummary>('/token-usages/summary', {
    query: {
      applicationId: query.applicationId,
      startedFrom: query.startedFrom,
      startedTo: query.startedTo,
    },
  })
}
