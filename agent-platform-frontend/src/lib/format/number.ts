export function formatInteger(value: number | null | undefined) {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 0,
  }).format(value ?? 0)
}

export function formatCompact(value: number | null | undefined) {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 1,
    notation: 'compact',
  }).format(value ?? 0)
}
