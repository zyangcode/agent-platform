import { buildTeamMermaid } from './team-mermaid'
import type { TeamPlan, TeamReview, TeamReviewRun, TeamRunSummary, TeamTask, TeamTaskRun, TeamTaskStatus } from './team-types'
import type { ChatStreamEvent } from './types'

const TEAM_EVENT_TYPES = new Set([
  'team_final',
  'team_plan',
  'team_retry',
  'team_review',
  'team_start',
  'team_task_result',
  'team_task_start',
  'team_tool_call',
  'team_tool_result',
])

export function buildTeamRunSummary(events: ChatStreamEvent[]): TeamRunSummary {
  const teamEvents = events.filter((event) => TEAM_EVENT_TYPES.has(event.type))
  const plan = findPlan(teamEvents)
  const tasks = new Map<string, TeamTaskRun>()

  plan?.tasks.forEach((task) => {
    tasks.set(task.id, {
      ...task,
      dependsOn: task.dependsOn ?? [],
      status: 'PENDING',
    })
  })

  teamEvents.forEach((event) => {
    if (!event.taskId) {
      return
    }

    const task = ensureTask(tasks, event.taskId)
    if (event.type === 'team_task_start') {
      task.status = 'RUNNING'
    }
    if (event.type === 'team_tool_call' || event.type === 'team_tool_result') {
      task.toolName = event.toolName ?? task.toolName
    }
    if (event.type === 'team_task_result') {
      task.status = normalizeStatus(event.status)
      task.result = event.content ?? task.result
    }
  })

  const taskList = Array.from(tasks.values())

  return {
    events: teamEvents,
    goal: plan?.goal ?? null,
    mermaid: buildTeamMermaid(taskList),
    review: findReview(teamEvents),
    tasks: taskList,
  }
}

function ensureTask(tasks: Map<string, TeamTaskRun>, taskId: string) {
  const existing = tasks.get(taskId)
  if (existing) {
    return existing
  }

  const task: TeamTaskRun = {
    dependsOn: [],
    description: '',
    id: taskId,
    name: taskId,
    status: 'PENDING',
    taskType: 'MODEL_TASK',
  }
  tasks.set(taskId, task)
  return task
}

function findPlan(events: ChatStreamEvent[]) {
  for (const event of events) {
    if (event.type !== 'team_plan') {
      continue
    }
    const plan = asRecord(event.payload)
    const goal = typeof plan?.goal === 'string' ? plan.goal : null
    const tasks = Array.isArray(plan?.tasks) ? plan.tasks.map(toTeamTask).filter((task): task is TeamTask => task !== null) : []

    if (goal || tasks.length > 0) {
      return {
        goal: goal ?? '',
        tasks,
      } satisfies TeamPlan
    }
  }

  return null
}

function findReview(events: ChatStreamEvent[]): TeamReviewRun | null {
  const event = events.findLast((candidate) => candidate.type === 'team_review')
  if (!event) {
    return null
  }

  const payload = asRecord(event.payload)
  const review = asRecord(payload?.review)
  if (!review) {
    return {
      answerDraft: typeof payload?.answerDraft === 'string' ? payload.answerDraft : null,
      issues: [],
      passed: event.status === 'SUCCESS',
      retryTasks: [],
      status: event.status ?? null,
      summary: event.content ?? '',
    }
  }

  return {
    answerDraft: typeof payload?.answerDraft === 'string' ? payload.answerDraft : null,
    issues: Array.isArray(review.issues) ? review.issues.map(toReviewIssue).filter((issue): issue is TeamReview['issues'][number] => issue !== null) : [],
    passed: Boolean(review.passed),
    retryTasks: Array.isArray(review.retryTasks) ? review.retryTasks.filter((item): item is string => typeof item === 'string') : [],
    status: event.status ?? null,
    summary: typeof review.summary === 'string' ? review.summary : event.content ?? '',
  }
}

function toReviewIssue(value: unknown): TeamReview['issues'][number] | null {
  const item = asRecord(value)
  if (!item || typeof item.message !== 'string') {
    return null
  }

  return {
    level: item.level === 'error' || item.level === 'info' || item.level === 'warning' ? item.level : 'warning',
    message: item.message,
    taskId: typeof item.taskId === 'string' ? item.taskId : null,
  }
}

function toTeamTask(value: unknown): TeamTask | null {
  const item = asRecord(value)
  if (!item || typeof item.id !== 'string') {
    return null
  }

  return {
    dependsOn: Array.isArray(item.dependsOn) ? item.dependsOn.filter((dependency): dependency is string => typeof dependency === 'string') : [],
    description: typeof item.description === 'string' ? item.description : '',
    id: item.id,
    name: typeof item.name === 'string' ? item.name : item.id,
    suggestedTool: typeof item.suggestedTool === 'string' ? item.suggestedTool : null,
    taskType: item.taskType === 'TOOL_TASK' ? 'TOOL_TASK' : 'MODEL_TASK',
  }
}

function normalizeStatus(status?: string | null): TeamTaskStatus {
  const upperStatus = status?.toUpperCase()
  if (
    upperStatus === 'FAILED' ||
    upperStatus === 'RUNNING' ||
    upperStatus === 'SKIPPED' ||
    upperStatus === 'SUCCESS'
  ) {
    return upperStatus
  }

  return 'PENDING'
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}
