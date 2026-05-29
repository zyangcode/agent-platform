import { describe, expect, it } from 'vitest'
import { buildTeamRunSummary } from './team-run-utils'
import type { ChatStreamEvent } from './types'

describe('buildTeamRunSummary', () => {
  it('builds plan, task statuses, review, and mermaid text from team events', () => {
    const events: ChatStreamEvent[] = [
      {
        type: 'team_plan',
        payload: {
          goal: 'Plan a calm team activity',
          tasks: [
            {
              dependsOn: [],
              description: 'Check local weather',
              id: 'task-1',
              name: 'Weather',
              suggestedTool: 'weather',
              taskType: 'TOOL_TASK',
            },
            {
              dependsOn: ['task-1'],
              description: 'Draft the plan',
              id: 'task-2',
              name: 'Draft',
              suggestedTool: null,
              taskType: 'MODEL_TASK',
            },
          ],
        },
        role: 'PLANNER',
        step: 2,
      },
      {
        content: 'Start task: Weather',
        taskId: 'task-1',
        type: 'team_task_start',
      },
      {
        status: 'SUCCESS',
        taskId: 'task-1',
        toolName: 'weather',
        type: 'team_tool_result',
      },
      {
        content: 'Weather is mild',
        status: 'SUCCESS',
        taskId: 'task-1',
        type: 'team_task_result',
      },
      {
        content: 'Review passed',
        payload: {
          answerDraft: 'Use an indoor board game venue.',
          review: {
            issues: [],
            passed: true,
            retryTasks: [],
            summary: 'Ready',
          },
        },
        status: 'SUCCESS',
        type: 'team_review',
      },
    ]

    const summary = buildTeamRunSummary(events)

    expect(summary.goal).toBe('Plan a calm team activity')
    expect(summary.tasks).toHaveLength(2)
    expect(summary.tasks[0]).toMatchObject({
      id: 'task-1',
      name: 'Weather',
      status: 'SUCCESS',
      toolName: 'weather',
    })
    expect(summary.tasks[1]).toMatchObject({
      dependsOn: ['task-1'],
      status: 'PENDING',
    })
    expect(summary.review).toMatchObject({
      answerDraft: 'Use an indoor board game venue.',
      passed: true,
      status: 'SUCCESS',
      summary: 'Ready',
    })
    expect(summary.mermaid).toContain('task_1["Weather"]')
    expect(summary.mermaid).toContain('Planner -. plan .-> task_1')
    expect(summary.mermaid).toContain('task_1 --> task_2')
    expect(summary.mermaid).toContain('task_2 --> Reviewer')
  })

  it('returns empty summary when no team events exist', () => {
    expect(buildTeamRunSummary([{ type: 'message', content: 'ok' }])).toEqual({
      events: [],
      goal: null,
      mermaid: null,
      review: null,
      tasks: [],
    })
  })
})
