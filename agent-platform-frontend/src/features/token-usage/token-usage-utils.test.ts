import { describe, expect, it } from 'vitest'
import type { TokenUsageSummary } from '@/lib/api/types'
import {
  buildTokenSplitData,
  buildTopModelRows,
  formatTokenShare,
  getEstimateRatio,
  getUsageEstimateVariant,
} from './token-usage-utils'

describe('token-usage-utils', () => {
  const summary = {
    applicationId: 1,
    completionTokens: 400,
    estimatedCount: 2,
    promptTokens: 600,
    realUsageCount: 6,
    requestCount: 8,
    topModels: [
      {
        modelConfigId: 2,
        modelName: 'qwen-plus',
        providerType: 'qwen',
        requestCount: 2,
        totalTokens: 300,
      },
      {
        modelConfigId: 1,
        modelName: 'mock-chat',
        providerType: 'mock',
        requestCount: 6,
        totalTokens: 700,
      },
    ],
    totalTokens: 1000,
  } satisfies TokenUsageSummary

  it('builds prompt and completion split data from summary totals', () => {
    expect(buildTokenSplitData(summary)).toEqual([
      { label: 'Prompt', percent: 60, tokens: 600 },
      { label: 'Completion', percent: 40, tokens: 400 },
    ])
  })

  it('sorts top models by token volume and calculates shares', () => {
    expect(buildTopModelRows(summary)).toEqual([
      {
        modelConfigId: 1,
        modelName: 'mock-chat',
        providerType: 'mock',
        requestCount: 6,
        share: 70,
        totalTokens: 700,
      },
      {
        modelConfigId: 2,
        modelName: 'qwen-plus',
        providerType: 'qwen',
        requestCount: 2,
        share: 30,
        totalTokens: 300,
      },
    ])
  })

  it('formats zero totals without producing NaN percentages', () => {
    expect(formatTokenShare(0, 0)).toBe('0%')
    expect(getEstimateRatio({ ...summary, estimatedCount: 0, requestCount: 0 })).toBe(0)
  })

  it('maps estimated usage ratio to badge variants', () => {
    expect(getUsageEstimateVariant(0)).toBe('success')
    expect(getUsageEstimateVariant(33)).toBe('warning')
    expect(getUsageEstimateVariant(80)).toBe('danger')
  })
})
