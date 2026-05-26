import type { Application, PageResult } from '@/lib/api/types'

function isActiveApplication(application: Application) {
  return application.status.toUpperCase() === 'ACTIVE'
}

function findApplication(
  applications: Application[],
  applicationId?: number | null,
  predicate: (application: Application) => boolean = () => true,
) {
  if (!applicationId) {
    return null
  }

  return (
    applications.find(
      (application) => application.applicationId === applicationId && predicate(application),
    ) ?? null
  )
}

export function resolveSelectedApplicationId(
  applications: PageResult<Application>,
  currentApplicationId: number | null,
  storedApplicationId: number | null,
) {
  if (applications.records.length === 0) {
    return null
  }

  return (
    findApplication(applications.records, currentApplicationId, isActiveApplication)?.applicationId ??
    findApplication(applications.records, storedApplicationId, isActiveApplication)?.applicationId ??
    applications.records.find(isActiveApplication)?.applicationId ??
    applications.records[0].applicationId
  )
}
