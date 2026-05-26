import { describe, expect, it } from 'vitest'
import type { Profile } from '@/lib/api/types'
import { isRunnableProfile, selectRunnableProfileId } from './chat-profile-selection-utils'

function profile(profileId: number, status = 'DRAFT'): Profile {
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
    status,
    visibility: 'PRIVATE',
  }
}

describe('chat profile selection', () => {
  it('allows draft and published profiles to run', () => {
    expect(isRunnableProfile(profile(1, 'DRAFT'))).toBe(true)
    expect(isRunnableProfile(profile(2, 'PUBLISHED'))).toBe(true)
  })

  it('rejects disabled profiles for chat runtime', () => {
    expect(isRunnableProfile(profile(1, 'DISABLED'))).toBe(false)
  })

  it('keeps a stored profile only when it is runnable', () => {
    const profiles = [profile(1, 'DISABLED'), profile(2, 'DRAFT')]

    expect(selectRunnableProfileId(profiles, 1)).toBe(2)
    expect(selectRunnableProfileId(profiles, 2)).toBe(2)
  })
})
