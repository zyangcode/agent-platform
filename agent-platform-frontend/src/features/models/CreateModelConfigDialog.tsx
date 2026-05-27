import { type FormEvent, useMemo, useState } from 'react'
import { Cpu, Plus } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/errors'
import { createModelConfig } from '@/lib/api/model-configs'
import type { ModelConfig, ModelProvider } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { buildCreateModelConfigPayload } from './model-form-utils'

type CreateModelConfigDialogProps = {
  onCreated: (config: ModelConfig) => void
  providers: ModelProvider[]
}

export function CreateModelConfigDialog({ onCreated, providers }: CreateModelConfigDialogProps) {
  const { t } = useI18n()
  const [capabilitiesJson, setCapabilitiesJson] = useState('{}')
  const [defaultTemperature, setDefaultTemperature] = useState('0.70')
  const [displayName, setDisplayName] = useState('DeepSeek Chat')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [maxContextTokens, setMaxContextTokens] = useState('64000')
  const [modelName, setModelName] = useState('deepseek-chat')
  const [open, setOpen] = useState(false)
  const [providerId, setProviderId] = useState('')

  const effectiveProviderId = useMemo(() => {
    return providerId || (providers[0]?.providerId ? String(providers[0].providerId) : '')
  }, [providerId, providers])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const config = await createModelConfig(
        buildCreateModelConfigPayload({
          capabilitiesJson,
          defaultTemperature,
          displayName,
          maxContextTokens,
          modelName,
          providerId: effectiveProviderId,
        }),
      )
      setOpen(false)
      onCreated(config)
    } catch (caught) {
      setError(caught instanceof ApiError || caught instanceof Error ? caught.message : t('model.createFailed'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen)
        if (nextOpen) {
          setError(null)
        }
      }}
      open={open}
    >
      <DialogTrigger asChild>
        <Button disabled={providers.length === 0} variant="secondary">
          <Plus className="h-4 w-4" strokeWidth={1.75} />
          {t('model.newModel')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('model.createConfigTitle')}</DialogTitle>
          <DialogDescription>{t('model.createConfigDescription')}</DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label>{t('model.provider')}</Label>
            <Select onValueChange={setProviderId} required value={effectiveProviderId}>
              <SelectTrigger>
                <SelectValue placeholder={t('model.selectProvider')} />
              </SelectTrigger>
              <SelectContent>
                {providers.map((provider) => (
                  <SelectItem key={provider.providerId} value={String(provider.providerId)}>
                    {provider.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="model-name">{t('model.modelName')}</Label>
              <Input
                id="model-name"
                maxLength={128}
                onChange={(event) => setModelName(event.target.value)}
                required
                value={modelName}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="model-display-name">{t('model.displayName')}</Label>
              <Input
                id="model-display-name"
                maxLength={128}
                onChange={(event) => setDisplayName(event.target.value)}
                required
                value={displayName}
              />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="model-temperature">{t('model.defaultTemperature')}</Label>
              <Input
                id="model-temperature"
                max="2"
                min="0"
                onChange={(event) => setDefaultTemperature(event.target.value)}
                step="0.01"
                type="number"
                value={defaultTemperature}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="model-context">{t('model.maxContextTokens')}</Label>
              <Input
                id="model-context"
                max="2000000"
                min="1"
                onChange={(event) => setMaxContextTokens(event.target.value)}
                required
                type="number"
                value={maxContextTokens}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="model-capabilities">{t('model.capabilitiesJson')}</Label>
            <Textarea
              className="font-mono"
              id="model-capabilities"
              maxLength={4096}
              onChange={(event) => setCapabilitiesJson(event.target.value)}
              rows={4}
              value={capabilitiesJson}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>{t('model.createFailed')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          {providers.length === 0 ? (
            <Alert>
              <Cpu className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
              <AlertTitle>{t('model.noProvider')}</AlertTitle>
              <AlertDescription>{t('model.noProviderDescription')}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting || providers.length === 0 || !effectiveProviderId} type="submit">
              {isSubmitting ? t('application.creating') : t('model.createModel')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
