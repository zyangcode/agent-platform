import type { Profile } from '@/lib/api/types'

export function selectMemoryProfileId(profiles: Profile[], preferredProfileId?: number | null) {
  if (preferredProfileId && profiles.some((profile) => profile.profileId === preferredProfileId)) {
    return preferredProfileId
  }

  return profiles[0]?.profileId ?? null
}
