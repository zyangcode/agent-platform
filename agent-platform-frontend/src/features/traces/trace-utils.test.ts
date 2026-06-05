import { describe, expect, it } from 'vitest'
import {
  findTokenUsageForSpan,
  getTokenUsageTimelineFacts,
  getTraceModelCallSummary,
  getTraceStatusVariant,
  sortTraceSpans,
} from './trace-utils'
import type { TokenUsage, TraceSpan } from '@/lib/api/types'

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

  it('finds token usage linked to the selected span id', () => {
    const span = traceSpan({ id: 20, spanName: 'model.invoke', spanType: 'MODEL' })
    const usages = [
      tokenUsage({ id: 1, spanId: 10, totalTokens: 100 }),
      tokenUsage({ id: 2, spanId: 20, totalTokens: 240 }),
    ]

    expect(findTokenUsageForSpan(span, usages)?.id).toBe(2)
  })

  it('does not guess token usage for spans without an exact span id match', () => {
    expect(findTokenUsageForSpan(traceSpan({ id: 20 }), [tokenUsage({ spanId: null })])).toBeNull()
    expect(findTokenUsageForSpan(null, [tokenUsage({ spanId: 20 })])).toBeNull()
  })

  it('formats token usage facts for timeline badges', () => {
    expect(getTokenUsageTimelineFacts(tokenUsage({
      estimated: true,
      modelName: 'gpt-4.1-mini',
      totalTokens: 2400,
    }), 'zh')).toEqual([
      { label: '模型', value: 'gpt-4.1-mini' },
      { label: 'Token', value: '2,400' },
      { label: '来源', value: '估算' },
    ])
  })

  it('returns no token usage facts without linked usage', () => {
    expect(getTokenUsageTimelineFacts(null, 'en')).toEqual([])
  })

  it('summarizes model calls, tokens, estimate ratio, and slowest latency', () => {
    const spans = [
      traceSpan({ id: 10, latencyMs: 120, spanName: 'model.invoke' }),
      traceSpan({ id: 20, latencyMs: 860, spanName: 'model.invoke' }),
      traceSpan({ id: 30, latencyMs: 10, spanName: 'tool.execute', spanType: 'TOOL' }),
    ]
    const usages = [
      tokenUsage({ spanId: 10, totalTokens: 100, estimated: false }),
      tokenUsage({ spanId: 20, totalTokens: 300, estimated: true }),
      tokenUsage({ spanId: null, totalTokens: 999, estimated: true }),
    ]

    expect(getTraceModelCallSummary(spans, usages)).toEqual({
      estimatedCount: 1,
      estimatedRatio: 50,
      modelCallCount: 2,
      slowestLatencyMs: 860,
      totalTokens: 400,
    })
  })

  it('returns zero model call summary for traces without model spans', () => {
    expect(getTraceModelCallSummary([traceSpan({ spanName: 'tool.execute', spanType: 'TOOL' })], [])).toEqual({
      estimatedCount: 0,
      estimatedRatio: 0,
      modelCallCount: 0,
      slowestLatencyMs: 0,
      totalTokens: 0,
    })
  })
})

function traceSpan(overrides: Partial<TraceSpan> = {}): TraceSpan {
  return {
    component: 'core',
    id: 1,
    spanName: 'model.invoke',
    spanType: 'MODEL',
    status: 'SUCCESS',
    traceId: 'tr_1',
    ...overrides,
  }
}

function tokenUsage(overrides: Partial<TokenUsage> = {}): TokenUsage {
  return {
    applicationId: 20001,
    completionTokens: 40,
    createdAt: '2026-05-24T10:00:00Z',
    estimated: false,
    id: 1,
    modelConfigId: 30001,
    modelName: 'mock-chat',
    profileId: 50001,
    promptTokens: 60,
    providerId: 40001,
    providerType: 'mock',
    spanId: 1,
    tenantId: 1,
    totalTokens: 100,
    traceId: 'tr_1',
    userId: 10001,
    ...overrides,
  }
}
