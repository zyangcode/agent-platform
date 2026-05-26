import { describe, expect, it } from 'vitest'
import type { Application, PageResult } from '@/lib/api/types'
import { resolveSelectedApplicationId } from './application-selection-utils'

function page(records: Application[]): PageResult<Application> {
  return {
    pageNo: 1,
    pageSize: 20,
    records,
    total: records.length,
    totalPages: 1,
  }
}

function application(applicationId: number, status = 'ACTIVE'): Application {
  return {
    applicationId,
    createdAt: '2026-05-26T00:00:00',
    description: null,
    name: `Application ${applicationId}`,
    status,
  }
}

describe('resolveSelectedApplicationId', () => {
  it('keeps the current active application selected', () => {
    const applications = page([application(1), application(2)])

    expect(resolveSelectedApplicationId(applications, 2, 1)).toBe(2)
  })

  it('uses stored active application when current application is disabled', () => {
    const applications = page([application(1, 'DISABLED'), application(2)])

    expect(resolveSelectedApplicationId(applications, 1, 2)).toBe(2)
  })

  it('falls back to the first active application before disabled records', () => {
    const applications = page([application(1, 'DISABLED'), application(2), application(3)])

    expect(resolveSelectedApplicationId(applications, null, null)).toBe(2)
  })

  it('returns disabled application only when no active application exists', () => {
    const applications = page([application(1, 'DISABLED')])

    expect(resolveSelectedApplicationId(applications, null, null)).toBe(1)
  })
})
