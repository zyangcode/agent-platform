import { apiClient } from '@/lib/api/client'
import type { PageResult, Profile } from '@/lib/api/types'

export function listProfiles(applicationId: number, pageNo = 1, pageSize = 20) {
  return apiClient.get<PageResult<Profile>>('/profiles', {
    query: { applicationId, pageNo, pageSize },
  })
}
