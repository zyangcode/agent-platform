const STORAGE_KEY = 'agent-platform.profile.selected-by-application'

type ProfileSelectionStorage = Pick<Storage, 'getItem' | 'setItem'>

export function loadLastSelectedProfileId(
  applicationId?: number | null,
  storage: ProfileSelectionStorage = localStorage,
) {
  if (!applicationId) {
    return null
  }

  try {
    const raw = storage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Record<string, unknown>
    const value = parsed[String(applicationId)]
    return typeof value === 'number' && Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

export function saveLastSelectedProfileId(
  applicationId?: number | null,
  profileId?: number | null,
  storage: ProfileSelectionStorage = localStorage,
) {
  if (!applicationId || !profileId) {
    return
  }

  const next = loadSelectionMap(storage)
  next[String(applicationId)] = profileId
  storage.setItem(STORAGE_KEY, JSON.stringify(next))
}

function loadSelectionMap(storage: ProfileSelectionStorage) {
  try {
    const raw = storage.getItem(STORAGE_KEY)
    if (!raw) {
      return {} as Record<string, number>
    }
    const parsed = JSON.parse(raw) as Record<string, unknown>
    return Object.fromEntries(
      Object.entries(parsed).filter((entry): entry is [string, number] => typeof entry[1] === 'number'),
    )
  } catch {
    return {} as Record<string, number>
  }
}
