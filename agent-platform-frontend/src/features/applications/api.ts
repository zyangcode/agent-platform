import { apiClient } from '@/lib/api/client'
import type {
  ApiKey,
  Application,
  CreateApplicationResult,
  PageResult,
  RevokeApiKeyResult,
} from '@/lib/api/types'

export type CreateApplicationRequest = {
  name: string
  description?: string
}

export function listApplications(pageNo = 1, pageSize = 20) {
  return apiClient.get<PageResult<Application>>('/applications', {
    query: { pageNo, pageSize },
  })
}

export function createApplication(request: CreateApplicationRequest) {
  return apiClient.post<CreateApplicationResult>('/applications', request)
}

export function listApiKeys(applicationId: number) {
  return apiClient.get<ApiKey[]>(`/applications/${applicationId}/api-keys`)
}

export function revokeApiKey(applicationId: number, apiKeyId: number) {
  return apiClient.post<RevokeApiKeyResult>(
    `/applications/${applicationId}/api-keys/${apiKeyId}/revoke`,
  )
}
