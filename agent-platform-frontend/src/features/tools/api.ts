import { apiClient } from '@/lib/api/client'
import type { McpTool, Skill } from '@/lib/api/types'

export type SkillStatusFilter = '' | 'DISABLED' | 'ENABLED' | 'FAILED' | 'LOADED' | 'UPLOADED' | 'VALIDATING'
export type SkillScopeFilter = '' | 'GLOBAL' | 'PERSONAL' | 'PROFILE'
export type McpStatusFilter = '' | 'DISABLED' | 'ENABLED' | 'FAILED'

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
