import { describe, expect, it } from 'vitest'
import { loadLastSelectedProfileId, saveLastSelectedProfileId } from './profile-selection-storage'

function memoryStorage(initial?: Record<string, string>) {
  const data = new Map(Object.entries(initial ?? {}))
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value)
    },
  }
}

describe('profile selection storage', () => {
  it('stores the selected profile per application', () => {
    const storage = memoryStorage()

    saveLastSelectedProfileId(1, 101, storage)
    saveLastSelectedProfileId(2, 202, storage)

    expect(loadLastSelectedProfileId(1, storage)).toBe(101)
    expect(loadLastSelectedProfileId(2, storage)).toBe(202)
  })

  it('ignores invalid stored data', () => {
    const storage = memoryStorage({ 'agent-platform.profile.selected-by-application': 'bad-json' })

    expect(loadLastSelectedProfileId(1, storage)).toBeNull()
  })
})
