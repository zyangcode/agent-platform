import { resolveSelectedApplicationId } from '@/features/applications/application-selection-utils'
import type { Application, PageResult } from '@/lib/api/types'

export function resolveDashboardApplicationId(
  applications: PageResult<Application>,
  storedApplicationId: number | null,
) {
  return resolveSelectedApplicationId(applications, storedApplicationId, storedApplicationId)
}

export function buildDashboardScopedQuery(applicationId: number | null) {
  return applicationId ? { applicationId } : {}
}
