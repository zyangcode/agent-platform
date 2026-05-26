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
import { buildCreateModelConfigPayload } from './model-form-utils'

type CreateModelConfigDialogProps = {
  onCreated: (config: ModelConfig) => void
  providers: ModelProvider[]
}

export function CreateModelConfigDialog({ onCreated, providers }: CreateModelConfigDialogProps) {
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
      setError(caught instanceof ApiError || caught instanceof Error ? caught.message : 'Model config could not be created.')
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
          New model
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create model config</DialogTitle>
          <DialogDescription>
            Bind a concrete model name to a provider. Profiles and Direct model chat can use active configs.
          </DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label>Provider</Label>
            <Select onValueChange={setProviderId} required value={effectiveProviderId}>
              <SelectTrigger>
                <SelectValue placeholder="Select provider" />
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
              <Label htmlFor="model-name">Model name</Label>
              <Input
                id="model-name"
                maxLength={128}
                onChange={(event) => setModelName(event.target.value)}
                required
                value={modelName}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="model-display-name">Display name</Label>
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
              <Label htmlFor="model-temperature">Default temperature</Label>
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
              <Label htmlFor="model-context">Max context tokens</Label>
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
            <Label htmlFor="model-capabilities">Capabilities JSON</Label>
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
              <AlertTitle>Create failed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          {providers.length === 0 ? (
            <Alert>
              <Cpu className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
              <AlertTitle>No provider</AlertTitle>
              <AlertDescription>Create a provider before creating model configs.</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting || providers.length === 0 || !effectiveProviderId} type="submit">
              {isSubmitting ? 'Creating' : 'Create model'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
