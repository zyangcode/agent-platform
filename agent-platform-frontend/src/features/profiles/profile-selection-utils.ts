import type { Profile } from '@/lib/api/types'

function isEditableProfile(profile: Profile) {
  return profile.status.toUpperCase() === 'DRAFT'
}

export function selectProfileAfterReload(
  profiles: Profile[],
  preferredProfileId?: number | null,
  detailedProfile?: Profile | null,
) {
  if (
    detailedProfile &&
    detailedProfile.profileId === preferredProfileId &&
    isEditableProfile(detailedProfile)
  ) {
    return detailedProfile
  }

  if (preferredProfileId) {
    const preferredProfile = profiles.find((profile) => profile.profileId === preferredProfileId)
    if (preferredProfile && isEditableProfile(preferredProfile)) {
      return preferredProfile
    }
  }

  return profiles.find(isEditableProfile) ?? profiles[0] ?? null
}
