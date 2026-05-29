import type { TeamTaskRun } from './team-types'

export function buildTeamMermaid(tasks: TeamTaskRun[]) {
  if (tasks.length === 0) {
    return null
  }

  const lines = ['flowchart TD']
  tasks.forEach((task) => {
    lines.push(`  ${nodeId(task.id)}["${escapeLabel(task.name || task.id)}"]`)
  })
  tasks.forEach((task) => {
    lines.push(`  Planner -. plan .-> ${nodeId(task.id)}`)
  })
  tasks.forEach((task) => {
    task.dependsOn.forEach((dependencyId) => {
      lines.push(`  ${nodeId(dependencyId)} --> ${nodeId(task.id)}`)
    })
  })
  const terminalTasks = tasks.filter((task) => !tasks.some((candidate) => candidate.dependsOn.includes(task.id)))
  terminalTasks.forEach((task) => {
    lines.push(`  ${nodeId(task.id)} --> Reviewer`)
  })
  lines.push('  Reviewer --> Final')

  return lines.join('\n')
}

function escapeLabel(value: string) {
  return value.replaceAll('"', '\\"')
}

function nodeId(value: string) {
  return value.replace(/[^A-Za-z0-9_]/g, '_')
}
