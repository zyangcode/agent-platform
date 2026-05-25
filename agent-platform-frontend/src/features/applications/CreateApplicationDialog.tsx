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
import { createApplication } from './api'

type CreateApplicationDialogProps = {
  onCreated: (result: CreateApplicationResult) => void
}

export function CreateApplicationDialog({ onCreated }: CreateApplicationDialogProps) {
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
      setError(caught instanceof ApiError ? caught.message : 'Application could not be created.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" strokeWidth={1.75} />
          New application
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create application</DialogTitle>
          <DialogDescription>
            Applications own API keys and provide the scope for later Profile and usage views.
          </DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="application-name">Name</Label>
            <Input
              autoFocus
              id="application-name"
              maxLength={128}
              onChange={(event) => setName(event.target.value)}
              placeholder="Research console"
              required
              value={name}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="application-description">Description</Label>
            <Textarea
              id="application-description"
              maxLength={1000}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Internal application for Agent platform demos."
              value={description}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>Create failed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting} type="submit">
              {isSubmitting ? 'Creating' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
