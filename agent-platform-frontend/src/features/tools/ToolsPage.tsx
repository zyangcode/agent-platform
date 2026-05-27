import { useEffect, useMemo, useState } from 'react'
import { RefreshCw, Search, Wrench } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { FilterSelect } from '@/components/ui/filter-select'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/errors'
import type { McpTool, Skill } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import {
  listMcpTools,
  listSkills,
  type McpStatusFilter,
  type SkillScopeFilter,
  type SkillStatusFilter,
} from './api'
import { McpToolTable } from './McpToolTable'
import { SkillTable } from './SkillTable'
import { filterToolsBySearch } from './tool-filters'

type ToolsState =
  | { error: null; mcpTools: McpTool[]; skills: Skill[]; status: 'ready' }
  | { error: string | null; mcpTools: McpTool[]; skills: Skill[]; status: 'error' | 'loading' }

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
  { label: 'Enabled', value: 'ENABLED' },
  { label: 'Disabled', value: 'DISABLED' },
  { label: 'Failed', value: 'FAILED' },
]

function normalizeAllFilter<TValue extends string>(value: TValue | 'ALL') {
  return value === 'ALL' ? '' : value
}

function getErrorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : 'Tools could not be loaded.'
}

async function fetchToolsForFilters(
  skillScope: SkillScopeFilter | 'ALL',
  skillStatus: SkillStatusFilter | 'ALL',
  mcpStatus: McpStatusFilter | 'ALL',
) {
  try {
    const [skills, mcpTools] = await Promise.all([
      listSkills(
        normalizeAllFilter(skillScope) as SkillScopeFilter,
        normalizeAllFilter(skillStatus) as SkillStatusFilter,
      ),
      listMcpTools(normalizeAllFilter(mcpStatus) as McpStatusFilter),
    ])

    return { error: null, mcpTools, skills, status: 'ready' } satisfies ToolsState
  } catch (error) {
    return { error: getErrorMessage(error), mcpTools: [], skills: [], status: 'error' } satisfies ToolsState
  }
}

export function ToolsPage() {
  const { t } = useI18n()
  const [mcpStatus, setMcpStatus] = useState<McpStatusFilter | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [skillScope, setSkillScope] = useState<SkillScopeFilter | 'ALL'>('ALL')
  const [skillStatus, setSkillStatus] = useState<SkillStatusFilter | 'ALL'>('ALL')
  const [state, setState] = useState<ToolsState>({
    error: null,
    mcpTools: [],
    skills: [],
    status: 'loading',
  })

  const filteredSkills = useMemo(() => {
    return filterToolsBySearch(
      state.skills.map((skill) => ({
        ...skill,
        key: skill.code,
      })),
      search,
    )
  }, [search, state.skills])

  const filteredMcpTools = useMemo(() => {
    return filterToolsBySearch(
      state.mcpTools.map((tool) => ({
        ...tool,
        key: tool.name,
      })),
      search,
    )
  }, [search, state.mcpTools])

  async function fetchTools() {
    return fetchToolsForFilters(skillScope, skillStatus, mcpStatus)
  }

  async function loadTools() {
    setState({ error: null, mcpTools: [], skills: [], status: 'loading' })
    setState(await fetchTools())
  }

  useEffect(() => {
    let isMounted = true

    async function initializeTools() {
      const nextState = await fetchToolsForFilters(skillScope, skillStatus, mcpStatus)

      if (isMounted) {
        setState(nextState)
      }
    }

    void initializeTools()

    return () => {
      isMounted = false
    }
  }, [mcpStatus, skillScope, skillStatus])

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

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTitle>{t('tools.unavailable')}</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
              <Wrench className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
            </div>
            <div>
              <CardTitle>{t('tools.catalogFilters')}</CardTitle>
              <CardDescription>{t('tools.catalogFiltersDescription')}</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)]">
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
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-6 2xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
        <Card>
          <CardHeader>
            <CardTitle>{t('tools.skills')}</CardTitle>
            <CardDescription>
              {t('tools.visibleLoadedSkills', { visible: filteredSkills.length, total: state.skills.length })}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <SkillTable skills={filteredSkills} status={state.status === 'error' ? 'error' : state.status} />
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
            <McpToolTable tools={filteredMcpTools} status={state.status === 'error' ? 'error' : state.status} />
          </CardContent>
        </Card>
      </div>
    </section>
  )
}
