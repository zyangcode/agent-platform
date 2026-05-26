const STORAGE_KEY = 'agent-platform.application.last-selected'

export function loadLastSelectedApplicationId(storage: Pick<Storage, 'getItem'> = localStorage) {
  const raw = storage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }

  const applicationId = Number(raw)
  return Number.isFinite(applicationId) && applicationId > 0 ? applicationId : null
}

export function saveLastSelectedApplicationId(
  applicationId: number | null,
  storage: Pick<Storage, 'removeItem' | 'setItem'> = localStorage,
) {
  if (!applicationId) {
    storage.removeItem(STORAGE_KEY)
    return
  }

  storage.setItem(STORAGE_KEY, String(applicationId))
}
