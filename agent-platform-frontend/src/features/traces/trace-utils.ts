import type { TraceSpan } from '@/lib/api/types'

export function getTraceStatusVariant(status: string | null | undefined) {
  const normalized = status?.toUpperCase()

  if (normalized === 'SUCCESS' || normalized === 'COMPLETED') {
    return 'success'
  }
  if (normalized === 'FAILED' || normalized === 'ERROR' || normalized === 'MODEL_ERROR') {
    return 'danger'
  }
  if (normalized === 'RUNNING' || normalized === 'PENDING') {
    return 'warning'
  }

  return 'muted'
}

export function sortTraceSpans(spans: TraceSpan[]) {
  return [...spans].sort((left, right) => {
    const leftTime = left.startedAt ? new Date(left.startedAt).getTime() : Number.MAX_SAFE_INTEGER
    const rightTime = right.startedAt ? new Date(right.startedAt).getTime() : Number.MAX_SAFE_INTEGER
    return leftTime - rightTime
  })
}

export function formatTraceTokenCount(totalTokens?: number | null) {
  if (totalTokens === undefined || totalTokens === null) {
    return '-'
  }

  return new Intl.NumberFormat('en-US').format(totalTokens)
}
