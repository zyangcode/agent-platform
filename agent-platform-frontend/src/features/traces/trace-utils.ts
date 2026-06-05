import type { TokenUsage, TraceSpan } from '@/lib/api/types'
import type { Locale } from '@/lib/i18n/i18n-context-value'

const statusLabels: Record<string, Record<Locale, string>> = {
  COMPLETED: { en: 'Completed', zh: '已完成' },
  ERROR: { en: 'Error', zh: '错误' },
  FAILED: { en: 'Failed', zh: '失败' },
  MODEL_ERROR: { en: 'Model error', zh: '模型错误' },
  PENDING: { en: 'Pending', zh: '等待中' },
  RUNNING: { en: 'Running', zh: '运行中' },
  SUCCESS: { en: 'Success', zh: '成功' },
}

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

export function formatTraceStatus(status: string | null | undefined, locale: Locale) {
  if (!status) {
    return '-'
  }

  const normalized = status.toUpperCase()
  return statusLabels[normalized]?.[locale] ?? status
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

export function findTokenUsageForSpan(span: TraceSpan | null | undefined, tokenUsages: TokenUsage[]) {
  if (!span || !span.id || tokenUsages.length === 0) {
    return null
  }

  return tokenUsages.find((usage) => usage.spanId === span.id) ?? null
}

export function getTokenUsageTimelineFacts(tokenUsage: TokenUsage | null | undefined, locale: Locale) {
  if (!tokenUsage) {
    return []
  }

  return [
    { label: locale === 'zh' ? '模型' : 'Model', value: tokenUsage.modelName || '-' },
    { label: 'Token', value: formatTraceTokenCount(tokenUsage.totalTokens) },
    { label: locale === 'zh' ? '来源' : 'Source', value: tokenUsage.estimated ? estimatedLabel(locale) : realLabel(locale) },
  ]
}

function estimatedLabel(locale: Locale) {
  return locale === 'zh' ? '估算' : 'estimated'
}

function realLabel(locale: Locale) {
  return locale === 'zh' ? '真实' : 'real'
}

export function getTraceModelCallSummary(spans: TraceSpan[], tokenUsages: TokenUsage[]) {
  const modelSpans = spans.filter((span) => span.spanName === 'model.invoke')
  const linkedUsages = modelSpans
    .map((span) => findTokenUsageForSpan(span, tokenUsages))
    .filter((usage): usage is TokenUsage => usage != null)
  const totalTokens = linkedUsages.reduce((sum, usage) => sum + usage.totalTokens, 0)
  const estimatedCount = linkedUsages.filter((usage) => usage.estimated).length
  const slowestLatencyMs = modelSpans.reduce((max, span) => Math.max(max, span.latencyMs ?? 0), 0)

  return {
    estimatedCount,
    estimatedRatio: linkedUsages.length === 0 ? 0 : Math.round((estimatedCount / linkedUsages.length) * 100),
    modelCallCount: modelSpans.length,
    slowestLatencyMs,
    totalTokens,
  }
}
