import type { TraceSpan } from '@/lib/api/types'
import type { Locale } from '@/lib/i18n/i18n-context-value'

type SpanFact = {
  label: string
  value: string
}

const spanTitleLabels: Record<string, Record<Locale, string>> = {
  'agent_runtime.run': { en: 'Single agent run', zh: '单 Agent 运行' },
  'api.messages.compose': { en: 'API messages', zh: '消息组装' },
  'compact.micro': { en: 'Micro compact', zh: '微压缩' },
  'context.budget.snapshot': { en: 'Context budget', zh: '上下文预算' },
  'context.build': { en: 'Context build', zh: '上下文构建' },
  'experience.resolve': { en: 'Experience recall', zh: '经验召回' },
  'final.answer.build': { en: 'Final answer cleanup', zh: '最终答案整理' },
  'memory.embedding': { en: 'Memory embedding', zh: '记忆向量生成' },
  'memory.vector.search': { en: 'Memory vector search', zh: '记忆向量检索' },
  'model.invoke': { en: 'Model invocation', zh: '模型调用' },
  'rag.embedding': { en: 'RAG embedding', zh: 'RAG 向量生成' },
  'rag.ingest': { en: 'RAG ingest', zh: 'RAG 入库' },
  'rag.vector.search': { en: 'RAG vector search', zh: 'RAG 向量检索' },
  'tool.execute': { en: 'Tool execution', zh: '工具执行' },
  'tool.plan': { en: 'Tool plan', zh: '工具编排' },
  'tool.validate': { en: 'Tool validation', zh: '工具校验' },
  'team.plan': { en: 'Team plan', zh: 'Team 计划' },
  'team.review': { en: 'Team review', zh: 'Team 审查' },
  'team.run': { en: 'Team run', zh: 'Team 运行' },
  'team.task.execute': { en: 'Team task execution', zh: 'Team 任务执行' },
}

const factLabels: Record<string, Record<Locale, string>> = {
  apiMessages: { en: 'API messages', zh: 'API 消息数' },
  apiMessagesTokens: { en: 'Message tokens', zh: '消息 Token' },
  batchSize: { en: 'Batch size', zh: '批大小' },
  changed: { en: 'Changed', zh: '已清理' },
  chunkCount: { en: 'Chunks', zh: '分块数' },
  compactedChars: { en: 'Compacted chars', zh: '压缩后字符' },
  contentChars: { en: 'Content chars', zh: '内容字符' },
  conversationMessages: { en: 'Conversation messages', zh: '对话消息数' },
  dimension: { en: 'Vector dimension', zh: '向量维度' },
  documentId: { en: 'Document ID', zh: '文档 ID' },
  estimatedTokens: { en: 'Estimated tokens', zh: '估算 Token' },
  experienceTokens: { en: 'Experience tokens', zh: '经验 Token' },
  finalChars: { en: 'Final chars', zh: '最终字符' },
  groupCount: { en: 'Groups', zh: '分组数' },
  invalidCount: { en: 'Invalid calls', zh: '无效调用' },
  maxContextTokens: { en: 'Context limit', zh: '上下文上限' },
  memoryTokens: { en: 'Memory tokens', zh: '记忆 Token' },
  model: { en: 'Embedding model', zh: '向量模型' },
  modelConfigId: { en: 'Model config', zh: '模型配置' },
  originalChars: { en: 'Original chars', zh: '原始字符' },
  parallelGroupCount: { en: 'Parallel groups', zh: '并行分组' },
  profileId: { en: 'Profile ID', zh: 'Profile ID' },
  profileScoped: { en: 'Profile scoped', zh: 'Profile 隔离' },
  queryChars: { en: 'Query chars', zh: '查询字符' },
  ragTokens: { en: 'RAG tokens', zh: 'RAG Token' },
  readOnly: { en: 'Read only', zh: '只读' },
  remainingTokens: { en: 'Remaining tokens', zh: '剩余 Token' },
  resolvedCount: { en: 'Resolved', zh: '召回数量' },
  resultCount: { en: 'Results', zh: '结果数' },
  retryIndex: { en: 'Retry index', zh: '重试次数' },
  riskLevel: { en: 'Risk', zh: '风险' },
  role: { en: 'Role', zh: '角色' },
  sourceType: { en: 'Source type', zh: '来源类型' },
  step: { en: 'Step', zh: '轮次' },
  stream: { en: 'Streaming', zh: '流式' },
  taskId: { en: 'Task ID', zh: '任务 ID' },
  taskType: { en: 'Task type', zh: '任务类型' },
  toolCount: { en: 'Tool count', zh: '工具数' },
  toolName: { en: 'Tool', zh: '工具' },
  toolSpecCount: { en: 'Available tools', zh: '可用工具' },
  toolType: { en: 'Tool type', zh: '工具类型' },
  titleChars: { en: 'Title chars', zh: '标题字符' },
  topK: { en: 'Top K', zh: 'Top K' },
  truncated: { en: 'Truncated', zh: '已裁剪' },
  vectorIndexedCount: { en: 'Vector indexed', zh: '已索引向量' },
}

const factOrder = [
  'role',
  'taskId',
  'taskType',
  'step',
  'modelConfigId',
  'stream',
  'toolSpecCount',
  'toolType',
  'toolName',
  'toolCount',
  'batchSize',
  'invalidCount',
  'riskLevel',
  'readOnly',
  'sourceType',
  'titleChars',
  'contentChars',
  'documentId',
  'chunkCount',
  'vectorIndexedCount',
  'queryChars',
  'model',
  'dimension',
  'topK',
  'resultCount',
  'profileScoped',
  'retryIndex',
  'profileId',
  'apiMessages',
  'conversationMessages',
  'estimatedTokens',
  'resolvedCount',
  'groupCount',
  'parallelGroupCount',
  'rawChars',
  'finalChars',
  'changed',
  'role',
  'originalChars',
  'compactedChars',
  'maxContextTokens',
  'apiMessagesTokens',
  'memoryTokens',
  'experienceTokens',
  'ragTokens',
  'remainingTokens',
  'truncated',
] as const

export function getTraceSpanTitle(span: TraceSpan, locale: Locale) {
  return spanTitleLabels[span.spanName]?.[locale] ?? span.spanName
}

export function getTraceSpanFacts(span: TraceSpan, locale: Locale): SpanFact[] {
  const attributes = asRecord(span.attributes)
  if (!attributes) {
    return []
  }

  const flattenedAttributes = {
    ...attributes,
    ...asRecord(attributes.contextBudgetSnapshot),
  }
  const seen = new Set<string>()

  return factOrder.flatMap((key) => {
    if (seen.has(key)) {
      return []
    }
    seen.add(key)
    const value = formatFactValue(key, flattenedAttributes[key], locale)
    if (!value || !factLabels[key]) {
      return []
    }

    return [{
      label: factLabels[key][locale],
      value,
    }]
  })
}

function formatFactValue(key: string, value: unknown, locale: Locale) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  if (key === 'role' && typeof value === 'string') {
    return toTitleCase(value)
  }
  if (typeof value === 'boolean') {
    return formatBoolean(value, locale)
  }
  if (typeof value === 'string' || typeof value === 'number') {
    return String(value)
  }

  return null
}

function formatBoolean(value: boolean, locale: Locale) {
  if (locale === 'zh') {
    return value ? '是' : '否'
  }
  return value ? 'Yes' : 'No'
}

function toTitleCase(value: string) {
  const lowerValue = value.toLowerCase()
  return lowerValue.charAt(0).toUpperCase() + lowerValue.slice(1)
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}
