import { useCallback, useEffect, useMemo, useState } from 'react'
import { KeyRound, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/lib/api/errors'
import { loadLastSelectedApplicationId, saveLastSelectedApplicationId } from '@/lib/application-selection-storage'
import type { Application, CreatedApiKey, PageResult } from '@/lib/api/types'
import { formatDateTime } from '@/lib/format/date'
import { useI18n } from '@/lib/i18n/use-i18n'
import { ApiKeyRevealDialog } from './ApiKeyRevealDialog'
import { ApiKeysPanel } from './ApiKeysPanel'
import { resolveSelectedApplicationId } from './application-selection-utils'
import { enableApplication, listApplications } from './api'
import { CreateApplicationDialog } from './CreateApplicationDialog'
import { DisableApplicationDialog } from './DisableApplicationDialog'
import { EditApplicationDialog } from './EditApplicationDialog'

type ApplicationsState =
  | { applications: PageResult<Application>; error: null; status: 'ready' }
  | { applications: null; error: string | null; status: 'loading' | 'error' }

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message
  }

  return fallback
}

function getApplicationStatusVariant(status: string) {
  const normalized = status.toUpperCase()

  if (normalized === 'ACTIVE') {
    return 'success'
  }
  if (normalized === 'DISABLED' || normalized === 'DELETED') {
    return 'danger'
  }

  return 'muted'
}

export function ApplicationsPage() {
  const { t } = useI18n()
  const [revealedKey, setRevealedKey] = useState<CreatedApiKey | null>(null)
  const [selectedApplicationId, setSelectedApplicationId] = useState<number | null>(null)
  const [state, setState] = useState<ApplicationsState>({
    applications: null,
    error: null,
    status: 'loading',
  })

  const selectedApplication = useMemo(() => {
    return (
      state.applications?.records.find(
        (application) => application.applicationId === selectedApplicationId,
      ) ?? state.applications?.records[0] ?? null
    )
  }, [selectedApplicationId, state.applications])

  const fetchApplications = useCallback(async () => {
    try {
      const applications = await listApplications()
      return { applications, error: null, status: 'ready' } satisfies ApplicationsState
    } catch (error) {
      return { applications: null, error: getErrorMessage(error, t('application.unavailable')), status: 'error' } satisfies ApplicationsState
    }
  }, [t])

  function syncSelectedApplication(applications: PageResult<Application>) {
    const nextApplicationId = resolveSelectedApplicationId(
      applications,
      selectedApplicationId,
      loadLastSelectedApplicationId(),
    )
    saveLastSelectedApplicationId(nextApplicationId)
    setSelectedApplicationId(nextApplicationId)
  }

  function handleApplicationSelect(applicationId: number) {
    saveLastSelectedApplicationId(applicationId)
    setSelectedApplicationId(applicationId)
  }

  async function loadApplications() {
    setState({ applications: null, error: null, status: 'loading' })
    const nextState = await fetchApplications()
    setState(nextState)

    if (nextState.status === 'ready') {
      syncSelectedApplication(nextState.applications)
    }
  }

  useEffect(() => {
    let isMounted = true

    async function initializeApplications() {
      const nextState = await fetchApplications()

      if (isMounted) {
        setState(nextState)

        if (nextState.status === 'ready') {
          const nextApplicationId = resolveSelectedApplicationId(
            nextState.applications,
            null,
            loadLastSelectedApplicationId(),
          )
          saveLastSelectedApplicationId(nextApplicationId)
          setSelectedApplicationId(nextApplicationId)
        }
      }
    }

    void initializeApplications()

    return () => {
      isMounted = false
    }
  }, [fetchApplications])

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">{t('application.title')}</h2>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-400">{t('application.intro')}</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button onClick={loadApplications} variant="secondary">
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
            {t('common.refresh')}
          </Button>
          <CreateApplicationDialog
            onCreated={(result) => {
              setRevealedKey(result.apiKey)
              handleApplicationSelect(result.applicationId)
              void loadApplications()
            }}
          />
        </div>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTitle>{t('application.unavailable')}</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <Card>
          <CardHeader>
            <CardTitle>{t('application.list')}</CardTitle>
            <CardDescription>{t('application.listDescription')}</CardDescription>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : state.status === 'ready' && state.applications.records.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('application.name')}</TableHead>
                    <TableHead>{t('application.status')}</TableHead>
                    <TableHead>{t('application.created')}</TableHead>
                    <TableHead>{t('application.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.applications.records.map((application) => {
                    const isSelected = selectedApplication?.applicationId === application.applicationId

                    return (
                      <TableRow
                        data-state={isSelected ? 'selected' : undefined}
                        key={application.applicationId}
                        onClick={() => handleApplicationSelect(application.applicationId)}
                      >
                        <TableCell>
                          <div className="flex items-center gap-3">
                            <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-200/20 bg-cyan-300/10">
                              <KeyRound className="h-4 w-4 text-cyan-100" strokeWidth={1.75} />
                            </div>
                            <div>
                              <p className="font-medium text-white">{application.name}</p>
                              <p className="max-w-[320px] truncate text-xs text-zinc-500">
                                {application.description || t('application.noDescription')}
                              </p>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={getApplicationStatusVariant(application.status)}>
                            {application.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-zinc-400">
                          {formatDateTime(application.createdAt)}
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-2" onClick={(event) => event.stopPropagation()}>
                            <EditApplicationDialog
                              application={application}
                              onUpdated={() => {
                                void loadApplications()
                              }}
                            />
                            <EnableApplicationButton
                              application={application}
                              onEnabled={() => { void loadApplications() }}
                            />
                            <DisableApplicationDialog
                              application={application}
                              onDisabled={() => {
                                void loadApplications()
                              }}
                            />
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            ) : (
              <Alert>
                <AlertTitle>{t('application.noApplications')}</AlertTitle>
                <AlertDescription>{t('application.createFirst')}</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>

        <ApiKeysPanel application={selectedApplication} />
      </div>

      <ApiKeyRevealDialog
        apiKey={revealedKey}
        onOpenChange={(open) => {
          if (!open) {
            setRevealedKey(null)
          }
        }}
        open={revealedKey !== null}
      />
    </section>
  )
}

function EnableApplicationButton({ application, onEnabled }: { application: Application; onEnabled: () => void }) {
  if (application.status !== 'DISABLED') return null
  return (
    <Button
      onClick={async () => {
        await enableApplication(application.applicationId)
        onEnabled()
      }}
      size="sm"
      variant="secondary"
    >
      Enable
    </Button>
  )
}
