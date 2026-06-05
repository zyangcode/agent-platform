import { describe, expect, it } from 'vitest'
import { getTraceSpanFacts, getTraceSpanTitle } from './trace-labels'
import type { TraceSpan } from '@/lib/api/types'

describe('trace-labels', () => {
  it('maps team span names to localized titles', () => {
    const span = traceSpan({
      attributes: { taskId: 'task-1', taskType: 'TOOL_TASK' },
      spanName: 'team.task.execute',
      spanType: 'TEAM',
    })

    expect(getTraceSpanTitle(span, 'zh')).toBe('Team 任务执行')
    expect(getTraceSpanTitle(span, 'en')).toBe('Team task execution')
  })

  it('maps single-agent span names to readable localized titles', () => {
    expect(getTraceSpanTitle(traceSpan({ spanName: 'agent_runtime.run', spanType: 'AGENT' }), 'zh')).toBe('单 Agent 运行')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'context.build', spanType: 'CONTEXT' }), 'zh')).toBe('上下文构建')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'model.invoke', spanType: 'MODEL' }), 'zh')).toBe('模型调用')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'tool.execute', spanType: 'TOOL' }), 'en')).toBe('Tool execution')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'context.budget.snapshot', spanType: 'CONTEXT' }), 'en')).toBe('Context budget')
  })

  it('extracts team span facts from attributes', () => {
    const span = traceSpan({
      attributes: {
        profileId: 11,
        retryIndex: 1,
        role: 'EXECUTOR',
        taskId: 'task-1',
        taskType: 'TOOL_TASK',
        toolName: 'weather',
      },
      spanName: 'team.task.execute',
      spanType: 'TEAM',
    })

    expect(getTraceSpanFacts(span, 'zh')).toEqual([
      { label: '角色', value: 'Executor' },
      { label: '任务 ID', value: 'task-1' },
      { label: '任务类型', value: 'TOOL_TASK' },
      { label: '工具', value: 'weather' },
      { label: '重试次数', value: '1' },
      { label: 'Profile ID', value: '11' },
    ])
  })

  it('extracts single-agent model and tool facts from attributes', () => {
    const span = traceSpan({
      attributes: {
        modelConfigId: 30001,
        step: 2,
        stream: true,
        toolSpecCount: 1,
        toolName: 'weather',
        toolType: 'SKILL',
        riskLevel: 'LOW',
        readOnly: true,
      },
      spanName: 'tool.execute',
      spanType: 'TOOL',
    })

    expect(getTraceSpanFacts(span, 'zh')).toEqual([
      { label: '轮次', value: '2' },
      { label: '模型配置', value: '30001' },
      { label: '流式', value: '是' },
      { label: '可用工具', value: '1' },
      { label: '工具类型', value: 'SKILL' },
      { label: '工具', value: 'weather' },
      { label: '风险', value: 'LOW' },
      { label: '只读', value: '是' },
    ])
  })

  it('maps memory and rag span names and facts for observability drill-down', () => {
    expect(getTraceSpanTitle(traceSpan({ spanName: 'memory.embedding', spanType: 'MEMORY' }), 'en'))
      .toBe('Memory embedding')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'memory.vector.search', spanType: 'MEMORY' }), 'en'))
      .toBe('Memory vector search')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'rag.ingest', spanType: 'RAG' }), 'en'))
      .toBe('RAG ingest')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'rag.embedding', spanType: 'RAG' }), 'en'))
      .toBe('RAG embedding')
    expect(getTraceSpanTitle(traceSpan({ spanName: 'rag.vector.search', spanType: 'RAG' }), 'en'))
      .toBe('RAG vector search')

    const span = traceSpan({
      attributes: {
        chunkCount: 3,
        contentChars: 1840,
        dimension: 1536,
        documentId: 90001,
        model: 'text-embedding-3-small',
        profileScoped: true,
        queryChars: 24,
        resultCount: 2,
        sourceType: 'MANUAL',
        titleChars: 16,
        topK: 5,
        vectorIndexedCount: 3,
      },
      spanName: 'rag.ingest',
      spanType: 'RAG',
    })

    expect(getTraceSpanFacts(span, 'en')).toEqual([
      { label: 'Source type', value: 'MANUAL' },
      { label: 'Title chars', value: '16' },
      { label: 'Content chars', value: '1840' },
      { label: 'Document ID', value: '90001' },
      { label: 'Chunks', value: '3' },
      { label: 'Vector indexed', value: '3' },
      { label: 'Query chars', value: '24' },
      { label: 'Embedding model', value: 'text-embedding-3-small' },
      { label: 'Vector dimension', value: '1536' },
      { label: 'Top K', value: '5' },
      { label: 'Results', value: '2' },
      { label: 'Profile scoped', value: 'Yes' },
    ])
  })

  it('flattens context budget snapshot into readable facts', () => {
    const span = traceSpan({
      attributes: {
        contextBudgetSnapshot: {
          maxContextTokens: 4000,
          apiMessagesTokens: 980,
          memoryTokens: 120,
          experienceTokens: 80,
          ragTokens: 0,
          remainingTokens: 3020,
          truncated: false,
        },
      },
      spanName: 'context.budget.snapshot',
      spanType: 'CONTEXT',
    })

    expect(getTraceSpanFacts(span, 'zh')).toEqual([
      { label: '上下文上限', value: '4000' },
      { label: '消息 Token', value: '980' },
      { label: '记忆 Token', value: '120' },
      { label: '经验 Token', value: '80' },
      { label: 'RAG Token', value: '0' },
      { label: '剩余 Token', value: '3020' },
      { label: '已裁剪', value: '否' },
    ])
  })

  it('returns no facts for spans without structured attributes', () => {
    expect(getTraceSpanFacts(traceSpan({ attributes: 'raw text' }), 'zh')).toEqual([])
  })
})

function traceSpan(overrides: Partial<TraceSpan>): TraceSpan {
  return {
    component: 'core',
    id: 1,
    spanName: 'team.run',
    spanType: 'TEAM',
    status: 'SUCCESS',
    traceId: 'tr_1',
    ...overrides,
  }
}
