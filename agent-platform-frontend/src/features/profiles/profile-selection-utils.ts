import type { Profile } from '@/lib/api/types'

export function selectProfileAfterReload(
  profiles: Profile[],
  preferredProfileId?: number | null,
  detailedProfile?: Profile | null,
) {
  if (detailedProfile && detailedProfile.profileId === preferredProfileId) {
    return detailedProfile
  }

  if (preferredProfileId) {
    return profiles.find((profile) => profile.profileId === preferredProfileId) ?? profiles[0] ?? null
  }

  return profiles[0] ?? null
}
