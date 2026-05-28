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
import type { Application, ModelConfig, Profile, ProfileExecutionMode } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { createProfile } from './api'

type CreateProfileDialogProps = {
  application: Application | null
  modelConfigs: ModelConfig[]
  onCreated: (profile: Profile) => void
}

export function CreateProfileDialog({ application, modelConfigs, onCreated }: CreateProfileDialogProps) {
  const { t } = useI18n()
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [maxSteps, setMaxSteps] = useState('5')
  const [modelConfigId, setModelConfigId] = useState<string>('')
  const [name, setName] = useState('')
  const [open, setOpen] = useState(false)
  const [promptExtra, setPromptExtra] = useState('')
  const [executionMode, setExecutionMode] = useState<ProfileExecutionMode>('BASIC')

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
        executionMode,
        visibility: 'PRIVATE',
      })
      setDescription('')
      setExecutionMode('BASIC')
      setMaxSteps('5')
      setName('')
      setOpen(false)
      setPromptExtra('')
      onCreated(profile)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : t('profile.createFailed'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog onOpenChange={setOpen} open={open}>
      <DialogTrigger asChild>
        <Button disabled={!application || modelConfigs.length === 0}>
          <Plus className="h-4 w-4" strokeWidth={1.75} />
          {t('profile.createButton')}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('profile.createTitle')}</DialogTitle>
          <DialogDescription>
            {t('profile.createDescription')}
          </DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-5" onSubmit={handleSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="profile-name">{t('profile.name')}</Label>
              <Input
                autoFocus
                id="profile-name"
                maxLength={128}
                onChange={(event) => setName(event.target.value)}
                placeholder={t('profile.namePlaceholder')}
                required
                value={name}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('profile.modelConfig')}</Label>
              <Select onValueChange={setModelConfigId} value={effectiveModelConfigId}>
                <SelectTrigger>
                  <SelectValue placeholder={t('profile.selectModel')} />
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
            <Label htmlFor="profile-description">{t('profile.description')}</Label>
            <Input
              id="profile-description"
              maxLength={2048}
              onChange={(event) => setDescription(event.target.value)}
              placeholder={t('profile.descriptionPlaceholder')}
              value={description}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="profile-prompt">{t('profile.stylePrompt')}</Label>
            <Textarea
              className="min-h-32"
              id="profile-prompt"
              maxLength={8000}
              onChange={(event) => setPromptExtra(event.target.value)}
              placeholder={t('profile.stylePromptPlaceholder')}
              value={promptExtra}
            />
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="profile-max-steps">{t('profile.maxSteps')}</Label>
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

            <div className="space-y-2">
              <Label>{t('profile.executionMode')}</Label>
              <Select
                onValueChange={(value) => setExecutionMode(value as ProfileExecutionMode)}
                value={executionMode}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('profile.selectExecutionMode')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="BASIC">{t('profile.executionModeBasic')}</SelectItem>
                  <SelectItem value="TEAM">{t('profile.executionModeTeam')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {error ? (
            <Alert variant="danger">
              <AlertTitle>{t('profile.createFailed')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button disabled={isSubmitting || !application || !effectiveModelConfigId} type="submit">
              {isSubmitting ? t('profile.creating') : t('profile.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
