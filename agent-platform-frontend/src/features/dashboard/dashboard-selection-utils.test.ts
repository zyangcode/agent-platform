import { describe, expect, it } from 'vitest'
import type { Application, PageResult } from '@/lib/api/types'
import { buildDashboardScopedQuery, resolveDashboardApplicationId } from './dashboard-selection-utils'

function page(records: Application[]): PageResult<Application> {
  return {
    pageNo: 1,
    pageSize: 20,
    records,
    total: records.length,
    totalPages: records.length > 0 ? 1 : 0,
  }
}

describe('dashboard selection utils', () => {
  it('prefers the stored active application for dashboard scoped queries', () => {
    const applications = page([
      { applicationId: 1, createdAt: '', name: 'Old', status: 'DISABLED' },
      { applicationId: 2, createdAt: '', name: 'Daily', status: 'ACTIVE' },
      { applicationId: 3, createdAt: '', name: 'Work', status: 'ACTIVE' },
    ])

    expect(resolveDashboardApplicationId(applications, 3)).toBe(3)
  })

  it('falls back to the first active application', () => {
    const applications = page([
      { applicationId: 1, createdAt: '', name: 'Old', status: 'DISABLED' },
      { applicationId: 2, createdAt: '', name: 'Daily', status: 'ACTIVE' },
    ])

    expect(resolveDashboardApplicationId(applications, 99)).toBe(2)
  })

  it('builds application scoped query only when an application exists', () => {
    expect(buildDashboardScopedQuery(2)).toEqual({ applicationId: 2 })
    expect(buildDashboardScopedQuery(null)).toEqual({})
  })
})
