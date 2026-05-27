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
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/errors'
import type { CreateApplicationResult } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { createApplication } from './api'

type CreateApplicationDialogProps = {
  onCreated: (result: CreateApplicationResult) => void
}

export function CreateApplicationDialog({ onCreated }: CreateApplicationDialogProps) {
  const { t } = useI18n()
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [name, setName] = useState('')
  const [open, setOpen] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const result = await createApplication({
        description: description.trim() || undefined,
        name: name.trim(),
      })
      setName('')
      setDescription('')
      setOpen(false)
      onCreated(result)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : t('application.createFailedFallback'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" strokeWidth={1.75} />
          {t('application.createButton')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('application.createButton')}</DialogTitle>
          <DialogDescription>{t('application.createDescription')}</DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="application-name">{t('application.name')}</Label>
            <Input
              autoFocus
              id="application-name"
              maxLength={128}
              onChange={(event) => setName(event.target.value)}
              placeholder={t('application.namePlaceholder')}
              required
              value={name}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="application-description">{t('application.description')}</Label>
            <Textarea
              id="application-description"
              maxLength={1000}
              onChange={(event) => setDescription(event.target.value)}
              placeholder={t('application.descriptionPlaceholder')}
              value={description}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>{t('application.createFailed')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting} type="submit">
              {isSubmitting ? t('application.creating') : t('application.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
