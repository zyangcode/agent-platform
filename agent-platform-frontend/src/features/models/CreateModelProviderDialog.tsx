import { type FormEvent, useState } from 'react'
import { Plus } from 'lucide-react'
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
import { ApiError } from '@/lib/api/errors'
import { createModelProvider } from '@/lib/api/model-configs'
import type { ModelProvider } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'

type CreateModelProviderDialogProps = {
  onCreated: (provider: ModelProvider) => void
}

export function CreateModelProviderDialog({ onCreated }: CreateModelProviderDialogProps) {
  const { t } = useI18n()
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('https://api.deepseek.com/v1')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [name, setName] = useState('DeepSeek')
  const [open, setOpen] = useState(false)
  const [providerType, setProviderType] = useState('OPENAI_COMPATIBLE')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const provider = await createModelProvider({
        apiKey: apiKey.trim() || undefined,
        baseUrl: baseUrl.trim(),
        name: name.trim(),
        providerType: providerType.trim(),
      })
      setApiKey('')
      setOpen(false)
      onCreated(provider)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : t('model.providerCreateFailedFallback'))
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
        <Button>
          <Plus className="h-4 w-4" strokeWidth={1.75} />
          {t('model.newProvider')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('model.createProviderTitle')}</DialogTitle>
          <DialogDescription>{t('model.createProviderDescription')}</DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="provider-name">{t('application.name')}</Label>
            <Input
              autoFocus
              id="provider-name"
              maxLength={128}
              onChange={(event) => setName(event.target.value)}
              required
              value={name}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="provider-type">{t('model.providerType')}</Label>
            <Input
              id="provider-type"
              maxLength={32}
              onChange={(event) => setProviderType(event.target.value)}
              required
              value={providerType}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="provider-base-url">{t('model.baseUrl')}</Label>
            <Input
              id="provider-base-url"
              maxLength={512}
              onChange={(event) => setBaseUrl(event.target.value)}
              required
              value={baseUrl}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="provider-api-key">API Key</Label>
            <Input
              id="provider-api-key"
              maxLength={4096}
              onChange={(event) => setApiKey(event.target.value)}
              placeholder="sk-..."
              type="password"
              value={apiKey}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>{t('model.createFailed')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting} type="submit">
              {isSubmitting ? t('application.creating') : t('model.newProvider')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
