import { useEffect, useMemo, useRef, useState } from 'react'
import { Bot, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import type { Application, ModelConfig, PageResult, Profile } from '@/lib/api/types'
import { resolveSelectedApplicationId } from '@/features/applications/application-selection-utils'
import { listApplications } from '@/features/applications/api'
import { ApiError } from '@/lib/api/errors'
import { loadLastSelectedApplicationId, saveLastSelectedApplicationId } from '@/lib/application-selection-storage'
import { listModelConfigs } from '@/lib/api/model-configs'
import { listProfiles } from '@/lib/api/profiles'
import type { ConversationSummary } from '@/lib/api/types'
import { streamChat } from './api'
import { ChatHistoryPanel } from './ChatHistoryPanel'
import { conversationMessagesToChatMessages } from './chat-history-utils'
import { isRunnableProfile, selectRunnableProfileId } from './chat-profile-selection-utils'
import { clearStoredChatSession, loadStoredChatSession, saveStoredChatSession } from './chat-session-storage'
import { nextConversationId } from './chat-session-utils'
import { ConversationPanel } from './ConversationPanel'
import { listConversationMessages, listConversations } from './history-api'
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

function clearCurrentChatSession() {
  clearStoredChatSession()
  return {
    conversationId: null,
    messages: [],
  }
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

async function fetchConversationHistory(
  applicationId?: number | null,
  profileId?: number | null,
  mode: AgentMode = 'agent',
) {
  if (!applicationId || !profileId || mode === 'none') {
    return []
  }

  return listConversations(applicationId, profileId, 20)
}

export function ChatPage() {
  const abortControllerRef = useRef<AbortController | null>(null)
  const loadRequestRef = useRef(0)
  const [agentMode, setAgentMode] = useState<AgentMode>('agent')
  const [input, setInput] = useState('')
  const [conversationId, setConversationId] = useState<number | null>(null)
  const [conversationHistory, setConversationHistory] = useState<ConversationSummary[]>([])
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [historyStatus, setHistoryStatus] = useState<'idle' | 'loading'>('idle')
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
    return (
      state.profiles.find(
        (profile) => profile.profileId === selectedProfileId && isRunnableProfile(profile),
      ) ?? null
    )
  }, [selectedProfileId, state.profiles])

  const runnableProfiles = useMemo(() => state.profiles.filter(isRunnableProfile), [state.profiles])

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
      const effectiveApplicationId = resolveSelectedApplicationId(
        applications,
        applicationId ?? null,
        loadLastSelectedApplicationId(),
      )
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
    const requestId = ++loadRequestRef.current
    setState((current) => ({ ...current, applications: null, error: null, status: 'loading' }))
    const nextState = await fetchResources(applicationId)

    if (requestId !== loadRequestRef.current) {
      return
    }

    setState(nextState)

    if (nextState.status === 'ready') {
      const nextApplicationId = resolveSelectedApplicationId(
        nextState.applications,
        applicationId ?? null,
        loadLastSelectedApplicationId(),
      )
      const nextProfileId = selectRunnableProfileId(nextState.profiles, selectedProfileId)
      setSelectedApplicationId(nextApplicationId)
      setSelectedModelConfigId((current) => current ?? nextState.modelConfigs[0]?.modelConfigId ?? null)
      setSelectedProfileId(nextProfileId)
      await loadConversationHistory(nextApplicationId, nextProfileId)
    }
  }

  async function loadConversationHistory(applicationId?: number | null, profileId?: number | null) {
    setHistoryStatus('loading')
    setHistoryError(null)

    try {
      setConversationHistory(await fetchConversationHistory(applicationId, profileId, agentMode))
    } catch (error) {
      setHistoryError(getErrorMessage(error))
    } finally {
      setHistoryStatus('idle')
    }
  }

  async function handleApplicationChange(value: string) {
    const applicationId = Number(value)
    clearStoredChatSession()
    saveLastSelectedApplicationId(applicationId)
    setSelectedApplicationId(applicationId)
    setSelectedProfileId(null)
    setConversationId(null)
    setMessages([])
    await loadResources(applicationId)
  }

  async function handleConversationSelect(conversation: ConversationSummary) {
    if (!conversation.applicationId || !conversation.profileId) {
      return
    }

    setRuntimeError(null)
    setRuntimeEvents([])
    setRuntimeStatus('idle')
    setConversationId(conversation.conversationId)
    setSelectedApplicationId(conversation.applicationId)
    setSelectedProfileId(conversation.profileId)

    try {
      const historyMessages = await listConversationMessages(
        conversation.conversationId,
        conversation.applicationId,
        conversation.profileId,
        50,
      )
      setMessages(conversationMessagesToChatMessages(historyMessages))
    } catch (error) {
      setRuntimeError(getErrorMessage(error))
      setRuntimeStatus('error')
    }
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
      await loadConversationHistory(selectedApplication.applicationId, selectedProfile?.profileId)
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
      const storedSession = loadStoredChatSession()
      const preferredApplicationId = loadLastSelectedApplicationId() ?? storedSession?.applicationId ?? null
      const shouldRestoreStoredSession = storedSession?.applicationId === preferredApplicationId
      const nextState = await fetchResources(preferredApplicationId)

      if (isMounted) {
        setState(nextState)

        if (nextState.status === 'ready') {
          const storedApplicationExists = nextState.applications.records.some(
            (application) => application.applicationId === storedSession?.applicationId,
          )
          const storedProfileExists = nextState.profiles.some(
            (profile) => profile.profileId === storedSession?.profileId && isRunnableProfile(profile),
          )
          const storedModelConfigExists = nextState.modelConfigs.some(
            (modelConfig) => modelConfig.modelConfigId === storedSession?.modelConfigId,
          )
          const nextApplicationId = resolveSelectedApplicationId(
            nextState.applications,
            shouldRestoreStoredSession && storedApplicationExists && storedSession
              ? storedSession.applicationId
              : preferredApplicationId,
            preferredApplicationId,
          )
          const nextProfileId =
            shouldRestoreStoredSession && storedProfileExists && storedSession
              ? storedSession.profileId
              : selectRunnableProfileId(nextState.profiles)

          setAgentMode(shouldRestoreStoredSession ? storedSession?.agentMode ?? 'agent' : 'agent')
          setSelectedApplicationId(nextApplicationId)
          setSelectedModelConfigId(
            shouldRestoreStoredSession && storedModelConfigExists && storedSession
              ? storedSession.modelConfigId
              : nextState.modelConfigs[0]?.modelConfigId ?? null,
          )
          setSelectedProfileId(nextProfileId)
          setConversationId(shouldRestoreStoredSession ? storedSession?.conversationId ?? null : null)
          setMessages(shouldRestoreStoredSession ? storedSession?.messages ?? [] : [])
          try {
            setConversationHistory(
              await fetchConversationHistory(
                nextApplicationId,
                shouldRestoreStoredSession && storedSession?.agentMode === 'none' ? null : nextProfileId,
                shouldRestoreStoredSession ? storedSession?.agentMode ?? 'agent' : 'agent',
              ),
            )
          } catch (error) {
            setHistoryError(getErrorMessage(error))
          }
        }
      }
    }

    void initialize()

    return () => {
      isMounted = false
      abortControllerRef.current?.abort()
    }
  }, [])

  useEffect(() => {
    if (state.status !== 'ready') {
      return
    }

    saveStoredChatSession({
      agentMode,
      applicationId: selectedApplicationId,
      conversationId,
      messages,
      modelConfigId: selectedModelConfigId,
      profileId: selectedProfileId,
    })
  }, [
    agentMode,
    conversationId,
    messages,
    selectedApplicationId,
    selectedModelConfigId,
    selectedProfileId,
    state.status,
  ])
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
                  value={selectedApplicationId ? String(selectedApplicationId) : undefined}
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
                    const cleared = clearCurrentChatSession()
                    setConversationId(cleared.conversationId)
                    setConversationHistory([])
                    setMessages(cleared.messages)
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
                  disabled={runnableProfiles.length === 0 || agentMode === 'none'}
                  onValueChange={(value) => {
                    const profileId = Number(value)
                    setSelectedProfileId(profileId)
                    const cleared = clearCurrentChatSession()
                    setConversationId(cleared.conversationId)
                    setMessages(cleared.messages)
                    void loadConversationHistory(selectedApplicationId, profileId)
                  }}
                  value={selectedProfile?.profileId ? String(selectedProfile.profileId) : undefined}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={runnableProfiles.length === 0 ? 'No runnable profile' : 'Select profile'} />
                  </SelectTrigger>
                  <SelectContent>
                    {runnableProfiles.map((profile) => (
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
                    const cleared = clearCurrentChatSession()
                    setConversationId(cleared.conversationId)
                    setMessages(cleared.messages)
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

      <div className="grid gap-6 2xl:grid-cols-[240px_minmax(720px,1fr)_320px]">
        <ChatHistoryPanel
          conversations={conversationHistory}
          disabled={!selectedApplicationId || !selectedProfileId || agentMode === 'none'}
          error={historyError}
          isLoading={historyStatus === 'loading'}
          onRefresh={() => loadConversationHistory(selectedApplicationId, selectedProfileId)}
          onSelect={handleConversationSelect}
          selectedConversationId={conversationId}
        />
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
