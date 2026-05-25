import { Bot, Boxes, BrainCircuit } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { ModelConfig, Profile } from '@/lib/api/types'
import { getStatusVariant } from './profile-utils'

type ProfileDetailPanelProps = {
  modelConfigs: ModelConfig[]
  profile: Profile | null
}

export function ProfileDetailPanel({ modelConfigs, profile }: ProfileDetailPanelProps) {
  if (!profile) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Profile detail</CardTitle>
          <CardDescription>Inspect prompt, memory, model, and bound tools.</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert>
            <AlertTitle>No profile selected</AlertTitle>
            <AlertDescription>Create or select a profile to inspect its runtime setup.</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  const modelConfig = modelConfigs.find((model) => model.modelConfigId === profile.modelConfigId)

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>{profile.name}</CardTitle>
            <CardDescription>{profile.description || 'No description'}</CardDescription>
          </div>
          <Badge variant={getStatusVariant(profile.status)}>{profile.status}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid gap-3 md:grid-cols-3">
          <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
            <div className="flex items-center gap-2 text-xs text-zinc-500">
              <Bot className="h-3.5 w-3.5" strokeWidth={1.75} />
              Type
            </div>
            <p className="mt-2 text-sm font-medium text-white">{profile.profileType}</p>
          </div>
          <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
            <div className="flex items-center gap-2 text-xs text-zinc-500">
              <BrainCircuit className="h-3.5 w-3.5" strokeWidth={1.75} />
              Model
            </div>
            <p className="mt-2 truncate text-sm font-medium text-white">
              {modelConfig?.displayName || modelConfig?.modelName || `#${profile.modelConfigId}`}
            </p>
          </div>
          <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-3">
            <div className="flex items-center gap-2 text-xs text-zinc-500">
              <Boxes className="h-3.5 w-3.5" strokeWidth={1.75} />
              Tools
            </div>
            <p className="mt-2 text-sm font-medium text-white">
              {profile.skillBindings.length + profile.mcpToolBindings.length}
            </p>
          </div>
        </div>

        <div className="rounded-xl border border-white/10 bg-zinc-950/50 p-4">
          <p className="text-xs uppercase tracking-[0.16em] text-zinc-500">Prompt extra</p>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-zinc-300">
            {profile.promptExtra || 'No extra prompt configured.'}
          </p>
        </div>

        <div className="grid gap-3 md:grid-cols-2">
          <BindingSummary
            emptyText="No Skills bound."
            items={profile.skillBindings.map((binding) => binding.name || `Skill #${binding.skillId}`)}
            title="Skill bindings"
          />
          <BindingSummary
            emptyText="No MCP tools bound."
            items={profile.mcpToolBindings.map((binding) => binding.name || `MCP Tool #${binding.mcpToolId}`)}
            title="MCP bindings"
          />
        </div>
      </CardContent>
    </Card>
  )
}

type BindingSummaryProps = {
  emptyText: string
  items: string[]
  title: string
}

function BindingSummary({ emptyText, items, title }: BindingSummaryProps) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.04] p-4">
      <p className="text-sm font-medium text-white">{title}</p>
      {items.length === 0 ? (
        <p className="mt-3 text-sm text-zinc-500">{emptyText}</p>
      ) : (
        <div className="mt-3 flex flex-wrap gap-2">
          {items.map((item) => (
            <Badge key={item} variant="muted">
              {item}
            </Badge>
          ))}
        </div>
      )}
    </div>
  )
}
