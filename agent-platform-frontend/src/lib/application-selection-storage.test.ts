import { describe, expect, it, vi } from 'vitest'
import { loadLastSelectedApplicationId, saveLastSelectedApplicationId } from './application-selection-storage'

describe('application selection storage', () => {
  it('saves and loads selected application id', () => {
    const storage = createStorage()

    saveLastSelectedApplicationId(3, storage)

    expect(loadLastSelectedApplicationId(storage)).toBe(3)
  })

  it('ignores invalid stored value', () => {
    const storage = createStorage()
    storage.setItem('agent-platform.application.last-selected', 'broken')

    expect(loadLastSelectedApplicationId(storage)).toBeNull()
  })

  it('clears selected application id', () => {
    const storage = createStorage()
    saveLastSelectedApplicationId(null, storage)

    expect(storage.removeItem).toHaveBeenCalledWith('agent-platform.application.last-selected')
  })
})

function createStorage() {
  const values = new Map<string, string>()
  return {
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    removeItem: vi.fn((key: string) => values.delete(key)),
    setItem: vi.fn((key: string, value: string) => values.set(key, value)),
  }
}
