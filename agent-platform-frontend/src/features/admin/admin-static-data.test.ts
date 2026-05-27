import { describe, expect, it } from 'vitest'
import { getAdminStatusLabel, getAdminStatusVariant, summarizeCurrentUser } from './admin-static-data'

describe('admin static data helpers', () => {
  it('maps policy status to badge variants', () => {
    expect(getAdminStatusVariant('active')).toBe('success')
    expect(getAdminStatusVariant('manual')).toBe('warning')
    expect(getAdminStatusVariant('deferred')).toBe('muted')
  })

  it('returns localized policy status labels', () => {
    expect(getAdminStatusLabel('active', 'zh')).toBe('已启用')
    expect(getAdminStatusLabel('manual', 'en')).toBe('Manual')
    expect(getAdminStatusLabel('deferred', 'zh')).toBe('暂缓')
  })

  it('summarizes the current user without leaking nullable fields into UI', () => {
    expect(
      summarizeCurrentUser({
        displayName: 'Admin',
        roles: ['ADMIN', 'USER'],
        tenantId: 1,
        userId: 7,
        username: 'admin',
      }),
    ).toEqual({
      displayName: 'Admin',
      roleCount: 2,
      roles: ['ADMIN', 'USER'],
      tenantId: '1',
      userId: '7',
      username: 'admin',
    })

    expect(summarizeCurrentUser(null)).toEqual({
      displayName: '-',
      roleCount: 0,
      roles: ['-'],
      tenantId: '-',
      userId: '-',
      username: '-',
    })
  })
})
