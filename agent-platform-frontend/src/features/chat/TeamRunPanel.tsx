import { CheckCircle2, CircleDashed, GitBranch, RotateCcw, ShieldCheck, Wrench } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useI18n } from '@/lib/i18n/use-i18n'
import type { TeamRunSummary, TeamTaskRun, TeamTaskStatus } from './team-types'

type TeamRunPanelProps = {
  summary: TeamRunSummary
}

export function TeamRunPanel({ summary }: TeamRunPanelProps) {
  const { t } = useI18n()

  if (summary.events.length === 0) {
    return null
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle>{t('runtime.teamRun')}</CardTitle>
            <CardDescription>{t('runtime.teamRunDescription')}</CardDescription>
          </div>
          <Badge variant="default">{t('runtime.teamEventCount', { count: summary.tasks.length || summary.events.length })}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {summary.goal ? (
          <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
            <p className="text-xs uppercase tracking-[0.16em] text-zinc-500">{t('runtime.teamGoal')}</p>
            <p className="mt-2 line-clamp-3 text-sm leading-6 text-zinc-200">{summary.goal}</p>
          </div>
        ) : null}

        {summary.tasks.length > 0 ? (
          <div className="space-y-2">
            {summary.tasks.map((task) => (
              <TaskRow key={task.id} task={task} />
            ))}
          </div>
        ) : (
          <Alert>
            <CircleDashed className="mb-3 h-5 w-5 text-zinc-400" strokeWidth={1.75} />
            <AlertTitle>{t('runtime.teamWaitingForPlan')}</AlertTitle>
            <AlertDescription>{t('runtime.teamWaitingForPlanDescription')}</AlertDescription>
          </Alert>
        )}

        {summary.review ? (
          <div className="rounded-xl border border-white/10 bg-white/[0.04] p-3">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-sm font-medium text-white">
                <ShieldCheck className="h-4 w-4 text-emerald-100" strokeWidth={1.75} />
                {t('runtime.teamReviewer')}
              </div>
              <Badge variant={summary.review.passed ? 'success' : 'warning'}>
                {summary.review.passed ? t('runtime.teamReviewPassed') : t('runtime.teamReviewNeedsRetry')}
              </Badge>
            </div>
            <p className="mt-2 text-xs leading-5 text-zinc-400">
              {summary.review.summary || t('runtime.teamReviewCompleted')}
            </p>
            {summary.review.answerDraft ? (
              <p className="mt-3 line-clamp-4 rounded-lg border border-white/10 bg-zinc-950/50 p-2 text-xs leading-5 text-zinc-300">
                {summary.review.answerDraft}
              </p>
            ) : null}
          </div>
        ) : null}

        {summary.mermaid ? (
          <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
            <div className="mb-2 flex items-center gap-2 text-xs uppercase tracking-[0.16em] text-zinc-500">
              <GitBranch className="h-3.5 w-3.5" strokeWidth={1.75} />
              Mermaid
            </div>
            <pre className="max-h-56 overflow-auto whitespace-pre-wrap break-words font-mono text-[11px] leading-5 text-zinc-300">
              {summary.mermaid}
            </pre>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

function TaskRow({ task }: { task: TeamTaskRun }) {
  const { t } = useI18n()

  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.04] p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            {task.status === 'SUCCESS' ? (
              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-100" strokeWidth={1.75} />
            ) : task.status === 'RUNNING' ? (
              <RotateCcw className="h-4 w-4 shrink-0 text-amber-100" strokeWidth={1.75} />
            ) : (
              <CircleDashed className="h-4 w-4 shrink-0 text-zinc-400" strokeWidth={1.75} />
            )}
            <p className="truncate text-sm font-medium text-white">{task.name}</p>
          </div>
          <p className="mt-1 line-clamp-2 text-xs leading-5 text-zinc-500">{task.description || task.id}</p>
        </div>
        <Badge className="shrink-0" variant={statusVariant(task.status)}>
          {statusLabel(task.status, t)}
        </Badge>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Badge variant="muted">{task.taskType}</Badge>
        {task.toolName || task.suggestedTool ? (
          <Badge className="gap-1" variant="default">
            <Wrench className="h-3 w-3" strokeWidth={1.75} />
            {task.toolName || task.suggestedTool}
          </Badge>
        ) : null}
        {task.dependsOn.map((dependency) => (
          <Badge className="font-mono" key={dependency} variant="muted">
            {t('runtime.teamAfterDependency', { dependency })}
          </Badge>
        ))}
      </div>
      {task.result ? (
        <p className="mt-3 line-clamp-3 rounded-lg border border-white/10 bg-zinc-950/40 p-2 text-xs leading-5 text-zinc-400">
          {task.result}
        </p>
      ) : null}
    </div>
  )
}

function statusLabel(status: TeamTaskStatus, t: (key: string) => string) {
  if (status === 'SUCCESS') {
    return t('common.success')
  }
  if (status === 'FAILED') {
    return t('common.failed')
  }
  if (status === 'RUNNING') {
    return t('common.running')
  }
  if (status === 'SKIPPED') {
    return t('runtime.teamStatusSkipped')
  }

  return t('runtime.teamStatusPending')
}

function statusVariant(status: TeamTaskStatus) {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  if (status === 'RUNNING') {
    return 'warning'
  }

  return 'muted'
}
