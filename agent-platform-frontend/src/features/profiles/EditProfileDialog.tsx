import { type FormEvent, useMemo, useState } from 'react'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/errors'
import type { ModelConfig, Profile } from '@/lib/api/types'
import { updateProfile } from './api'

type EditProfileForm = {
  description: string
  maxSteps: string
  modelConfigId: string
  name: string
  promptExtra: string
}

type EditProfileDialogProps = {
  modelConfigs: ModelConfig[]
  onUpdated: (profile: Profile) => void
  profile: Profile | null
}

export function EditProfileDialog({ modelConfigs, onUpdated, profile }: EditProfileDialogProps) {
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<EditProfileForm>(emptyForm())
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [open, setOpen] = useState(false)

  const effectiveModelConfigId = useMemo(() => {
    return form.modelConfigId || String(profile?.modelConfigId ?? modelConfigs[0]?.modelConfigId ?? '')
  }, [form.modelConfigId, modelConfigs, profile])

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen && profile) {
      setForm(formFromProfile(profile))
      setError(null)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!profile || !effectiveModelConfigId) {
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      const updatedProfile = await updateProfile(profile.profileId, {
        description: form.description.trim() || undefined,
        maxSteps: Number(form.maxSteps),
        memoryStrategy: profile.memoryStrategy ?? { mode: 'READ_WRITE' },
        modelConfigId: Number(effectiveModelConfigId),
        name: form.name.trim(),
        promptExtra: form.promptExtra.trim() || undefined,
        visibility: profile.visibility || 'PRIVATE',
      })
      setOpen(false)
      onUpdated(updatedProfile)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Profile could not be updated.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog onOpenChange={handleOpenChange} open={open}>
      <DialogTrigger asChild>
        <Button disabled={!profile || modelConfigs.length === 0} variant="secondary">
          <Pencil className="h-4 w-4" strokeWidth={1.75} />
          Edit profile
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Edit profile</DialogTitle>
          <DialogDescription>Update the profile model, prompt, and runtime step limit.</DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="edit-profile-name">Name</Label>
              <Input
                autoFocus
                id="edit-profile-name"
                maxLength={128}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                required
                value={form.name}
              />
            </div>

            <div className="space-y-2">
              <Label>Model config</Label>
              <Select
                onValueChange={(value) => setForm((current) => ({ ...current, modelConfigId: value }))}
                value={effectiveModelConfigId}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select model" />
                </SelectTrigger>
                <SelectContent>
                  {modelConfigs.map((modelConfig) => (
                    <SelectItem key={modelConfig.modelConfigId} value={String(modelConfig.modelConfigId)}>
                      {modelConfig.displayName || modelConfig.modelName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-profile-description">Description</Label>
            <Input
              id="edit-profile-description"
              maxLength={2048}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
              value={form.description}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-profile-prompt">Prompt extra</Label>
            <Textarea
              className="min-h-32"
              id="edit-profile-prompt"
              maxLength={8000}
              onChange={(event) => setForm((current) => ({ ...current, promptExtra: event.target.value }))}
              value={form.promptExtra}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-profile-max-steps">Max steps</Label>
            <Input
              id="edit-profile-max-steps"
              max={50}
              min={1}
              onChange={(event) => setForm((current) => ({ ...current, maxSteps: event.target.value }))}
              required
              type="number"
              value={form.maxSteps}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>Update failed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting || !profile || !effectiveModelConfigId} type="submit">
              {isSubmitting ? 'Saving' : 'Save changes'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function emptyForm(): EditProfileForm {
  return {
    description: '',
    maxSteps: '5',
    modelConfigId: '',
    name: '',
    promptExtra: '',
  }
}

function formFromProfile(profile: Profile): EditProfileForm {
  return {
    description: profile.description ?? '',
    maxSteps: String(profile.maxSteps ?? 5),
    modelConfigId: String(profile.modelConfigId),
    name: profile.name,
    promptExtra: profile.promptExtra ?? '',
  }
}
