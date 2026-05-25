export function getStatusVariant(status: string) {
  const normalized = status.toUpperCase()

  if (normalized === 'ACTIVE' || normalized === 'ENABLED' || normalized === 'DRAFT') {
    return 'success'
  }
  if (normalized === 'FAILED' || normalized === 'DISABLED' || normalized === 'DELETED') {
    return 'danger'
  }

  return 'muted'
}

export function toggleNumber(values: number[], value: number) {
  return values.includes(value) ? values.filter((current) => current !== value) : [...values, value]
}
