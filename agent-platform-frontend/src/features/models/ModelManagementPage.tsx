import { useEffect, useMemo, useState } from 'react'
import { Cpu, RefreshCw, ServerCog } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/lib/api/errors'
import { listModelConfigs, listModelProviders } from '@/lib/api/model-configs'
import type { ModelConfig, ModelProvider } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { CreateModelConfigDialog } from './CreateModelConfigDialog'
import { CreateModelProviderDialog } from './CreateModelProviderDialog'

type ModelState =
  | { configs: ModelConfig[]; error: null; providers: ModelProvider[]; status: 'ready' }
  | { configs: ModelConfig[]; error: string | null; providers: ModelProvider[]; status: 'error' | 'loading' }

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

async function fetchModels(fallback: string) {
  try {
    const [providers, configs] = await Promise.all([listModelProviders(), listModelConfigs()])
    return { configs, error: null, providers, status: 'ready' } satisfies ModelState
  } catch (error) {
    return { configs: [], error: getErrorMessage(error, fallback), providers: [], status: 'error' } satisfies ModelState
  }
}

function getStatusVariant(status: string) {
  return status.toUpperCase() === 'ACTIVE' ? 'success' : 'muted'
}

export function ModelManagementPage() {
  const { t } = useI18n()
  const [state, setState] = useState<ModelState>({
    configs: [],
    error: null,
    providers: [],
    status: 'loading',
  })

  const providerById = useMemo(() => {
    return new Map(state.providers.map((provider) => [provider.providerId, provider]))
  }, [state.providers])

  async function loadModels() {
    setState((current) => ({ ...current, error: null, status: 'loading' }))
    setState(await fetchModels(t('model.settingsLoadFailed')))
  }

  useEffect(() => {
    let isMounted = true

    async function initialize() {
      const nextState = await fetchModels(t('model.settingsLoadFailed'))
      if (isMounted) {
        setState(nextState)
      }
    }

    void initialize()

    return () => {
      isMounted = false
    }
  }, [t])

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">{t('model.title')}</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">{t('model.intro')}</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button onClick={loadModels} variant="secondary">
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
            {t('common.refresh')}
          </Button>
          <CreateModelConfigDialog onCreated={() => void loadModels()} providers={state.providers} />
          <CreateModelProviderDialog onCreated={() => void loadModels()} />
        </div>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTitle>{t('model.unavailable')}</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-6 2xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
                <ServerCog className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
              </div>
              <div>
                <CardTitle>{t('model.providers')}</CardTitle>
                <CardDescription>{t('model.providerEndpoints')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : state.providers.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('application.name')}</TableHead>
                    <TableHead>{t('profile.type')}</TableHead>
                    <TableHead>{t('profile.status')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.providers.map((provider) => (
                    <TableRow key={provider.providerId}>
                      <TableCell>
                        <p className="font-medium text-white">{provider.name}</p>
                        <p className="mt-1 max-w-[360px] truncate text-xs text-zinc-500">{provider.baseUrl}</p>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-zinc-400">{provider.providerType}</TableCell>
                      <TableCell>
                        <Badge variant={getStatusVariant(provider.status)}>{provider.status}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <Alert>
                <AlertTitle>{t('model.noProvider')}</AlertTitle>
                <AlertDescription>{t('model.noProviderDescription')}</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-emerald-200/20 bg-emerald-300/10">
                <Cpu className="h-5 w-5 text-emerald-100" strokeWidth={1.75} />
              </div>
              <div>
                <CardTitle>{t('model.modelConfigs')}</CardTitle>
                <CardDescription>{t('model.modelConfigsDescription')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : state.configs.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('model.model')}</TableHead>
                    <TableHead>{t('model.provider')}</TableHead>
                    <TableHead>{t('model.context')}</TableHead>
                    <TableHead>{t('profile.status')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.configs.map((config) => {
                    const provider = providerById.get(config.providerId)

                    return (
                      <TableRow key={config.modelConfigId}>
                        <TableCell>
                          <p className="font-medium text-white">{config.displayName}</p>
                          <p className="mt-1 font-mono text-xs text-zinc-500">{config.modelName}</p>
                        </TableCell>
                        <TableCell className="text-zinc-400">{provider?.name ?? `#${config.providerId}`}</TableCell>
                        <TableCell className="font-mono text-xs text-zinc-400">
                          {config.maxContextTokens?.toLocaleString() ?? '-'}
                        </TableCell>
                        <TableCell>
                          <Badge variant={getStatusVariant(config.status)}>{config.status}</Badge>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            ) : (
              <Alert>
                <AlertTitle>{t('model.noConfig')}</AlertTitle>
                <AlertDescription>{t('model.noConfigDescription')}</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  )
}
