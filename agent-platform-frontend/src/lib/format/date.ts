export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'Pending'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('en-US', {
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    month: 'short',
  }).format(date)
}

export function formatLatency(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return '-'
  }

  if (value < 1000) {
    return `${value}ms`
  }

  return `${(value / 1000).toFixed(1)}s`
}
