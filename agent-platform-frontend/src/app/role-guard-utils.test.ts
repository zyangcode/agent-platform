import { describe, expect, it } from 'vitest'
import { hasAnyRequiredRole } from './role-guard-utils'

describe('role guard utils', () => {
  it('allows routes without required roles', () => {
    expect(hasAnyRequiredRole(undefined, undefined)).toBe(true)
    expect(hasAnyRequiredRole([], [])).toBe(true)
  })

  it('accepts plain and Spring Security prefixed roles', () => {
    expect(hasAnyRequiredRole(['ADMIN'], ['ADMIN'])).toBe(true)
    expect(hasAnyRequiredRole(['ROLE_ADMIN'], ['ADMIN'])).toBe(true)
  })

  it('rejects users without a required role', () => {
    expect(hasAnyRequiredRole(['USER'], ['ADMIN'])).toBe(false)
  })
})
