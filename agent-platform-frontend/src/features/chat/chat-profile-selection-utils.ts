import type { Profile } from '@/lib/api/types'

export function isRunnableProfile(profile: Profile) {
  const status = profile.status.toUpperCase()
  return status === 'DRAFT' || status === 'PUBLISHED'
}

export function selectRunnableProfileId(profiles: Profile[], preferredProfileId?: number | null) {
  if (preferredProfileId) {
    const preferredProfile = profiles.find((profile) => profile.profileId === preferredProfileId)
    if (preferredProfile && isRunnableProfile(preferredProfile)) {
      return preferredProfile.profileId
    }
  }

  return profiles.find(isRunnableProfile)?.profileId ?? null
}
