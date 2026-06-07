import { useEffect, useMemo, useState } from 'react'
import { RefreshCw, Save } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api/errors'
import type { McpTool, Profile, Skill } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { bindProfileMcpTools, bindProfileSkills, listMcpTools, listSkills } from './api'
import { getStatusVariant, toggleNumber } from './profile-utils'

type ProfileToolBindingPanelProps = {
  onProfileChanged: (profileId: number) => void
  profile: Profile | null
}

type ToolState =
  | { error: null; mcpTools: McpTool[]; skills: Skill[]; status: 'ready' }
  | { error: string | null; mcpTools: McpTool[]; skills: Skill[]; status: 'error' | 'loading' }

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

export function ProfileToolBindingPanel({ onProfileChanged, profile }: ProfileToolBindingPanelProps) {
  const { t } = useI18n()
  const [selectedMcpToolIds, setSelectedMcpToolIds] = useState<number[]>([])
  const [selectedSkillIds, setSelectedSkillIds] = useState<number[]>([])
  const [selectionProfileId, setSelectionProfileId] = useState<number | null>(null)
  const [state, setState] = useState<ToolState>({
    error: null,
    mcpTools: [],
    skills: [],
    status: 'loading',
  })
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const isEditableProfile = profile?.status.toUpperCase() === 'DRAFT'

  const canSave = useMemo(
    () => !!profile && isEditableProfile && state.status === 'ready' && !isSaving,
    [isEditableProfile, isSaving, profile, state.status],
  )

  const effectiveSelectedSkillIds =
    selectionProfileId === profile?.profileId
      ? selectedSkillIds
      : profile?.skillBindings.map((binding) => binding.skillId) ?? []

  const effectiveSelectedMcpToolIds =
    selectionProfileId === profile?.profileId
      ? selectedMcpToolIds
      : profile?.mcpToolBindings.map((binding) => binding.mcpToolId) ?? []

  async function loadTools() {
    setState({ error: null, mcpTools: [], skills: [], status: 'loading' })
    try {
      const [skills, mcpTools] = await Promise.all([listSkills(), listMcpTools()])
      setState({ error: null, mcpTools, skills, status: 'ready' })
    } catch (error) {
      setState({
        error: getErrorMessage(error, t('profile.toolsUnavailable')),
        mcpTools: [],
        skills: [],
        status: 'error',
      })
    }
  }

  async function handleSave() {
    if (!profile) {
      return
    }

    setIsSaving(true)
    setSubmitError(null)

    try {
      await Promise.all([
        bindProfileSkills(profile.profileId, effectiveSelectedSkillIds),
        bindProfileMcpTools(profile.profileId, effectiveSelectedMcpToolIds),
      ])
      onProfileChanged(profile.profileId)
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('profile.saveFailed'))
    } finally {
      setIsSaving(false)
    }
  }

  useEffect(() => {
    let isMounted = true

    async function initializeTools() {
      try {
        const [skills, mcpTools] = await Promise.all([listSkills(), listMcpTools()])

        if (isMounted) {
          setState({ error: null, mcpTools, skills, status: 'ready' })
        }
      } catch (error) {
        if (isMounted) {
          setState({
            error: getErrorMessage(error, t('profile.toolsUnavailable')),
            mcpTools: [],
            skills: [],
            status: 'error',
          })
        }
      }
    }

    void initializeTools()

    return () => {
      isMounted = false
    }
  }, [t])

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>{t('profile.toolBindings')}</CardTitle>
            <CardDescription>{t('profile.toolBindingsDescription')}</CardDescription>
          </div>
          <Button onClick={loadTools} size="sm" variant="secondary">
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
            {t('common.refresh')}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        {!profile ? (
          <Alert>
            <AlertTitle>{t('profile.noProfileSelected')}</AlertTitle>
            <AlertDescription>{t('profile.noProfileSelectedBindingDescription')}</AlertDescription>
          </Alert>
        ) : !isEditableProfile ? (
          <Alert variant="danger">
            <AlertTitle>{t('profile.profileDisabled')}</AlertTitle>
            <AlertDescription>{t('profile.bindingsReadOnly')}</AlertDescription>
          </Alert>
        ) : null}

        {state.status === 'loading' ? (
          <div className="grid gap-3">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : null}

        {state.status === 'error' ? (
          <Alert variant="danger">
            <AlertTitle>{t('profile.toolsUnavailable')}</AlertTitle>
            <AlertDescription>{state.error}</AlertDescription>
          </Alert>
        ) : null}

        {state.status === 'ready' ? (
          <div className="grid gap-5 xl:grid-cols-2">
            <ToolChecklist
              disabled={!profile || !isEditableProfile}
              emptyText={t('profile.noEnabledSkills')}
              items={state.skills
                .filter((skill) => skill.skillType !== 'BUILTIN')
                .map((skill) => ({
                description: skill.description || t('profile.noDescription'),
                id: skill.skillId,
                meta: skill.scope,
                name: skill.name,
                status: skill.status,
              }))}
              onToggle={setSelectedSkillIds}
              selectedIds={effectiveSelectedSkillIds}
              seedDataHint={t('profile.seedDataHint')}
              setSelectionProfileId={() => setSelectionProfileId(profile?.profileId ?? null)}
              title={t('profile.skills')}
            />
            <ToolChecklist
              disabled={!profile || !isEditableProfile}
              emptyText={t('profile.noEnabledMcpTools')}
              items={state.mcpTools.map((tool) => ({
                description: tool.description || t('profile.noDescription'),
                id: tool.mcpToolId,
                meta: `server ${tool.mcpServerId}`,
                name: tool.name,
                status: tool.status,
              }))}
              onToggle={setSelectedMcpToolIds}
              selectedIds={effectiveSelectedMcpToolIds}
              seedDataHint={t('profile.seedDataHint')}
              setSelectionProfileId={() => setSelectionProfileId(profile?.profileId ?? null)}
              title={t('profile.mcpTools')}
            />
          </div>
        ) : null}

        {submitError ? (
          <Alert variant="danger">
            <AlertTitle>{t('profile.saveFailed')}</AlertTitle>
            <AlertDescription>{submitError}</AlertDescription>
          </Alert>
        ) : null}

        <div className="flex justify-end">
          <Button disabled={!canSave} onClick={handleSave}>
            <Save className="h-4 w-4" strokeWidth={1.75} />
            {isSaving ? t('profile.saving') : t('profile.saveBindings')}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

type ChecklistItem = {
  description: string
  id: number
  meta: string
  name: string
  status: string
}

type ToolChecklistProps = {
  disabled: boolean
  emptyText: string
  items: ChecklistItem[]
  onToggle: (ids: number[]) => void
  selectedIds: number[]
  seedDataHint: string
  setSelectionProfileId: () => void
  title: string
}

function ToolChecklist({
  disabled,
  emptyText,
  items,
  onToggle,
  selectedIds,
  seedDataHint,
  setSelectionProfileId,
  title,
}: ToolChecklistProps) {
  return (
    <div>
      <p className="mb-3 text-sm font-medium text-white">{title}</p>
      {items.length === 0 ? (
        <Alert>
          <AlertTitle>{emptyText}</AlertTitle>
          <AlertDescription>{seedDataHint}</AlertDescription>
        </Alert>
      ) : (
        <div className="space-y-2">
          {items.map((item) => {
            const selected = selectedIds.includes(item.id)

            return (
              <label
                className="flex cursor-pointer gap-3 rounded-xl border border-white/10 bg-white/[0.04] p-3 transition hover:bg-white/[0.065]"
                key={item.id}
              >
                <input
                  checked={selected}
                  className="mt-1 h-4 w-4 accent-cyan-200"
                  disabled={disabled}
                  onChange={() => {
                    setSelectionProfileId()
                    onToggle(toggleNumber(selectedIds, item.id))
                  }}
                  type="checkbox"
                />
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-white">{item.name}</span>
                    <Badge variant={getStatusVariant(item.status)}>{item.status}</Badge>
                    <span className="font-mono text-xs text-zinc-500">{item.meta}</span>
                  </span>
                  <span className="mt-1 block truncate text-xs text-zinc-500">
                    {item.description}
                  </span>
                </span>
              </label>
            )
          })}
        </div>
      )}
    </div>
  )
}
