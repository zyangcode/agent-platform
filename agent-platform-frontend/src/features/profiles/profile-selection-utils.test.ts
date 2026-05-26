import { describe, expect, it } from 'vitest'
import type { Profile } from '@/lib/api/types'
import { selectProfileAfterReload } from './profile-selection-utils'

function profile(profileId: number): Profile {
  return {
    applicationId: 1,
    description: null,
    maxSteps: 3,
    mcpToolBindings: [],
    memoryStrategy: {},
    modelConfigId: 1,
    name: `Profile ${profileId}`,
    profileId,
    profileType: 'GENERAL',
    promptExtra: null,
    skillBindings: [],
    status: 'DRAFT',
    visibility: 'PRIVATE',
  }
}

describe('selectProfileAfterReload', () => {
  it('keeps the requested profile selected after reloading profile list', () => {
    const profiles = [profile(1), profile(4), profile(2)]

    expect(selectProfileAfterReload(profiles, 4)?.profileId).toBe(4)
  })

  it('falls back to the first profile when the requested profile is missing', () => {
    const profiles = [profile(1), profile(2)]

    expect(selectProfileAfterReload(profiles, 4)?.profileId).toBe(1)
  })
})
