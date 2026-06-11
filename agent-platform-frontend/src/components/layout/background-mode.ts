export const BACKGROUND_STORAGE_KEY = 'agent-platform.background'

export const BACKGROUND_MODES = ['aurora', 'soft'] as const

export type BackgroundMode = (typeof BACKGROUND_MODES)[number]

export function normalizeBackgroundMode(value: string | null): BackgroundMode {
  return BACKGROUND_MODES.includes(value as BackgroundMode) ? (value as BackgroundMode) : 'aurora'
}

export function getNextBackgroundMode(mode: BackgroundMode): BackgroundMode {
  const currentIndex = BACKGROUND_MODES.indexOf(mode)
  return BACKGROUND_MODES[(currentIndex + 1) % BACKGROUND_MODES.length]
}
