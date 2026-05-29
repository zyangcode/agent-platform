export type TeamTask = {
  id: string
  name: string
  description: string
  taskType: 'MODEL_TASK' | 'TOOL_TASK'
  suggestedTool?: string | null
  dependsOn: string[]
}

export type TeamPlan = {
  goal: string
  tasks: TeamTask[]
}

export type TeamReview = {
  passed: boolean
  issues: Array<{
    taskId?: string | null
    level: 'error' | 'info' | 'warning'
    message: string
  }>
  retryTasks: string[]
  summary: string
}

export type TeamTaskStatus = 'FAILED' | 'PENDING' | 'RUNNING' | 'SKIPPED' | 'SUCCESS'

export type TeamTaskRun = TeamTask & {
  result?: string | null
  status: TeamTaskStatus
  toolName?: string | null
}

export type TeamReviewRun = TeamReview & {
  answerDraft?: string | null
  status?: string | null
}

export type TeamRunSummary = {
  events: import('./types').ChatStreamEvent[]
  goal: string | null
  mermaid: string | null
  review: TeamReviewRun | null
  tasks: TeamTaskRun[]
}
