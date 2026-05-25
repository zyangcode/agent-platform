import { describe, expect, it } from 'vitest'
import { getPageNavigationState } from './pagination-utils'

describe('pagination-utils', () => {
  it('disables previous on first page and enables next when more pages exist', () => {
    expect(getPageNavigationState({ pageNo: 1, totalPages: 3 })).toEqual({
      canGoNext: true,
      canGoPrevious: false,
      currentPage: 1,
      totalPages: 3,
    })
  })

  it('clamps invalid page values to a stable display state', () => {
    expect(getPageNavigationState({ pageNo: 0, totalPages: 0 })).toEqual({
      canGoNext: false,
      canGoPrevious: false,
      currentPage: 1,
      totalPages: 1,
    })
  })
})
