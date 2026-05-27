export function hasAnyRequiredRole(userRoles: string[] | undefined, requiredRoles: string[] | undefined) {
  if (!requiredRoles?.length) {
    return true
  }

  const normalizedRoles = new Set(
    (userRoles ?? []).flatMap((role) => [role.toUpperCase(), role.replace(/^ROLE_/, '').toUpperCase()]),
  )

  return requiredRoles.some((role) => normalizedRoles.has(role.toUpperCase()))
}
