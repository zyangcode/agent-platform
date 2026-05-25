import type { TokenUsageSummary, TokenUsageTopModel } from '@/lib/api/types'

export type TokenSplitDatum = {
  label: 'Completion' | 'Prompt'
  percent: number
  tokens: number
}

export type TopModelRow = TokenUsageTopModel & {
  share: number
}

function calculatePercent(part: number, total: number) {
  if (total <= 0) {
    return 0
  }

  return Math.round((part / total) * 100)
}

export function formatTokenShare(part: number, total: number) {
  return `${calculatePercent(part, total)}%`
}

export function buildTokenSplitData(summary: TokenUsageSummary): TokenSplitDatum[] {
  return [
    {
      label: 'Prompt',
      percent: calculatePercent(summary.promptTokens, summary.totalTokens),
      tokens: summary.promptTokens,
    },
    {
      label: 'Completion',
      percent: calculatePercent(summary.completionTokens, summary.totalTokens),
      tokens: summary.completionTokens,
    },
  ]
}

export function buildTopModelRows(summary: TokenUsageSummary): TopModelRow[] {
  return [...summary.topModels]
    .sort((left, right) => right.totalTokens - left.totalTokens)
    .map((model) => ({
      ...model,
      share: calculatePercent(model.totalTokens, summary.totalTokens),
    }))
}

export function getEstimateRatio(summary: Pick<TokenUsageSummary, 'estimatedCount' | 'requestCount'>) {
  return calculatePercent(summary.estimatedCount, summary.requestCount)
}

export function getUsageEstimateVariant(ratio: number) {
  if (ratio >= 50) {
    return 'danger'
  }
  if (ratio > 0) {
    return 'warning'
  }

  return 'success'
}
