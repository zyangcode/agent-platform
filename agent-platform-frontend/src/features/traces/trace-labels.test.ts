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
