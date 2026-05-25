import { apiClient } from '@/lib/api/client'
import type { PageResult, TraceDetail, TraceSummary } from '@/lib/api/types'

export type TraceStatusFilter = '' | 'FAILED' | 'RUNNING' | 'SUCCESS'
export type TraceEntrypointFilter = '' | 'API_KEY' | 'INTERNAL_WEB' | 'WEB'

export type ListTracesQuery = {
  applicationId?: number | null
  profileId?: number | null
  modelConfigId?: number | null
  status?: TraceStatusFilter
  entrypoint?: TraceEntrypointFilter
  pageNo?: number
  pageSize?: number
}

export function listTraces(query: ListTracesQuery = {}) {
  return apiClient.get<PageResult<TraceSummary>>('/traces', {
    query: {
      applicationId: query.applicationId,
      entrypoint: query.entrypoint,
      modelConfigId: query.modelConfigId,
      pageNo: query.pageNo ?? 1,
      pageSize: query.pageSize ?? 20,
      profileId: query.profileId,
      status: query.status,
    },
  })
}

export function getTrace(traceId: string) {
  return apiClient.get<TraceDetail>(`/traces/${traceId}`)
}
