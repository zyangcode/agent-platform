import { apiClient } from '@/lib/api/client'
import type {
  Application,
  PageResult,
  TokenUsageSummary,
  TraceSummary,
} from '@/lib/api/types'
import { loadLastSelectedApplicationId } from '@/lib/application-selection-storage'
import { buildDashboardScopedQuery, resolveDashboardApplicationId } from './dashboard-selection-utils'

export type DashboardData = {
  applications: PageResult<Application>
  applicationId: number | null
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
  const applications = await apiClient.get<PageResult<Application>>('/applications', {
    query: { pageNo: 1, pageSize: 20 },
  })
  const applicationId = resolveDashboardApplicationId(applications, loadLastSelectedApplicationId())
  const scopedQuery = buildDashboardScopedQuery(applicationId)

  const [recentTraces, tokenSummary] = await Promise.all([
    apiClient.get<PageResult<TraceSummary>>('/traces', {
      query: { ...scopedQuery, pageNo: 1, pageSize: 5 },
    }),
    apiClient.get<TokenUsageSummary>('/token-usages/summary', {
      query: scopedQuery,
    }),
  ])

  return {
    applications,
    applicationId,
    recentTraces,
    tokenSummary: tokenSummary ?? emptyTokenSummary,
  }
}
