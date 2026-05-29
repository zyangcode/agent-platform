import type { JsonValue, TraceSpan } from '@/lib/api/types'
import type { Locale } from '@/lib/i18n/i18n-context-value'

type SpanFact = {
  label: string
  value: string
}

const spanTitleLabels: Record<string, Record<Locale, string>> = {
  'team.plan': { en: 'Team plan', zh: 'Team 计划' },
  'team.review': { en: 'Team review', zh: 'Team 审查' },
  'team.run': { en: 'Team run', zh: 'Team 运行' },
  'team.task.execute': { en: 'Team task execution', zh: 'Team 任务执行' },
}

const factLabels: Record<string, Record<Locale, string>> = {
  profileId: { en: 'Profile ID', zh: 'Profile ID' },
  retryIndex: { en: 'Retry index', zh: '重试次数' },
  role: { en: 'Role', zh: '角色' },
  taskId: { en: 'Task ID', zh: '任务 ID' },
  taskType: { en: 'Task type', zh: '任务类型' },
  toolName: { en: 'Tool', zh: '工具' },
}

const factOrder = ['role', 'taskId', 'taskType', 'toolName', 'retryIndex', 'profileId'] as const

export function getTraceSpanTitle(span: TraceSpan, locale: Locale) {
  return spanTitleLabels[span.spanName]?.[locale] ?? span.spanName
}

export function getTraceSpanFacts(span: TraceSpan, locale: Locale): SpanFact[] {
  const attributes = asRecord(span.attributes)
  if (!attributes) {
    return []
  }

  return factOrder.flatMap((key) => {
    const value = formatFactValue(key, attributes[key])
    if (!value) {
      return []
    }

    return [{
      label: factLabels[key][locale],
      value,
    }]
  })
}

function formatFactValue(key: string, value: unknown) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  if (key === 'role' && typeof value === 'string') {
    return toTitleCase(value)
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  return null
}

function toTitleCase(value: string) {
  const lowerValue = value.toLowerCase()
  return lowerValue.charAt(0).toUpperCase() + lowerValue.slice(1)
}

function asRecord(value: JsonValue | undefined): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}
