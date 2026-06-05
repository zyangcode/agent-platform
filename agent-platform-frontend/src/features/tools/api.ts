import { apiClient } from '@/lib/api/client'
import type {
  ExperienceSkill,
  JarSkillRegistrationResult,
  JsonValue,
  MemoryRecord,
  McpServer,
  McpTool,
  PageResult,
  RagIngestResult,
  RagSearchResult,
  Skill,
} from '@/lib/api/types'

export type SkillStatusFilter = '' | 'DISABLED' | 'ENABLED' | 'FAILED' | 'LOADED' | 'UPLOADED' | 'VALIDATING'
export type SkillScopeFilter = '' | 'GLOBAL' | 'PERSONAL' | 'PROFILE'
export type McpStatusFilter = '' | 'AVAILABLE' | 'UNAVAILABLE'
export type McpServerStatusFilter = '' | 'ACTIVE' | 'DISABLED' | 'UNAVAILABLE'

export type CreateExperienceSkillPayload = {
  applicationId: number
  profileId?: number | null
  code: string
  name: string
  domain?: string
  triggerKeywords?: string[]
  content: string
}

export type CreateMcpServerPayload = {
  name: string
  serverType: 'HTTP' | 'STDIO'
  connectionConfig: JsonValue
}

export type CreateRagDocumentPayload = {
  applicationId: number
  profileId?: number | null
  title: string
  sourceType?: string
  sourceUri?: string
  content: string
  chunkTokenBudget?: number | null
  overlapTokens?: number | null
}

export type SearchRagDocumentsParams = {
  applicationId: number
  profileId?: number | null
  query: string
  topK?: number
}

export type ListMemoriesParams = {
  applicationId: number
  profileId?: number | null
  category?: string | null
  query?: string | null
  limit?: number | null
}

export type UpdateMemoryPayload = {
  applicationId: number
  profileId?: number | null
  content: string
  memoryCategory?: string | null
  tags?: string[] | null
  importance?: number | null
  slotHint?: string | null
}

export function listSkills(scope: SkillScopeFilter, status: SkillStatusFilter) {
  return apiClient.get<Skill[]>('/skills', {
    query: { scope, status },
  })
}

export function listMcpTools(status: McpStatusFilter) {
  return apiClient.get<McpTool[]>('/mcp-tools', {
    query: { status },
  })
}

export function uploadJarSkill(scope: SkillScopeFilter, jarFile: File, manifestJson: string) {
  const formData = new FormData()
  formData.append('jarFile', jarFile)
  formData.append(
    'manifest',
    new Blob([manifestJson], { type: 'application/json' }),
    'manifest.json',
  )
  return apiClient.post<JarSkillRegistrationResult>('/skills/jar', formData, {
    query: { scope },
  })
}

export function listExperienceSkills(applicationId: number, pageNo = 1, pageSize = 20) {
  return apiClient.get<PageResult<ExperienceSkill>>('/experience-skills', {
    query: { applicationId, pageNo, pageSize },
  })
}

export function createExperienceSkill(payload: CreateExperienceSkillPayload) {
  return apiClient.post<ExperienceSkill>('/experience-skills', payload)
}

export function disableExperienceSkill(experienceSkillId: number, applicationId: number) {
  return apiClient.post<ExperienceSkill>(`/experience-skills/${experienceSkillId}/disable`, undefined, {
    query: { applicationId },
  })
}

export function listMcpServers(status: McpServerStatusFilter) {
  return apiClient.get<McpServer[]>('/mcp-servers', {
    query: { status },
  })
}

export function createMcpServer(payload: CreateMcpServerPayload) {
  return apiClient.post<McpServer>('/mcp-servers', payload)
}

export function disableMcpServer(mcpServerId: number) {
  return apiClient.post<McpServer>(`/mcp-servers/${mcpServerId}/disable`)
}

export function createRagDocument(payload: CreateRagDocumentPayload) {
  return apiClient.post<RagIngestResult>('/rag/documents', payload)
}

export function searchRagDocuments(params: SearchRagDocumentsParams) {
  return apiClient.get<RagSearchResult[]>('/rag/search', {
    query: params,
  })
}

export function deleteRagDocument(documentId: number, applicationId: number, profileId?: number | null) {
  return apiClient.delete<number>(`/rag/documents/${documentId}`, {
    query: { applicationId, profileId },
  })
}

export function listMemories(params: ListMemoriesParams) {
  return apiClient.get<MemoryRecord[]>('/memories', {
    query: params,
  })
}

export function updateMemory(memoryId: number, payload: UpdateMemoryPayload) {
  return apiClient.patch<MemoryRecord>(`/memories/${memoryId}`, payload)
}

export function disableMemory(memoryId: number, applicationId: number, profileId?: number | null) {
  return apiClient.delete<MemoryRecord>(`/memories/${memoryId}`, {
    query: { applicationId, profileId },
  })
}
