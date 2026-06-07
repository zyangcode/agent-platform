import { useCallback, useEffect, useState } from 'react'
import { Ban, KeyRound, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/lib/api/errors'
import type { ApiKey, Application } from '@/lib/api/types'
import { formatDateTime } from '@/lib/format/date'
import { useI18n } from '@/lib/i18n/use-i18n'
import { listApiKeys, regenerateApiKey, revokeApiKey } from './api'

type ApiKeysPanelProps = {
  application: Application | null
}

type ApiKeysState =
  | { apiKeys: ApiKey[]; error: null; status: 'ready' }
  | { apiKeys: null; error: string | null; status: 'idle' | 'loading' | 'error' }

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message
  }

  return fallback
}

function getKeyStatusVariant(status: string) {
  const normalized = status.toUpperCase()

  if (normalized === 'ACTIVE') {
    return 'success'
  }
  if (normalized === 'REVOKED' || normalized === 'DISABLED') {
    return 'danger'
  }

  return 'muted'
}

export function ApiKeysPanel({ application }: ApiKeysPanelProps) {
  const { t } = useI18n()
  const [revokeId, setRevokeId] = useState<number | null>(null)
  const isActiveApplication = application?.status.toUpperCase() === 'ACTIVE'
  const [state, setState] = useState<ApiKeysState>({
    apiKeys: null,
    error: null,
    status: 'idle',
  })

  const fetchKeys = useCallback(async (applicationId: number) => {
    try {
      const apiKeys = await listApiKeys(applicationId)
      return { apiKeys, error: null, status: 'ready' } satisfies ApiKeysState
    } catch (error) {
      return { apiKeys: null, error: getErrorMessage(error, t('application.apiKeysUnavailable')), status: 'error' } satisfies ApiKeysState
    }
  }, [t])

  async function loadKeys(applicationId: number) {
    setState({ apiKeys: null, error: null, status: 'loading' })
    setState(await fetchKeys(applicationId))
  }

  async function handleRevoke(apiKeyId: number) {
    if (!application || !isActiveApplication) {
      return
    }

    setRevokeId(apiKeyId)

    try {
      await revokeApiKey(application.applicationId, apiKeyId)
      await loadKeys(application.applicationId)
    } finally {
      setRevokeId(null)
    }
  }

  async function handleRegenerate() {
    if (!application || !isActiveApplication) return
    try {
      const newKey = await regenerateApiKey(application.applicationId)
      alert(`New API Key: ${newKey.key}\nCopy it now - it won't be shown again.`)
      await loadKeys(application.applicationId)
    } catch {
      alert('Failed to regenerate API key.')
    }
  }

  useEffect(() => {
    let isMounted = true

    if (!application || !isActiveApplication) {
      return
    }

    async function initializeKeys(applicationId: number) {
      const nextState = await fetchKeys(applicationId)

      if (isMounted) {
        setState(nextState)
      }
    }

    void initializeKeys(application.applicationId)

    return () => {
      isMounted = false
    }
  }, [application, fetchKeys, isActiveApplication])

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>{t('application.apiKeys')}</CardTitle>
            <CardDescription>
              {application
                ? t('application.apiKeysDescription', { name: application.name })
                : t('application.selectApiKeys')}
            </CardDescription>
          </div>
          <div className="flex gap-2">
            {application ? (
              <>
                <Button
                  disabled={!isActiveApplication}
                  onClick={handleRegenerate}
                  size="sm"
                  variant="secondary"
                >
                  <KeyRound className="h-4 w-4" strokeWidth={1.75} />
                  Regenerate
                </Button>
                <Button
                  disabled={!isActiveApplication}
                  onClick={() => loadKeys(application.applicationId)}
                  size="sm"
                  variant="secondary"
                >
                  <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
                  {t('common.refresh')}
                </Button>
              </>
            ) : null}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {!application ? (
          <Alert>
            <KeyRound className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
            <AlertTitle>{t('application.noApplicationSelected')}</AlertTitle>
            <AlertDescription>{t('application.noApplicationSelectedDescription')}</AlertDescription>
          </Alert>
        ) : !isActiveApplication ? (
          <Alert variant="danger">
            <AlertTitle>{t('application.applicationDisabled')}</AlertTitle>
            <AlertDescription>{t('application.applicationDisabledDescription')}</AlertDescription>
          </Alert>
        ) : state.status === 'loading' ? (
          <div className="space-y-3">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        ) : state.status === 'error' ? (
          <Alert variant="danger">
            <AlertTitle>{t('application.apiKeysUnavailable')}</AlertTitle>
            <AlertDescription>{state.error}</AlertDescription>
          </Alert>
        ) : state.status === 'ready' && state.apiKeys.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('application.prefix')}</TableHead>
                <TableHead>{t('application.status')}</TableHead>
                <TableHead>{t('application.created')}</TableHead>
                <TableHead>{t('application.lastUsed')}</TableHead>
                <TableHead>{t('application.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {state.apiKeys.map((apiKey) => {
                const isActive = apiKey.status.toUpperCase() === 'ACTIVE'

                return (
                  <TableRow key={apiKey.apiKeyId}>
                    <TableCell className="font-mono text-xs text-zinc-300">{apiKey.keyPrefix}</TableCell>
                    <TableCell>
                      <Badge variant={getKeyStatusVariant(apiKey.status)}>{apiKey.status}</Badge>
                    </TableCell>
                    <TableCell className="text-zinc-400">{formatDateTime(apiKey.createdAt)}</TableCell>
                    <TableCell className="text-zinc-400">{formatDateTime(apiKey.lastUsedAt)}</TableCell>
                    <TableCell>
                      <Button
                        disabled={!isActive || revokeId === apiKey.apiKeyId}
                        onClick={() => handleRevoke(apiKey.apiKeyId)}
                        size="sm"
                        variant="danger"
                      >
                        <Ban className="h-4 w-4" strokeWidth={1.75} />
                        {revokeId === apiKey.apiKeyId ? t('application.revoking') : t('application.revoke')}
                      </Button>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
          <Alert>
            <AlertTitle>{t('application.noApiKeys')}</AlertTitle>
            <AlertDescription>{t('application.noApiKeysDescription')}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}
