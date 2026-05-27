import { useState } from 'react'
import { Power } from 'lucide-react'
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
import { ApiError } from '@/lib/api/errors'
import type { Application } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { disableApplication } from './api'

type DisableApplicationDialogProps = {
  application: Application
  onDisabled: (application: Application) => void
}

export function DisableApplicationDialog({ application, onDisabled }: DisableApplicationDialogProps) {
  const { t } = useI18n()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [open, setOpen] = useState(false)
  const isActive = application.status.toUpperCase() === 'ACTIVE'

  async function handleDisable() {
    setError(null)
    setIsSubmitting(true)

    try {
      const disabledApplication = await disableApplication(application.applicationId)
      setOpen(false)
      onDisabled(disabledApplication)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : t('application.disableFailedFallback'))
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
        <Button disabled={!isActive} size="sm" variant="danger">
          <Power className="h-4 w-4" strokeWidth={1.75} />
          {t('application.disable')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('application.disableTitle')}</DialogTitle>
          <DialogDescription>{t('application.disableDescription', { name: application.name })}</DialogDescription>
        </DialogHeader>

        {error ? (
          <Alert className="mt-5" variant="danger">
            <AlertTitle>{t('application.disableFailed')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="secondary">
            {t('profile.cancel')}
          </Button>
          <Button disabled={isSubmitting || !isActive} onClick={handleDisable} variant="danger">
            {isSubmitting ? t('application.disabling') : t('application.disableTitle')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
