import { apiClient } from '@/lib/api/client'
import type { JsonValue, McpTool, ModelConfig, PageResult, Profile, Skill } from '@/lib/api/types'

export type CreateProfileRequest = {
  applicationId: number
  name: string
  profileType: string
  description?: string
  modelConfigId: number
  promptExtra?: string
  memoryStrategy?: JsonValue
  maxSteps?: number
  visibility: string
}

export function listProfiles(applicationId: number, pageNo = 1, pageSize = 20) {
  return apiClient.get<PageResult<Profile>>('/profiles', {
    query: { applicationId, pageNo, pageSize },
  })
}

export function createProfile(request: CreateProfileRequest) {
  return apiClient.post<Profile>('/profiles', request)
}

export function getProfile(profileId: number) {
  return apiClient.get<Profile>(`/profiles/${profileId}`)
}

export function bindProfileSkills(profileId: number, skillIds: number[]) {
  return apiClient.put<boolean>(`/profiles/${profileId}/skills`, { skillIds })
}

export function bindProfileMcpTools(profileId: number, mcpToolIds: number[]) {
  return apiClient.put<boolean>(`/profiles/${profileId}/mcp-tools`, { mcpToolIds })
}

export function listModelConfigs() {
  return apiClient.get<ModelConfig[]>('/model-configs')
}

export function listSkills() {
  return apiClient.get<Skill[]>('/skills', {
    query: { status: 'ENABLED' },
  })
}

export function listMcpTools() {
  return apiClient.get<McpTool[]>('/mcp-tools', {
    query: { status: 'ENABLED' },
  })
}
