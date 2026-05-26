import { type FormEvent, useState } from 'react'
import { Pencil } from 'lucide-react'
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
import type { Application } from '@/lib/api/types'
import { updateApplication } from './api'

type EditApplicationDialogProps = {
  application: Application
  onUpdated: (application: Application) => void
}

export function EditApplicationDialog({ application, onUpdated }: EditApplicationDialogProps) {
  const [description, setDescription] = useState(application.description ?? '')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [name, setName] = useState(application.name)
  const [open, setOpen] = useState(false)

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)

    if (nextOpen) {
      setName(application.name)
      setDescription(application.description ?? '')
      setError(null)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const updatedApplication = await updateApplication(application.applicationId, {
        description: description.trim() || undefined,
        name: name.trim(),
      })
      setOpen(false)
      onUpdated(updatedApplication)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Application could not be updated.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog onOpenChange={handleOpenChange} open={open}>
      <DialogTrigger asChild>
        <Button size="sm" variant="secondary">
          <Pencil className="h-4 w-4" strokeWidth={1.75} />
          Edit
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit application</DialogTitle>
          <DialogDescription>Rename the application or update its usage note.</DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor={`edit-application-name-${application.applicationId}`}>Name</Label>
            <Input
              autoFocus
              id={`edit-application-name-${application.applicationId}`}
              maxLength={128}
              onChange={(event) => setName(event.target.value)}
              required
              value={name}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`edit-application-description-${application.applicationId}`}>Description</Label>
            <Textarea
              id={`edit-application-description-${application.applicationId}`}
              maxLength={1000}
              onChange={(event) => setDescription(event.target.value)}
              value={description}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>Update failed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting} type="submit">
              {isSubmitting ? 'Saving' : 'Save changes'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
