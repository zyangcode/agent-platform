import { apiClient } from '@/lib/api/client'
import type {
  Application,
  PageResult,
  TokenUsageSummary,
  TraceSummary,
} from '@/lib/api/types'

export type DashboardData = {
  applications: PageResult<Application>
  recentTraces: PageResult<TraceSummary>
  tokenSummary: TokenUsageSummary
}

const emptyTokenSummary: TokenUsageSummary = {
  applicationId: null,
  completionTokens: 0,
  estimatedCount: 0,
  promptTokens: 0,
  realUsageCount: 0,
  requestCount: 0,
  topModels: [],
  totalTokens: 0,
}

export async function getDashboardData(): Promise<DashboardData> {
  const [applications, recentTraces, tokenSummary] = await Promise.all([
    apiClient.get<PageResult<Application>>('/applications', {
      query: { pageNo: 1, pageSize: 20 },
    }),
    apiClient.get<PageResult<TraceSummary>>('/traces', {
      query: { pageNo: 1, pageSize: 5 },
    }),
    apiClient.get<TokenUsageSummary>('/token-usages/summary'),
  ])

  return {
    applications,
    recentTraces,
    tokenSummary: tokenSummary ?? emptyTokenSummary,
  }
}
