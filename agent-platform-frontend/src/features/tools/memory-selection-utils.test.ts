import { describe, expect, it } from 'vitest'
import type { Profile } from '@/lib/api/types'
import { selectMemoryProfileId } from './memory-selection-utils'

function profile(profileId: number, applicationId = 4, status = 'DRAFT'): Profile {
  return {
    applicationId,
    maxSteps: 5,
    memoryStrategy: { mode: 'READ_WRITE' },
    modelConfigId: 1,
    name: `Profile ${profileId}`,
    profileId,
    profileType: 'GENERAL',
    promptExtra: '',
    skillBindings: [],
    mcpToolBindings: [],
    status,
    visibility: 'PRIVATE',
  }
}

describe('memory selection helpers', () => {
  it('keeps the stored profile when it belongs to the selected application profiles', () => {
    expect(selectMemoryProfileId([profile(2), profile(3)], 3)).toBe(3)
  })

  it('falls back to the first profile instead of keeping a profile from another application', () => {
    expect(selectMemoryProfileId([profile(2, 3)], 3)).toBe(2)
  })

  it('supports querying all profiles when an application has no profiles', () => {
    expect(selectMemoryProfileId([], 3)).toBeNull()
  })
})
