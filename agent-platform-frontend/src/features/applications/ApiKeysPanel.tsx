import { useEffect, useState } from 'react'
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
import { listApiKeys, revokeApiKey } from './api'

type ApiKeysPanelProps = {
  application: Application | null
}

type ApiKeysState =
  | { apiKeys: ApiKey[]; error: null; status: 'ready' }
  | { apiKeys: null; error: string | null; status: 'idle' | 'loading' | 'error' }

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message
  }

  return 'API keys could not be loaded.'
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
  const [revokeId, setRevokeId] = useState<number | null>(null)
  const isActiveApplication = application?.status.toUpperCase() === 'ACTIVE'
  const [state, setState] = useState<ApiKeysState>({
    apiKeys: null,
    error: null,
    status: 'idle',
  })

  async function fetchKeys(applicationId: number) {
    try {
      const apiKeys = await listApiKeys(applicationId)
      return { apiKeys, error: null, status: 'ready' } satisfies ApiKeysState
    } catch (error) {
      return { apiKeys: null, error: getErrorMessage(error), status: 'error' } satisfies ApiKeysState
    }
  }

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
  }, [application, isActiveApplication])

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>API keys</CardTitle>
            <CardDescription>
              {application
                ? `Keys for ${application.name}. Plaintext secrets are only shown at creation.`
                : 'Select an application to inspect API keys.'}
            </CardDescription>
          </div>
          {application ? (
            <Button
              disabled={!isActiveApplication}
              onClick={() => loadKeys(application.applicationId)}
              size="sm"
              variant="secondary"
            >
              <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
              Refresh
            </Button>
          ) : null}
        </div>
      </CardHeader>
      <CardContent>
        {!application ? (
          <Alert>
            <KeyRound className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
            <AlertTitle>No application selected</AlertTitle>
            <AlertDescription>
              Create or select an application. Keys are scoped to a single application.
            </AlertDescription>
          </Alert>
        ) : !isActiveApplication ? (
          <Alert variant="danger">
            <AlertTitle>Application disabled</AlertTitle>
            <AlertDescription>
              This application is visible for audit, but API key operations are disabled.
            </AlertDescription>
          </Alert>
        ) : state.status === 'loading' ? (
          <div className="space-y-3">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        ) : state.status === 'error' ? (
          <Alert variant="danger">
            <AlertTitle>API keys unavailable</AlertTitle>
            <AlertDescription>{state.error}</AlertDescription>
          </Alert>
        ) : state.status === 'ready' && state.apiKeys.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Prefix</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Last used</TableHead>
                <TableHead>Action</TableHead>
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
                        {revokeId === apiKey.apiKeyId ? 'Revoking' : 'Revoke'}
                      </Button>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
          <Alert>
            <AlertTitle>No API keys</AlertTitle>
            <AlertDescription>
              This application has no visible keys. Create a new application to receive an initial key.
            </AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}
