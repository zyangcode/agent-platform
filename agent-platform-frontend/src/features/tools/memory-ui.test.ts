import { describe, expect, it } from 'vitest'
import { clampMemoryImportance, formatMemoryTimestamp, parseMemoryTags } from './memory-ui'

describe('memory UI helpers', () => {
  it('parses comma-separated tags into unique trimmed values', () => {
    expect(parseMemoryTags(' sports, style, sports,  preference ')).toEqual([
      'sports',
      'style',
      'preference',
    ])
  })

  it('clamps importance into the backend score range', () => {
    expect(clampMemoryImportance('1.4')).toBe(1)
    expect(clampMemoryImportance('-0.2')).toBe(0)
    expect(clampMemoryImportance('0.75')).toBe(0.75)
  })

  it('formats missing timestamps as a stable placeholder', () => {
    expect(formatMemoryTimestamp(null)).toBe('-')
    expect(formatMemoryTimestamp(undefined)).toBe('-')
  })
})
