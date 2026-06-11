import { describe, expect, it } from 'vitest'
import { getNextBackgroundMode, normalizeBackgroundMode } from './background-mode'

describe('background mode', () => {
  it('cycles through available console backgrounds', () => {
    expect(getNextBackgroundMode('aurora')).toBe('soft')
    expect(getNextBackgroundMode('soft')).toBe('aurora')
  })

  it('falls back to aurora when stored value is invalid', () => {
    expect(normalizeBackgroundMode(null)).toBe('aurora')
    expect(normalizeBackgroundMode('unknown')).toBe('aurora')
    expect(normalizeBackgroundMode('plain')).toBe('aurora')
    expect(normalizeBackgroundMode('soft')).toBe('soft')
  })
})
