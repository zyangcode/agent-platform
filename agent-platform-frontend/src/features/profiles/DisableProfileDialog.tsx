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
import type { Profile } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { disableProfile } from './api'

type DisableProfileDialogProps = {
  onDisabled: (profile: Profile) => void
  profile: Profile | null
}

export function DisableProfileDialog({ onDisabled, profile }: DisableProfileDialogProps) {
  const { t } = useI18n()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [open, setOpen] = useState(false)
  const isDraft = profile?.status.toUpperCase() === 'DRAFT'

  async function handleDisable() {
    if (!profile) {
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      const disabledProfile = await disableProfile(profile.profileId)
      setOpen(false)
      onDisabled(disabledProfile)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : t('profile.disableFailed'))
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
        <Button disabled={!profile || !isDraft} variant="danger">
          <Power className="h-4 w-4" strokeWidth={1.75} />
          {t('profile.disableButton')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('profile.disableTitle')}</DialogTitle>
          <DialogDescription>
            {t('profile.disableDescription', { name: profile?.name ?? t('profile.detail') })}
          </DialogDescription>
        </DialogHeader>

        {error ? (
          <Alert className="mt-5" variant="danger">
            <AlertTitle>{t('profile.disableFailed')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <DialogFooter>
          <Button onClick={() => setOpen(false)} variant="secondary">
            {t('profile.cancel')}
          </Button>
          <Button disabled={isSubmitting || !profile || !isDraft} onClick={handleDisable} variant="danger">
            {isSubmitting ? t('profile.disabling') : t('profile.disableButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
