import { describe, expect, it } from 'vitest'
import { getTraceStatusVariant, sortTraceSpans } from './trace-utils'
import type { TraceSpan } from '@/lib/api/types'

describe('trace-utils', () => {
  it('maps trace statuses to badge variants', () => {
    expect(getTraceStatusVariant('SUCCESS')).toBe('success')
    expect(getTraceStatusVariant('FAILED')).toBe('danger')
    expect(getTraceStatusVariant('RUNNING')).toBe('warning')
    expect(getTraceStatusVariant('UNKNOWN')).toBe('muted')
  })

  it('sorts spans by startedAt while keeping missing dates last', () => {
    const spans = [
      { id: 3, traceId: 'tr_1', spanName: 'c', spanType: 'MODEL', component: 'core', status: 'SUCCESS' },
      {
        id: 2,
        traceId: 'tr_1',
        spanName: 'b',
        spanType: 'MODEL',
        component: 'core',
        status: 'SUCCESS',
        startedAt: '2026-05-24T10:02:00Z',
      },
      {
        id: 1,
        traceId: 'tr_1',
        spanName: 'a',
        spanType: 'GATEWAY',
        component: 'gateway',
        status: 'SUCCESS',
        startedAt: '2026-05-24T10:01:00Z',
      },
    ] satisfies TraceSpan[]

    expect(sortTraceSpans(spans).map((span) => span.id)).toEqual([1, 2, 3])
  })
})
