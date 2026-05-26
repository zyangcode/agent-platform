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
import { disableApplication } from './api'

type DisableApplicationDialogProps = {
  application: Application
  onDisabled: (application: Application) => void
}

export function DisableApplicationDialog({ application, onDisabled }: DisableApplicationDialogProps) {
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
      setError(caught instanceof ApiError ? caught.message : 'Application could not be disabled.')
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
          Disable
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Disable application</DialogTitle>
          <DialogDescription>
            {application.name} will stay visible, but Chat, Profile, and API key operations will no
            longer use it.
          </DialogDescription>
        </DialogHeader>

        {error ? (
          <Alert className="mt-5" variant="danger">
            <AlertTitle>Disable failed</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="secondary">
            Cancel
          </Button>
          <Button disabled={isSubmitting || !isActive} onClick={handleDisable} variant="danger">
            {isSubmitting ? 'Disabling' : 'Disable application'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
