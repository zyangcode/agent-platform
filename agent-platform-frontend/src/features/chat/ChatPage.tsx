import { useEffect, useMemo, useRef, useState } from 'react'
import { Bot, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import type { Application, ModelConfig, PageResult, Profile } from '@/lib/api/types'
import { listApplications } from '@/features/applications/api'
import { ApiError } from '@/lib/api/errors'
import { listModelConfigs } from '@/lib/api/model-configs'
import { listProfiles } from '@/lib/api/profiles'
import { streamChat } from './api'
import { nextConversationId } from './chat-session-utils'
import { ConversationPanel } from './ConversationPanel'
import { RuntimeDetailPanel } from './RuntimeDetailPanel'
import type { AgentMode, ChatMessage, ChatStreamEvent, RuntimeStatus } from './types'

type ResourceState =
  | {
      applications: PageResult<Application>
      error: null
      modelConfigs: ModelConfig[]
      profiles: Profile[]
      status: 'ready'
    }
  | {
      applications: null
      error: string | null
      modelConfigs: ModelConfig[]
      profiles: Profile[]
      status: 'error' | 'loading'
    }

const AGENT_MODE_LABELS: Record<AgentMode, string> = {
  agent: 'Agent',
  none: 'Direct model',
}

function nextId(prefix: string) {
  return `${prefix}_${crypto.randomUUID()}`
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.kind === 'quota_exceeded') {
      return 'Token quota is not enough for this request.'
    }
    if (error.kind === 'forbidden') {
      return 'The request was blocked by security policy or permission rules.'
    }
    return error.message
  }

  return 'Chat stream failed.'
}

export function ChatPage() {
  const abortControllerRef = useRef<AbortController | null>(null)
  const [agentMode, setAgentMode] = useState<AgentMode>('agent')
  const [input, setInput] = useState('')
  const [conversationId, setConversationId] = useState<number | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [runtimeError, setRuntimeError] = useState<string | null>(null)
  const [runtimeEvents, setRuntimeEvents] = useState<ChatStreamEvent[]>([])
  const [runtimeStatus, setRuntimeStatus] = useState<RuntimeStatus>('idle')
  const [selectedApplicationId, setSelectedApplicationId] = useState<number | null>(null)
  const [selectedModelConfigId, setSelectedModelConfigId] = useState<number | null>(null)
  const [selectedProfileId, setSelectedProfileId] = useState<number | null>(null)
  const [state, setState] = useState<ResourceState>({
    applications: null,
    error: null,
    modelConfigs: [],
    profiles: [],
    status: 'loading',
  })

  const selectedApplication = useMemo(() => {
    return (
      state.applications?.records.find(
        (application) => application.applicationId === selectedApplicationId,
      ) ?? state.applications?.records[0] ?? null
    )
  }, [selectedApplicationId, state.applications])

  const selectedProfile = useMemo(() => {
    return state.profiles.find((profile) => profile.profileId === selectedProfileId) ?? null
  }, [selectedProfileId, state.profiles])

  const selectedModelConfig = useMemo(() => {
    return (
      state.modelConfigs.find((modelConfig) => modelConfig.modelConfigId === selectedModelConfigId) ??
      state.modelConfigs[0] ??
      null
    )
  }, [selectedModelConfigId, state.modelConfigs])

  const disabledReason = useMemo(() => {
    if (state.status === 'loading') {
      return 'Loading applications.'
    }
    if (!selectedApplication) {
      return 'Create an application before starting chat.'
    }
    if (agentMode === 'agent' && !selectedProfile) {
      return 'Agent mode needs a profile. Switch to Direct model or create a profile first.'
    }
    if (agentMode === 'none' && !selectedModelConfig) {
      return 'Direct model mode needs an active model config.'
    }
    if (runtimeStatus === 'streaming') {
      return null
    }

    return null
  }, [agentMode, runtimeStatus, selectedApplication, selectedModelConfig, selectedProfile, state.status])

  async function fetchResources(applicationId?: number | null) {
    try {
      const [applications, modelConfigs] = await Promise.all([
        listApplications(1, 50),
        listModelConfigs(),
      ])
      const effectiveApplicationId = applicationId ?? applications.records[0]?.applicationId ?? null
      const profiles = effectiveApplicationId
        ? (await listProfiles(effectiveApplicationId, 1, 50)).records
        : []

      return {
        applications,
        error: null,
        modelConfigs,
        profiles,
        status: 'ready',
      } satisfies ResourceState
    } catch (error) {
      return {
        applications: null,
        error: getErrorMessage(error),
        modelConfigs: [],
        profiles: [],
        status: 'error',
      } satisfies ResourceState
    }
  }

  async function loadResources(applicationId?: number | null) {
    setState((current) => ({ ...current, applications: null, error: null, status: 'loading' }))
    const nextState = await fetchResources(applicationId)
    setState(nextState)

    if (nextState.status === 'ready') {
      const nextApplicationId = applicationId ?? nextState.applications.records[0]?.applicationId ?? null
      setSelectedApplicationId(nextApplicationId)
      setSelectedModelConfigId((current) => current ?? nextState.modelConfigs[0]?.modelConfigId ?? null)
      setSelectedProfileId(nextState.profiles[0]?.profileId ?? null)
    }
  }

  async function handleApplicationChange(value: string) {
    const applicationId = Number(value)
    setSelectedApplicationId(applicationId)
    setSelectedProfileId(null)
    setConversationId(null)
    setMessages([])
    await loadResources(applicationId)
  }

  function handleEvent(event: ChatStreamEvent, assistantMessageId: string) {
    setRuntimeEvents((current) => [...current, event])
    setConversationId((current) => nextConversationId(current, event))

    if (event.type === 'message' && event.content) {
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantMessageId
            ? { ...message, content: `${message.content}${event.content}` }
            : message,
        ),
      )
    }

    if (event.type === 'error') {
      setRuntimeStatus('error')
      setRuntimeError(event.content ?? 'Chat stream failed.')
    }

    if (event.type === 'done') {
      setRuntimeStatus('done')
    }
  }

  async function sendMessage() {
    const trimmedInput = input.trim()

    if (!trimmedInput || !selectedApplication || disabledReason || runtimeStatus === 'streaming') {
      return
    }

    const controller = new AbortController()
    abortControllerRef.current = controller
    const assistantMessageId = nextId('assistant')

    setInput('')
    setRuntimeError(null)
    setRuntimeEvents([])
    setRuntimeStatus('streaming')
    setMessages((current) => [
      ...current,
      { content: trimmedInput, id: nextId('user'), role: 'user' },
      { content: '', id: assistantMessageId, role: 'assistant' },
    ])

    try {
      await streamChat(
        {
          agentMode,
          applicationId: selectedApplication.applicationId,
          clientRequestId: nextId('web'),
          conversationId: agentMode === 'agent' ? conversationId ?? undefined : undefined,
          message: agentMode === 'agent' ? trimmedInput : undefined,
          messages:
            agentMode === 'none'
              ? [
                  {
                    content: trimmedInput,
                    role: 'user',
                  },
                ]
              : undefined,
          profileId: agentMode === 'agent' ? selectedProfile?.profileId : undefined,
          modelConfigId: agentMode === 'none' ? selectedModelConfig?.modelConfigId : undefined,
          stream: true,
        },
        {
          onEvent: (event) => handleEvent(event, assistantMessageId),
          signal: controller.signal,
        },
      )

      setRuntimeStatus((current) => (current === 'streaming' ? 'done' : current))
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        setRuntimeStatus('idle')
        setRuntimeError('Chat request was stopped.')
        return
      }
      setRuntimeStatus('error')
      setRuntimeError(getErrorMessage(error))
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantMessageId && message.content.length === 0
            ? { ...message, content: 'The stream failed before an assistant message was returned.' }
            : message,
        ),
      )
    } finally {
      abortControllerRef.current = null
    }
  }

  function stopStreaming() {
    abortControllerRef.current?.abort()
  }

  useEffect(() => {
    let isMounted = true

    async function initialize() {
      const nextState = await fetchResources()

      if (isMounted) {
        setState(nextState)

        if (nextState.status === 'ready') {
          setSelectedApplicationId(nextState.applications.records[0]?.applicationId ?? null)
          setSelectedModelConfigId(nextState.modelConfigs[0]?.modelConfigId ?? null)
          setSelectedProfileId(nextState.profiles[0]?.profileId ?? null)
        }
      }
    }

    void initialize()

    return () => {
      isMounted = false
      abortControllerRef.current?.abort()
    }
  }, [])

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">Agent Chat</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">
            Browser calls enter Web first, then Gateway applies trace, token quota, sensitive data
            checks, and forwards the run to core.
          </p>
        </div>
        <Button onClick={() => loadResources(selectedApplicationId)} variant="secondary">
          <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          Refresh resources
        </Button>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTitle>Chat resources unavailable</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Run setup</CardTitle>
          <CardDescription>Use Direct model when no Profile exists yet.</CardDescription>
        </CardHeader>
        <CardContent>
          {state.status === 'loading' ? (
            <div className="grid gap-4 md:grid-cols-4">
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <div className="space-y-2">
                <Label>Application</Label>
                <Select
                  onValueChange={handleApplicationChange}
                  value={selectedApplication?.applicationId ? String(selectedApplication.applicationId) : undefined}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select application" />
                  </SelectTrigger>
                  <SelectContent>
                    {state.applications?.records.map((application) => (
                      <SelectItem key={application.applicationId} value={String(application.applicationId)}>
                        {application.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Mode</Label>
                <Select
                  onValueChange={(value) => {
                    setAgentMode(value as AgentMode)
                    setConversationId(null)
                    setMessages([])
                  }}
                  value={agentMode}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(AGENT_MODE_LABELS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Profile</Label>
                <Select
                  disabled={state.profiles.length === 0 || agentMode === 'none'}
                  onValueChange={(value) => {
                    setSelectedProfileId(Number(value))
                    setConversationId(null)
                    setMessages([])
                  }}
                  value={selectedProfile?.profileId ? String(selectedProfile.profileId) : undefined}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={state.profiles.length === 0 ? 'No profile' : 'Select profile'} />
                  </SelectTrigger>
                  <SelectContent>
                    {state.profiles.map((profile) => (
                      <SelectItem key={profile.profileId} value={String(profile.profileId)}>
                        {profile.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Model config</Label>
                <Select
                  disabled={state.modelConfigs.length === 0 || agentMode === 'agent'}
                  onValueChange={(value) => {
                    setSelectedModelConfigId(Number(value))
                    setConversationId(null)
                    setMessages([])
                  }}
                  value={selectedModelConfig?.modelConfigId ? String(selectedModelConfig.modelConfigId) : undefined}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={state.modelConfigs.length === 0 ? 'No model' : 'Select model'} />
                  </SelectTrigger>
                  <SelectContent>
                    {state.modelConfigs.map((modelConfig) => (
                      <SelectItem key={modelConfig.modelConfigId} value={String(modelConfig.modelConfigId)}>
                        {modelConfig.displayName || modelConfig.modelName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {!selectedApplication && state.status === 'ready' ? (
        <Alert>
          <Bot className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
          <AlertTitle>No application</AlertTitle>
          <AlertDescription>Create an Application first. Chat requests need application scope.</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <ConversationPanel
          disabledReason={disabledReason}
          input={input}
          messages={messages}
          onInputChange={setInput}
          onStop={stopStreaming}
          onSubmit={sendMessage}
          status={runtimeStatus}
        />
        <RuntimeDetailPanel error={runtimeError} events={runtimeEvents} status={runtimeStatus} />
      </div>
    </section>
  )
}
