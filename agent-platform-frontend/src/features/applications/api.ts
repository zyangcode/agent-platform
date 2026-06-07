import { apiClient } from '@/lib/api/client'
import type {
  ApiKey,
  Application,
  CreateApplicationResult,
  CreatedApiKey,
  PageResult,
  RevokeApiKeyResult,
} from '@/lib/api/types'

export type CreateApplicationRequest = {
  name: string
  description?: string
}

export type UpdateApplicationRequest = {
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

export function updateApplication(applicationId: number, request: UpdateApplicationRequest) {
  return apiClient.put<Application>(`/applications/${applicationId}`, request)
}

export function disableApplication(applicationId: number) {
  return apiClient.post<Application>(`/applications/${applicationId}/disable`)
}

export function enableApplication(applicationId: number) {
  return apiClient.post<Application>(`/applications/${applicationId}/activate`)
}

export function regenerateApiKey(applicationId: number) {
  return apiClient.post<CreatedApiKey>(`/applications/${applicationId}/api-keys/regenerate`)
}

export function listApiKeys(applicationId: number) {
  return apiClient.get<ApiKey[]>(`/applications/${applicationId}/api-keys`)
}

export function revokeApiKey(applicationId: number, apiKeyId: number) {
  return apiClient.post<RevokeApiKeyResult>(
    `/applications/${applicationId}/api-keys/${apiKeyId}/revoke`,
  )
}
