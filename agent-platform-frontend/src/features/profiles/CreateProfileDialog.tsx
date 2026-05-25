import { type FormEvent, useMemo, useState } from 'react'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/errors'
import type { Application, ModelConfig, Profile } from '@/lib/api/types'
import { createProfile } from './api'

type CreateProfileDialogProps = {
  application: Application | null
  modelConfigs: ModelConfig[]
  onCreated: (profile: Profile) => void
}

export function CreateProfileDialog({ application, modelConfigs, onCreated }: CreateProfileDialogProps) {
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [maxSteps, setMaxSteps] = useState('5')
  const [modelConfigId, setModelConfigId] = useState<string>('')
  const [name, setName] = useState('')
  const [open, setOpen] = useState(false)
  const [promptExtra, setPromptExtra] = useState('')

  const effectiveModelConfigId = useMemo(() => {
    return modelConfigId || String(modelConfigs[0]?.modelConfigId ?? '')
  }, [modelConfigId, modelConfigs])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!application || !effectiveModelConfigId) {
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      const profile = await createProfile({
        applicationId: application.applicationId,
        description: description.trim() || undefined,
        maxSteps: Number(maxSteps),
        memoryStrategy: { mode: 'READ_WRITE' },
        modelConfigId: Number(effectiveModelConfigId),
        name: name.trim(),
        profileType: 'GENERAL',
        promptExtra: promptExtra.trim() || undefined,
        visibility: 'PRIVATE',
      })
      setDescription('')
      setMaxSteps('5')
      setName('')
      setOpen(false)
      setPromptExtra('')
      onCreated(profile)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Profile could not be created.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button disabled={!application || modelConfigs.length === 0}>
          <Plus className="h-4 w-4" strokeWidth={1.75} />
          New profile
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Create profile</DialogTitle>
          <DialogDescription>
            MVP creates a private general profile with READ_WRITE memory and a single model config.
          </DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="profile-name">Name</Label>
              <Input
                autoFocus
                id="profile-name"
                maxLength={128}
                onChange={(event) => setName(event.target.value)}
                placeholder="Research assistant"
                required
                value={name}
              />
            </div>

            <div className="space-y-2">
              <Label>Model config</Label>
              <Select onValueChange={setModelConfigId} value={effectiveModelConfigId}>
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
            <Label htmlFor="profile-description">Description</Label>
            <Input
              id="profile-description"
              maxLength={2048}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="A focused assistant for project research and summary tasks."
              value={description}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="profile-prompt">Prompt extra</Label>
            <Textarea
              className="min-h-32"
              id="profile-prompt"
              maxLength={8000}
              onChange={(event) => setPromptExtra(event.target.value)}
              placeholder="Answer with concise reasoning and cite relevant project context when available."
              value={promptExtra}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="profile-max-steps">Max steps</Label>
            <Input
              id="profile-max-steps"
              max={50}
              min={1}
              onChange={(event) => setMaxSteps(event.target.value)}
              required
              type="number"
              value={maxSteps}
            />
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>Create failed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting || !application || !effectiveModelConfigId} type="submit">
              {isSubmitting ? 'Creating' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
