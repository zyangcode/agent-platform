import { useEffect, useMemo, useState } from 'react'
import {
  Archive,
  BrainCircuit,
  DatabaseZap,
  FileSearch,
  PackagePlus,
  PlugZap,
  RefreshCw,
  Search,
  ServerCog,
  Upload,
} from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { FilterSelect } from '@/components/ui/filter-select'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { listApplications } from '@/features/applications/api'
import { listProfiles } from '@/lib/api/profiles'
import { ApiError } from '@/lib/api/errors'
import type {
  Application,
  ExperienceSkill,
  MemoryRecord,
  McpServer,
  McpTool,
  Profile,
  RagSearchResult,
  Skill,
} from '@/lib/api/types'
import { loadLastSelectedApplicationId, saveLastSelectedApplicationId } from '@/lib/application-selection-storage'
import { useI18n } from '@/lib/i18n/use-i18n'
import { loadLastSelectedProfileId, saveLastSelectedProfileId } from '@/lib/profile-selection-storage'
import {
  createRagDocument,
  createExperienceSkill,
  createMcpServer,
  deleteRagDocument,
  disableExperienceSkill,
  disableMemory,
  disableMcpServer,
  listMemories,
  listExperienceSkills,
  listMcpServers,
  listMcpTools,
  listSkills,
  refreshMcpServerTools,
  searchRagDocuments,
  updateMemory,
  uploadJarSkill,
  type McpServerStatusFilter,
  type McpStatusFilter,
  type SkillScopeFilter,
  type SkillStatusFilter,
} from './api'
import { ExperienceSkillTable } from './ExperienceSkillTable'
import { McpServerTable } from './McpServerTable'
import { McpToolTable } from './McpToolTable'
import {
  MCP_SERVER_PRESETS,
  MCP_SERVER_TYPE_OPTIONS,
  mcpServerPresetToForm,
  type McpServerType,
} from './mcp-presets'
import { selectMemoryProfileId } from './memory-selection-utils'
import { clampMemoryImportance, formatMemoryTimestamp, parseMemoryTags } from './memory-ui'
import { SkillTable } from './SkillTable'
import { DEFAULT_MCP_SERVER_STATUS } from './tool-defaults'
import { filterToolsBySearch } from './tool-filters'

type LoadStatus = 'error' | 'loading' | 'ready'
type ActiveTab = 'experience' | 'knowledge' | 'mcp' | 'memory' | 'skills'

type ToolsState = {
  applications: Application[]
  error: string | null
  experienceSkills: ExperienceSkill[]
  mcpServers: McpServer[]
  mcpTools: McpTool[]
  skills: Skill[]
  status: LoadStatus
}

const SKILL_SCOPE_OPTIONS: Array<{ label: string; value: SkillScopeFilter | 'ALL' }> = [
  { label: 'All scopes', value: 'ALL' },
  { label: 'Global', value: 'GLOBAL' },
  { label: 'Profile', value: 'PROFILE' },
  { label: 'Personal', value: 'PERSONAL' },
]

const SKILL_STATUS_OPTIONS: Array<{ label: string; value: SkillStatusFilter | 'ALL' }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Enabled', value: 'ENABLED' },
  { label: 'Loaded', value: 'LOADED' },
  { label: 'Validating', value: 'VALIDATING' },
  { label: 'Uploaded', value: 'UPLOADED' },
  { label: 'Disabled', value: 'DISABLED' },
  { label: 'Failed', value: 'FAILED' },
]

const MCP_STATUS_OPTIONS: Array<{ label: string; value: McpStatusFilter | 'ALL' }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Available', value: 'AVAILABLE' },
  { label: 'Unavailable', value: 'UNAVAILABLE' },
]

const MCP_SERVER_STATUS_OPTIONS: Array<{ label: string; value: McpServerStatusFilter | 'ALL' }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Disabled', value: 'DISABLED' },
  { label: 'Unavailable', value: 'UNAVAILABLE' },
]

const MEMORY_CATEGORY_OPTIONS: Array<{ label: string; value: string }> = [
  { label: 'All categories', value: 'ALL' },
  { label: 'Summary', value: 'summary' },
  { label: 'Preference', value: 'preference' },
  { label: 'Fact', value: 'fact' },
  { label: 'Episodic', value: 'episodic' },
  { label: 'Tool failure', value: 'tool_failure' },
  { label: 'Policy', value: 'policy' },
  { label: 'General', value: 'general' },
]

const TAB_OPTIONS: Array<{ icon: typeof PackagePlus; labelKey: string; value: ActiveTab }> = [
  { icon: PackagePlus, labelKey: 'tools.executableSkills', value: 'skills' },
  { icon: BrainCircuit, labelKey: 'tools.experienceSkills', value: 'experience' },
  { icon: PlugZap, labelKey: 'tools.mcp', value: 'mcp' },
  { icon: Archive, labelKey: 'tools.longTermMemory', value: 'memory' },
  { icon: DatabaseZap, labelKey: 'tools.knowledgeBase', value: 'knowledge' },
]

function normalizeAllFilter<TValue extends string>(value: TValue | 'ALL') {
  return value === 'ALL' ? '' : value
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.status ? `[${error.status}] ${error.message}` : error.message
  }
  return error instanceof Error ? `${fallback}: ${error.message}` : fallback
}

function initialState(): ToolsState {
  return {
    applications: [],
    error: null,
    experienceSkills: [],
    mcpServers: [],
    mcpTools: [],
    skills: [],
    status: 'loading',
  }
}

export function ToolsPage() {
  const { t } = useI18n()
  const [activeTab, setActiveTab] = useState<ActiveTab>('skills')
  const [experienceForm, setExperienceForm] = useState({
    code: '',
    content: '',
    domain: '',
    name: '',
    profileId: '',
    triggerKeywords: '',
  })
  const [jarFile, setJarFile] = useState<File | null>(null)
  const [jarManifest, setJarManifest] = useState('{\n  "code": "demo-skill",\n  "name": "Demo Skill",\n  "version": "1.0.0"\n}')
  const [jarScope, setJarScope] = useState<SkillScopeFilter>('PERSONAL')
  const [mcpServerForm, setMcpServerForm] = useState(() => mcpServerPresetToForm(MCP_SERVER_PRESETS[0]))
  const [mcpServerStatus, setMcpServerStatus] = useState<McpServerStatusFilter | 'ALL'>(DEFAULT_MCP_SERVER_STATUS)
  const [mcpStatus, setMcpStatus] = useState<McpStatusFilter | 'ALL'>('ALL')
  const [message, setMessage] = useState<string | null>(null)
  const [messageVariant, setMessageVariant] = useState<'default' | 'danger'>('default')
  const [memories, setMemories] = useState<MemoryRecord[]>([])
  const [memoryProfiles, setMemoryProfiles] = useState<Profile[]>([])
  const [editingMemoryId, setEditingMemoryId] = useState<number | null>(null)
  const [memoryEditForm, setMemoryEditForm] = useState({
    content: '',
    importance: '0.5',
    memoryCategory: 'general',
    slotHint: '',
    tags: '',
  })
  const [memoryFilters, setMemoryFilters] = useState({
    category: 'ALL',
    limit: '30',
    profileId: '',
    query: '',
  })
  const [ragDocumentForm, setRagDocumentForm] = useState({
    chunkTokenBudget: '400',
    content: '',
    overlapTokens: '40',
    profileId: '',
    sourceType: 'MANUAL',
    sourceUri: '',
    title: '',
  })
  const [ragQuery, setRagQuery] = useState('')
  const [ragResults, setRagResults] = useState<RagSearchResult[]>([])
  const [ragTopK, setRagTopK] = useState('5')
  const [search, setSearch] = useState('')
  const [selectedApplicationId, setSelectedApplicationId] = useState('')
  const [skillScope, setSkillScope] = useState<SkillScopeFilter | 'ALL'>('ALL')
  const [skillStatus, setSkillStatus] = useState<SkillStatusFilter | 'ALL'>('ALL')
  const [state, setState] = useState<ToolsState>(initialState)
  const [submitting, setSubmitting] = useState(false)

  const selectedApplication = selectedApplicationId ? Number(selectedApplicationId) : null

  function handleSelectedApplicationChange(value: string) {
    const applicationId = Number(value)
    setSelectedApplicationId(value)
    saveLastSelectedApplicationId(Number.isFinite(applicationId) ? applicationId : null)
    setMemories([])
    setMemoryFilters((current) => ({ ...current, profileId: '' }))
  }

  function handleMemoryProfileChange(value: string) {
    const profileId = value === 'ALL' ? '' : value
    setMemoryFilters((current) => ({ ...current, profileId }))

    if (selectedApplication && profileId) {
      saveLastSelectedProfileId(selectedApplication, Number(profileId))
    }
  }

  const filteredSkills = useMemo(() => {
    return filterToolsBySearch(
      state.skills
        .filter((skill) => skill.skillType !== 'BUILTIN')
        .map((skill) => ({
          ...skill,
          key: skill.code,
        })),
      search,
    )
  }, [search, state.skills])

  const filteredExperienceSkills = useMemo(() => {
    return filterToolsBySearch(
      state.experienceSkills.map((skill) => ({
        ...skill,
        description: skill.content,
        key: skill.code,
        status: 'ACTIVE',
      })),
      search,
    )
  }, [search, state.experienceSkills])

  const filteredMcpServers = useMemo(() => {
    return filterToolsBySearch(
      state.mcpServers.map((server) => ({
        ...server,
        description: server.serverType,
        key: String(server.mcpServerId),
      })),
      search,
    )
  }, [search, state.mcpServers])

  const filteredMcpTools = useMemo(() => {
    return filterToolsBySearch(
      state.mcpTools.map((tool) => ({
        ...tool,
        key: tool.name,
      })),
      search,
    )
  }, [search, state.mcpTools])

  async function loadTools() {
    setState((current) => ({ ...current, error: null, status: 'loading' }))
    try {
      const [applicationsPage, skills, mcpTools, mcpServers, experienceSkillsPage] = await Promise.all([
        listApplications(1, 100),
        listSkills(
          normalizeAllFilter(skillScope) as SkillScopeFilter,
          normalizeAllFilter(skillStatus) as SkillStatusFilter,
        ),
        listMcpTools(normalizeAllFilter(mcpStatus) as McpStatusFilter),
        listMcpServers(normalizeAllFilter(mcpServerStatus) as McpServerStatusFilter),
        selectedApplication
          ? listExperienceSkills(selectedApplication, 1, 50)
          : Promise.resolve({ records: [] }),
      ])

      setState({
        applications: applicationsPage.records,
        error: null,
        experienceSkills: experienceSkillsPage.records,
        mcpServers,
        mcpTools,
        skills,
        status: 'ready',
      })

      if (!selectedApplication && applicationsPage.records[0]) {
        const applicationId = applicationsPage.records[0].applicationId
        setSelectedApplicationId(String(applicationId))
        saveLastSelectedApplicationId(applicationId)
      }
    } catch (error) {
      setState((current) => ({
        ...current,
        error: getErrorMessage(error, t('tools.loadFailed')),
        status: 'error',
      }))
    }
  }

  useEffect(() => {
    let isMounted = true

    async function initializeTools() {
      try {
        const applicationsPage = await listApplications(1, 100)
        const storedApplicationId = loadLastSelectedApplicationId()
        const nextApplicationId =
          selectedApplicationId ||
          String(
            applicationsPage.records.some((application) => application.applicationId === storedApplicationId)
              ? storedApplicationId
              : applicationsPage.records[0]?.applicationId ?? '',
          )
        const nextApplication = nextApplicationId ? Number(nextApplicationId) : null
        const [skills, mcpTools, mcpServers, experienceSkillsPage] = await Promise.all([
          listSkills(
            normalizeAllFilter(skillScope) as SkillScopeFilter,
            normalizeAllFilter(skillStatus) as SkillStatusFilter,
          ),
          listMcpTools(normalizeAllFilter(mcpStatus) as McpStatusFilter),
          listMcpServers(normalizeAllFilter(mcpServerStatus) as McpServerStatusFilter),
          nextApplication
            ? listExperienceSkills(nextApplication, 1, 50)
            : Promise.resolve({ records: [] }),
        ])

        if (isMounted) {
          setSelectedApplicationId(nextApplicationId)
          if (nextApplication) {
            saveLastSelectedApplicationId(nextApplication)
          }
          setState({
            applications: applicationsPage.records,
            error: null,
            experienceSkills: experienceSkillsPage.records,
            mcpServers,
            mcpTools,
            skills,
            status: 'ready',
          })
        }
      } catch (error) {
        if (isMounted) {
          setState((current) => ({
            ...current,
            error: getErrorMessage(error, t('tools.loadFailed')),
            status: 'error',
          }))
        }
      }
    }

    void initializeTools()

    return () => {
      isMounted = false
    }
  }, [mcpServerStatus, mcpStatus, selectedApplicationId, skillScope, skillStatus, t])

  useEffect(() => {
    let isMounted = true

    async function loadApplicationProfiles(applicationId: number) {
      try {
        const profilesPage = await listProfiles(applicationId, 1, 100)
        if (!isMounted) {
          return
        }

        setMemoryProfiles(profilesPage.records)
        setMemoryFilters((current) => {
          const preferredProfileId = current.profileId
            ? Number(current.profileId)
            : loadLastSelectedProfileId(applicationId)
          const nextProfileId = selectMemoryProfileId(profilesPage.records, preferredProfileId)

          if (nextProfileId) {
            saveLastSelectedProfileId(applicationId, nextProfileId)
          }

          return {
            ...current,
            profileId: nextProfileId ? String(nextProfileId) : '',
          }
        })
      } catch {
        if (isMounted) {
          setMemoryProfiles([])
          setMemoryFilters((current) => ({ ...current, profileId: '' }))
        }
      }
    }

    if (selectedApplication) {
      void loadApplicationProfiles(selectedApplication)
    } else {
      setMemoryProfiles([])
      setMemoryFilters((current) => ({ ...current, profileId: '' }))
    }

    return () => {
      isMounted = false
    }
  }, [selectedApplication])

  async function handleJarUpload() {
    if (!jarFile) {
      setMessage(t('tools.jarRequired'))
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await uploadJarSkill(jarScope, jarFile, jarManifest)
      setMessage(t('tools.uploadSuccess'))
      await loadTools()
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.uploadFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCreateExperienceSkill() {
    if (!selectedApplication) {
      setMessage(t('tools.applicationRequired'))
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await createExperienceSkill({
        applicationId: selectedApplication,
        code: experienceForm.code,
        content: experienceForm.content,
        domain: experienceForm.domain,
        name: experienceForm.name,
        profileId: experienceForm.profileId ? Number(experienceForm.profileId) : null,
        triggerKeywords: experienceForm.triggerKeywords
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean),
      })
      setExperienceForm({ code: '', content: '', domain: '', name: '', profileId: '', triggerKeywords: '' })
      setMessage(t('tools.experienceCreateSuccess'))
      await loadTools()
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.experienceCreateFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDisableExperienceSkill(experienceSkillId: number) {
    if (!selectedApplication) {
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await disableExperienceSkill(experienceSkillId, selectedApplication)
      setMessage(t('tools.experienceDisableSuccess'))
      await loadTools()
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.experienceDisableFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCreateMcpServer() {
    setSubmitting(true)
    setMessage(null)
    try {
      await createMcpServer({
        connectionConfig: JSON.parse(mcpServerForm.connectionConfig),
        name: mcpServerForm.name,
        serverType: mcpServerForm.serverType,
      })
      setMessage(t('tools.mcpServerCreateSuccess'))
      await loadTools()
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.mcpServerCreateFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCreateRagDocument() {
    if (!selectedApplication) {
      setMessage(t('tools.applicationRequired'))
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      const result = await createRagDocument({
        applicationId: selectedApplication,
        chunkTokenBudget: ragDocumentForm.chunkTokenBudget ? Number(ragDocumentForm.chunkTokenBudget) : null,
        content: ragDocumentForm.content,
        overlapTokens: ragDocumentForm.overlapTokens ? Number(ragDocumentForm.overlapTokens) : null,
        profileId: ragDocumentForm.profileId ? Number(ragDocumentForm.profileId) : null,
        sourceType: ragDocumentForm.sourceType,
        sourceUri: ragDocumentForm.sourceUri,
        title: ragDocumentForm.title,
      })
      setRagDocumentForm((current) => ({ ...current, content: '', sourceUri: '', title: '' }))
      setMessageVariant('default')
      setMessage(t('tools.ragIngestSuccess', { chunkCount: result.chunkCount, documentId: result.documentId }))
    } catch (error) {
      setMessageVariant('danger')
      setMessage(getErrorMessage(error, t('tools.ragIngestFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSearchRagDocuments() {
    if (!selectedApplication) {
      setMessage(t('tools.applicationRequired'))
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      const results = await searchRagDocuments({
        applicationId: selectedApplication,
        profileId: ragDocumentForm.profileId ? Number(ragDocumentForm.profileId) : null,
        query: ragQuery,
        topK: ragTopK ? Number(ragTopK) : 5,
      })
      setRagResults(results)
      setMessage(t('tools.ragSearchSuccess', { count: results.length }))
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.ragSearchFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDeleteRagDocument(documentId: number) {
    if (!selectedApplication) {
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await deleteRagDocument(
        documentId,
        selectedApplication,
        ragDocumentForm.profileId ? Number(ragDocumentForm.profileId) : null,
      )
      setRagResults((current) => current.filter((item) => item.documentId !== documentId))
      setMessage(t('tools.ragDeleteSuccess'))
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.ragDeleteFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleListMemories() {
    if (!selectedApplication) {
      setMessage(t('tools.applicationRequired'))
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      const results = await listMemories({
        applicationId: selectedApplication,
        category: memoryFilters.category === 'ALL' ? null : memoryFilters.category,
        limit: memoryFilters.limit ? Number(memoryFilters.limit) : 30,
        profileId: memoryFilters.profileId ? Number(memoryFilters.profileId) : null,
        query: memoryFilters.query,
      })
      setMemories(results)
      setEditingMemoryId(null)
      setMessage(t('tools.memoryLoadSuccess', { count: results.length }))
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.memoryLoadFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  function handleEditMemory(memory: MemoryRecord) {
    setEditingMemoryId(memory.id)
    setMemoryEditForm({
      content: memory.content,
      importance: String(memory.importance ?? 0.5),
      memoryCategory: memory.memoryCategory ?? 'general',
      slotHint: memory.slotHint ?? '',
      tags: (memory.tags ?? []).join(', '),
    })
  }

  async function handleSaveMemory(memory: MemoryRecord) {
    if (!selectedApplication) {
      setMessage(t('tools.applicationRequired'))
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      const updated = await updateMemory(memory.id, {
        applicationId: selectedApplication,
        content: memoryEditForm.content,
        importance: clampMemoryImportance(memoryEditForm.importance),
        memoryCategory: memoryEditForm.memoryCategory,
        profileId: memory.profileId ?? (memoryFilters.profileId ? Number(memoryFilters.profileId) : null),
        slotHint: memoryEditForm.slotHint,
        tags: parseMemoryTags(memoryEditForm.tags),
      })
      setMemories((current) => current.map((item) => (item.id === memory.id ? updated : item)))
      setEditingMemoryId(null)
      setMessage(t('tools.memoryUpdateSuccess'))
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.memoryUpdateFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDisableMemory(memory: MemoryRecord) {
    if (!selectedApplication) {
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await disableMemory(
        memory.id,
        selectedApplication,
        memory.profileId ?? (memoryFilters.profileId ? Number(memoryFilters.profileId) : null),
      )
      setMemories((current) => current.filter((item) => item.id !== memory.id))
      setEditingMemoryId(null)
      setMessage(t('tools.memoryDisableSuccess'))
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.memoryDisableFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDisableMcpServer(mcpServerId: number) {
    setSubmitting(true)
    setMessage(null)
    try {
      await disableMcpServer(mcpServerId)
      setMessage(t('tools.mcpServerDisableSuccess'))
      await loadTools()
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.mcpServerDisableFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRefreshMcpServerTools(mcpServerId: number) {
    setSubmitting(true)
    setMessage(null)
    try {
      await refreshMcpServerTools(mcpServerId)
      setMessage(t('tools.mcpServerRefreshSuccess'))
      await loadTools()
    } catch (error) {
      setMessage(getErrorMessage(error, t('tools.mcpServerRefreshFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">{t('tools.title')}</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">{t('tools.intro')}</p>
        </div>
        <Button onClick={loadTools} variant="secondary">
          <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
          {t('common.refresh')}
        </Button>
      </div>

      {state.error ? (
        <Alert variant="danger">
          <AlertTitle>{t('tools.unavailable')}</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      {message ? (
        <Alert variant={messageVariant}>
          <AlertTitle>{messageVariant === 'danger' ? t('tools.operationFailed') : t('tools.operationResult')}</AlertTitle>
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {TAB_OPTIONS.map((tab) => {
          const Icon = tab.icon
          return (
            <Button
              key={tab.value}
              onClick={() => setActiveTab(tab.value)}
              variant={activeTab === tab.value ? 'primary' : 'secondary'}
            >
              <Icon className="h-4 w-4" strokeWidth={1.75} />
              {t(tab.labelKey)}
            </Button>
          )
        })}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('tools.catalogFilters')}</CardTitle>
          <CardDescription>{t('tools.catalogFiltersDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1.3fr)_repeat(4,minmax(0,1fr))]">
            <div className="space-y-2">
              <Label htmlFor="tool-search">{t('tools.search')}</Label>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-600" />
                <Input
                  className="pl-9"
                  id="tool-search"
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={t('tools.searchPlaceholder')}
                  value={search}
                />
              </div>
            </div>

            <FilterSelect
              label={t('tools.skillScope')}
              onChange={(value) => setSkillScope(value as SkillScopeFilter | 'ALL')}
              options={SKILL_SCOPE_OPTIONS}
              value={skillScope}
            />

            <FilterSelect
              label={t('tools.skillStatus')}
              onChange={(value) => setSkillStatus(value as SkillStatusFilter | 'ALL')}
              options={SKILL_STATUS_OPTIONS}
              value={skillStatus}
            />

            <FilterSelect
              label={t('tools.mcpStatus')}
              onChange={(value) => setMcpStatus(value as McpStatusFilter | 'ALL')}
              options={MCP_STATUS_OPTIONS}
              value={mcpStatus}
            />

            <FilterSelect
              label={t('tools.mcpServerStatus')}
              onChange={(value) => setMcpServerStatus(value as McpServerStatusFilter | 'ALL')}
              options={MCP_SERVER_STATUS_OPTIONS}
              value={mcpServerStatus}
            />
          </div>
        </CardContent>
      </Card>

      {activeTab === 'skills' ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(340px,420px)_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>{t('tools.uploadJarSkill')}</CardTitle>
              <CardDescription>{t('tools.uploadJarSkillDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <FilterSelect
                label={t('tools.skillScope')}
                onChange={(value) => setJarScope(value as SkillScopeFilter)}
                options={SKILL_SCOPE_OPTIONS.filter((option) => option.value !== 'ALL')}
                value={jarScope}
              />
              <div className="space-y-2">
                <Label htmlFor="jar-file">{t('tools.jarFile')}</Label>
                <Input
                  id="jar-file"
                  onChange={(event) => setJarFile(event.target.files?.[0] ?? null)}
                  type="file"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="jar-manifest">{t('tools.manifestJson')}</Label>
                <Textarea
                  className="min-h-44 font-mono text-xs"
                  id="jar-manifest"
                  onChange={(event) => setJarManifest(event.target.value)}
                  value={jarManifest}
                />
              </div>
              <Button disabled={submitting} onClick={handleJarUpload}>
                <Upload className="h-4 w-4" strokeWidth={1.75} />
                {t('tools.uploadJar')}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('tools.skills')}</CardTitle>
              <CardDescription>
                {t('tools.visibleLoadedSkills', { visible: filteredSkills.length, total: state.skills.length })}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <SkillTable skills={filteredSkills} status={state.status} />
            </CardContent>
          </Card>
        </div>
      ) : null}

      {activeTab === 'experience' ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(340px,420px)_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>{t('tools.createExperienceSkill')}</CardTitle>
              <CardDescription>{t('tools.createExperienceSkillDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>{t('application.title')}</Label>
                <Select onValueChange={handleSelectedApplicationChange} value={selectedApplicationId}>
                  <SelectTrigger>
                    <SelectValue placeholder={t('application.noApplicationSelected')} />
                  </SelectTrigger>
                  <SelectContent>
                    {state.applications.map((application) => (
                      <SelectItem key={application.applicationId} value={String(application.applicationId)}>
                        {application.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <FieldInput
                id="experience-name"
                label={t('profile.name')}
                onChange={(value) => setExperienceForm((current) => ({ ...current, name: value, code: value.replace(/[^a-zA-Z0-9_-]/g, '-').toLowerCase() }))}
                value={experienceForm.name}
              />
              <FieldInput
                id="experience-domain"
                label={t('tools.domain')}
                onChange={(value) => setExperienceForm((current) => ({ ...current, domain: value }))}
                value={experienceForm.domain}
              />
              <FieldInput
                id="experience-keywords"
                label={t('tools.triggerKeywords')}
                onChange={(value) => setExperienceForm((current) => ({ ...current, triggerKeywords: value }))}
                value={experienceForm.triggerKeywords}
              />
              <div className="space-y-2">
                <Label htmlFor="experience-content">{t('tools.content')}</Label>
                <Textarea
                  id="experience-content"
                  onChange={(event) =>
                    setExperienceForm((current) => ({ ...current, content: event.target.value }))
                  }
                  value={experienceForm.content}
                />
              </div>
              <Button disabled={submitting || !selectedApplication} onClick={handleCreateExperienceSkill}>
                <BrainCircuit className="h-4 w-4" strokeWidth={1.75} />
                {t('tools.createExperienceSkill')}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('tools.experienceSkills')}</CardTitle>
              <CardDescription>
                {t('tools.visibleLoadedExperience', {
                  visible: filteredExperienceSkills.length,
                  total: state.experienceSkills.length,
                })}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ExperienceSkillTable
                onDisable={handleDisableExperienceSkill}
                skills={filteredExperienceSkills}
                status={state.status}
              />
            </CardContent>
          </Card>
        </div>
      ) : null}

      {activeTab === 'knowledge' ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(340px,420px)_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>{t('tools.ragIngestTitle')}</CardTitle>
              <CardDescription>{t('tools.ragIngestDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>{t('application.title')}</Label>
                <Select onValueChange={handleSelectedApplicationChange} value={selectedApplicationId}>
                  <SelectTrigger>
                    <SelectValue placeholder={t('application.noApplicationSelected')} />
                  </SelectTrigger>
                  <SelectContent>
                    {state.applications.map((application) => (
                      <SelectItem key={application.applicationId} value={String(application.applicationId)}>
                        {application.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <FieldInput
                id="rag-title"
                label={t('tools.ragTitle')}
                onChange={(value) => setRagDocumentForm((current) => ({ ...current, title: value }))}
                value={ragDocumentForm.title}
              />
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-1">
                <FieldInput
                  id="rag-source-type"
                  label={t('tools.ragSourceType')}
                  onChange={(value) => setRagDocumentForm((current) => ({ ...current, sourceType: value }))}
                  value={ragDocumentForm.sourceType}
                />
                <FieldInput
                  id="rag-source-uri"
                  label={t('tools.ragSourceUri')}
                  onChange={(value) => setRagDocumentForm((current) => ({ ...current, sourceUri: value }))}
                  value={ragDocumentForm.sourceUri}
                />
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <FieldInput
                  id="rag-chunk-budget"
                  label={t('tools.ragChunkBudget')}
                  onChange={(value) => setRagDocumentForm((current) => ({ ...current, chunkTokenBudget: value }))}
                  type="number"
                  value={ragDocumentForm.chunkTokenBudget}
                />
                <FieldInput
                  id="rag-overlap"
                  label={t('tools.ragOverlap')}
                  onChange={(value) => setRagDocumentForm((current) => ({ ...current, overlapTokens: value }))}
                  type="number"
                  value={ragDocumentForm.overlapTokens}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="rag-content">{t('tools.content')}</Label>
                <Textarea
                  className="min-h-52"
                  id="rag-content"
                  onChange={(event) =>
                    setRagDocumentForm((current) => ({ ...current, content: event.target.value }))
                  }
                  value={ragDocumentForm.content}
                />
              </div>
              <Button disabled={submitting || !selectedApplication} onClick={handleCreateRagDocument}>
                <DatabaseZap className="h-4 w-4" strokeWidth={1.75} />
                {t('tools.ragIngest')}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('tools.ragSearchTitle')}</CardTitle>
              <CardDescription>{t('tools.ragSearchDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_120px_auto]">
                <div className="space-y-2">
                  <Label htmlFor="rag-query">{t('tools.ragQuery')}</Label>
                  <Input
                    id="rag-query"
                    onChange={(event) => setRagQuery(event.target.value)}
                    value={ragQuery}
                  />
                </div>
                <FieldInput
                  id="rag-top-k"
                  label={t('tools.ragTopK')}
                  onChange={setRagTopK}
                  type="number"
                  value={ragTopK}
                />
                <div className="flex items-end">
                  <Button disabled={submitting || !selectedApplication || !ragQuery.trim()} onClick={handleSearchRagDocuments}>
                    <FileSearch className="h-4 w-4" strokeWidth={1.75} />
                    {t('tools.ragSearch')}
                  </Button>
                </div>
              </div>
              <RagSearchResults onDelete={handleDeleteRagDocument} results={ragResults} submitting={submitting} />
            </CardContent>
          </Card>
        </div>
      ) : null}

      {activeTab === 'memory' ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(340px,420px)_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>{t('tools.memoryFiltersTitle')}</CardTitle>
              <CardDescription>{t('tools.memoryFiltersDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>{t('application.title')}</Label>
                <Select onValueChange={handleSelectedApplicationChange} value={selectedApplicationId}>
                  <SelectTrigger>
                    <SelectValue placeholder={t('application.noApplicationSelected')} />
                  </SelectTrigger>
                  <SelectContent>
                    {state.applications.map((application) => (
                      <SelectItem key={application.applicationId} value={String(application.applicationId)}>
                        {application.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t('tools.profileIdOptional')}</Label>
                <Select onValueChange={handleMemoryProfileChange} value={memoryFilters.profileId || 'ALL'}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">{t('common.allProfiles')}</SelectItem>
                    {memoryProfiles.map((profile) => (
                      <SelectItem key={profile.profileId} value={String(profile.profileId)}>
                        #{profile.profileId} {profile.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <FilterSelect
                label={t('tools.memoryCategory')}
                onChange={(value) => setMemoryFilters((current) => ({ ...current, category: value }))}
                options={MEMORY_CATEGORY_OPTIONS}
                value={memoryFilters.category}
              />
              <FieldInput
                id="memory-query"
                label={t('tools.memoryQuery')}
                onChange={(value) => setMemoryFilters((current) => ({ ...current, query: value }))}
                value={memoryFilters.query}
              />
              <FieldInput
                id="memory-limit"
                label={t('tools.memoryLimit')}
                onChange={(value) => setMemoryFilters((current) => ({ ...current, limit: value }))}
                type="number"
                value={memoryFilters.limit}
              />
              <Button disabled={submitting || !selectedApplication} onClick={handleListMemories}>
                <Search className="h-4 w-4" strokeWidth={1.75} />
                {t('tools.memorySearch')}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('tools.memoryRecords')}</CardTitle>
              <CardDescription>{t('tools.memoryRecordsDescription', { count: memories.length })}</CardDescription>
            </CardHeader>
            <CardContent>
              <MemoryRecords
                editingMemoryId={editingMemoryId}
                editForm={memoryEditForm}
                memories={memories}
                onDisable={handleDisableMemory}
                onEdit={handleEditMemory}
                onEditFormChange={setMemoryEditForm}
                onSave={handleSaveMemory}
                submitting={submitting}
              />
            </CardContent>
          </Card>
        </div>
      ) : null}

      {activeTab === 'mcp' ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(340px,420px)_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>{t('tools.createMcpServer')}</CardTitle>
              <CardDescription>{t('tools.createMcpServerDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap gap-2">
                {MCP_SERVER_PRESETS.map((preset) => (
                  <Button
                    key={preset.id}
                    onClick={() => setMcpServerForm(mcpServerPresetToForm(preset))}
                    size="sm"
                    variant="secondary"
                  >
                    {preset.label}
                  </Button>
                ))}
              </div>
              <FieldInput
                id="mcp-server-name"
                label={t('profile.name')}
                onChange={(value) => setMcpServerForm((current) => ({ ...current, name: value }))}
                value={mcpServerForm.name}
              />
              <div className="space-y-2">
                <Label>{t('profile.type')}</Label>
                <Select
                  onValueChange={(value) =>
                    setMcpServerForm((current) => ({ ...current, serverType: value as McpServerType }))
                  }
                  value={mcpServerForm.serverType}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MCP_SERVER_TYPE_OPTIONS.map((serverType) => (
                      <SelectItem key={serverType} value={serverType}>
                        {serverType}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="mcp-config">{t('tools.connectionConfig')}</Label>
                <Textarea
                  className="min-h-44 font-mono text-xs"
                  id="mcp-config"
                  onChange={(event) =>
                    setMcpServerForm((current) => ({ ...current, connectionConfig: event.target.value }))
                  }
                  value={mcpServerForm.connectionConfig}
                />
              </div>
              <Button disabled={submitting} onClick={handleCreateMcpServer}>
                <ServerCog className="h-4 w-4" strokeWidth={1.75} />
                {t('tools.createMcpServer')}
              </Button>
            </CardContent>
          </Card>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <CardTitle>{t('tools.mcpServers')}</CardTitle>
                    <CardDescription>
                      {t('tools.visibleLoadedMcpServers', {
                        visible: filteredMcpServers.length,
                        total: state.mcpServers.length,
                      })}
                    </CardDescription>
                  </div>
                  <Badge variant="muted">{t('tools.externalProtocol')}</Badge>
                </div>
              </CardHeader>
              <CardContent>
                <McpServerTable
                  onDisable={handleDisableMcpServer}
                  onRefreshTools={handleRefreshMcpServerTools}
                  servers={filteredMcpServers}
                  status={state.status}
                  submitting={submitting}
                />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{t('tools.mcpTools')}</CardTitle>
                <CardDescription>
                  {t('tools.visibleLoadedMcp', { visible: filteredMcpTools.length, total: state.mcpTools.length })}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <McpToolTable tools={filteredMcpTools} status={state.status} />
              </CardContent>
            </Card>
          </div>
        </div>
      ) : null}
    </section>
  )
}

function FieldInput({
  id,
  label,
  onChange,
  type = 'text',
  value,
}: {
  id: string
  label: string
  onChange: (value: string) => void
  type?: string
  value: string
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} onChange={(event) => onChange(event.target.value)} type={type} value={value} />
    </div>
  )
}

function RagSearchResults({
  onDelete,
  results,
  submitting,
}: {
  onDelete: (documentId: number) => void
  results: RagSearchResult[]
  submitting: boolean
}) {
  const { t } = useI18n()

  if (results.length === 0) {
    return (
      <Alert>
        <FileSearch className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        <AlertTitle>{t('tools.ragNoResults')}</AlertTitle>
        <AlertDescription>{t('tools.ragNoResultsDescription')}</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="divide-y divide-zinc-800 rounded-lg border border-zinc-800">
      {results.map((result) => (
        <div className="grid gap-4 p-4 lg:grid-cols-[minmax(0,1fr)_auto]" key={`${result.documentId}-${result.chunkId}`}>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <p className="truncate font-medium text-white">{result.title}</p>
              <Badge variant="muted">{result.score.toFixed(3)}</Badge>
            </div>
            <p className="mt-2 line-clamp-3 text-sm leading-6 text-zinc-300">{result.content}</p>
            <div className="mt-3 flex flex-wrap gap-3 font-mono text-xs text-zinc-500">
              <span>doc:{result.documentId}</span>
              <span>chunk:{result.chunkId}</span>
              {result.sourceUri ? <span className="truncate">src:{result.sourceUri}</span> : null}
            </div>
          </div>
          <div className="flex items-start lg:justify-end">
            <Button disabled={submitting} onClick={() => onDelete(result.documentId)} size="sm" variant="danger">
              {t('application.disable')}
            </Button>
          </div>
        </div>
      ))}
    </div>
  )
}

type MemoryEditForm = {
  content: string
  importance: string
  memoryCategory: string
  slotHint: string
  tags: string
}

function MemoryRecords({
  editingMemoryId,
  editForm,
  memories,
  onDisable,
  onEdit,
  onEditFormChange,
  onSave,
  submitting,
}: {
  editingMemoryId: number | null
  editForm: MemoryEditForm
  memories: MemoryRecord[]
  onDisable: (memory: MemoryRecord) => void
  onEdit: (memory: MemoryRecord) => void
  onEditFormChange: (form: MemoryEditForm) => void
  onSave: (memory: MemoryRecord) => void
  submitting: boolean
}) {
  const { t } = useI18n()

  if (memories.length === 0) {
    return (
      <Alert>
        <Archive className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        <AlertTitle>{t('tools.memoryNoResults')}</AlertTitle>
        <AlertDescription>{t('tools.memoryNoResultsDescription')}</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="divide-y divide-zinc-800 rounded-lg border border-zinc-800">
      {memories.map((memory) => {
        const isEditing = editingMemoryId === memory.id
        const tags = memory.tags ?? []

        return (
          <div className="space-y-4 p-4" key={memory.id}>
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto]">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge>{memory.memoryCategory ?? 'general'}</Badge>
                  <Badge variant={memory.status === 'DISABLED' ? 'danger' : 'muted'}>
                    {memory.status ?? 'ACTIVE'}
                  </Badge>
                  <span className="font-mono text-xs text-zinc-500">id:{memory.id}</span>
                  {memory.importance !== null && memory.importance !== undefined ? (
                    <span className="font-mono text-xs text-zinc-500">
                      importance:{memory.importance.toFixed(2)}
                    </span>
                  ) : null}
                </div>
                <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-zinc-200">{memory.content}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {tags.map((tag) => (
                    <Badge key={tag} variant="muted">
                      {tag}
                    </Badge>
                  ))}
                </div>
                <div className="mt-3 flex flex-wrap gap-3 font-mono text-xs text-zinc-500">
                  <span>type:{memory.memoryType ?? '-'}</span>
                  <span>slot:{memory.slotHint ?? '-'}</span>
                  <span>access:{memory.accessCount ?? 0}</span>
                  <span>updated:{formatMemoryTimestamp(memory.updatedAt)}</span>
                  <span>last:{formatMemoryTimestamp(memory.lastAccessedAt)}</span>
                </div>
              </div>
              <div className="flex gap-2 lg:justify-end">
                <Button disabled={submitting} onClick={() => onEdit(memory)} size="sm" variant="secondary">
                  {t('application.edit')}
                </Button>
                <Button disabled={submitting} onClick={() => onDisable(memory)} size="sm" variant="danger">
                  {t('application.disable')}
                </Button>
              </div>
            </div>

            {isEditing ? (
              <div className="rounded-xl border border-white/10 bg-zinc-950/35 p-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <FieldInput
                    id={`memory-category-${memory.id}`}
                    label={t('tools.memoryCategory')}
                    onChange={(value) => onEditFormChange({ ...editForm, memoryCategory: value })}
                    value={editForm.memoryCategory}
                  />
                  <FieldInput
                    id={`memory-importance-${memory.id}`}
                    label={t('tools.memoryImportance')}
                    onChange={(value) => onEditFormChange({ ...editForm, importance: value })}
                    type="number"
                    value={editForm.importance}
                  />
                  <FieldInput
                    id={`memory-tags-${memory.id}`}
                    label={t('tools.memoryTags')}
                    onChange={(value) => onEditFormChange({ ...editForm, tags: value })}
                    value={editForm.tags}
                  />
                  <FieldInput
                    id={`memory-slot-${memory.id}`}
                    label={t('tools.memorySlotHint')}
                    onChange={(value) => onEditFormChange({ ...editForm, slotHint: value })}
                    value={editForm.slotHint}
                  />
                </div>
                <div className="mt-4 space-y-2">
                  <Label htmlFor={`memory-content-${memory.id}`}>{t('tools.content')}</Label>
                  <Textarea
                    className="min-h-36"
                    id={`memory-content-${memory.id}`}
                    onChange={(event) => onEditFormChange({ ...editForm, content: event.target.value })}
                    value={editForm.content}
                  />
                </div>
                <div className="mt-4 flex justify-end">
                  <Button disabled={submitting || !editForm.content.trim()} onClick={() => onSave(memory)}>
                    {t('application.saveChanges')}
                  </Button>
                </div>
              </div>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}
