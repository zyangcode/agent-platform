import { useEffect, useMemo, useState } from 'react'
import { Bot, RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { resolveSelectedApplicationId } from '@/features/applications/application-selection-utils'
import { listApplications } from '@/features/applications/api'
import { ApiError } from '@/lib/api/errors'
import { loadLastSelectedApplicationId, saveLastSelectedApplicationId } from '@/lib/application-selection-storage'
import { listModelConfigs } from '@/lib/api/model-configs'
import { listProfiles } from '@/lib/api/profiles'
import type { Application, ModelConfig, PageResult, Profile } from '@/lib/api/types'
import { CreateProfileDialog } from './CreateProfileDialog'
import { DisableProfileDialog } from './DisableProfileDialog'
import { EditProfileDialog } from './EditProfileDialog'
import { getProfile } from './api'
import { ProfileDetailPanel } from './ProfileDetailPanel'
import { ProfileToolBindingPanel } from './ProfileToolBindingPanel'
import { selectProfileAfterReload } from './profile-selection-utils'
import { getStatusVariant } from './profile-utils'

type ProfilesState =
  | {
      applications: PageResult<Application>
      error: null
      modelConfigs: ModelConfig[]
      profiles: PageResult<Profile>
      status: 'ready'
    }
  | {
      applications: null
      error: string | null
      modelConfigs: ModelConfig[]
      profiles: null
      status: 'error' | 'loading'
    }

function getErrorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : 'Profiles could not be loaded.'
}

export function ProfilesPage() {
  const [selectedApplicationId, setSelectedApplicationId] = useState<number | null>(null)
  const [selectedProfile, setSelectedProfile] = useState<Profile | null>(null)
  const [state, setState] = useState<ProfilesState>({
    applications: null,
    error: null,
    modelConfigs: [],
    profiles: null,
    status: 'loading',
  })

  const selectedApplication = useMemo(() => {
    return (
      state.applications?.records.find(
        (application) => application.applicationId === selectedApplicationId,
      ) ?? state.applications?.records[0] ?? null
    )
  }, [selectedApplicationId, state.applications])

  async function fetchProfiles(applicationId?: number | null) {
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
        ? await listProfiles(effectiveApplicationId, 1, 50)
        : { pageNo: 1, pageSize: 50, records: [], total: 0, totalPages: 0 }

      return {
        applications,
        error: null,
        modelConfigs,
        profiles,
        status: 'ready',
      } satisfies ProfilesState
    } catch (error) {
      return {
        applications: null,
        error: getErrorMessage(error),
        modelConfigs: [],
        profiles: null,
        status: 'error',
      } satisfies ProfilesState
    }
  }

  async function loadProfiles(
    applicationId?: number | null,
    preferredProfileId?: number | null,
    detailedProfile?: Profile | null,
  ) {
    setState((current) => ({
      ...current,
      applications: null,
      error: null,
      profiles: null,
      status: 'loading',
    }))
    const nextState = await fetchProfiles(applicationId)
    setState(nextState)

    if (nextState.status === 'ready') {
      const nextApplicationId = resolveSelectedApplicationId(
        nextState.applications,
        applicationId ?? null,
        loadLastSelectedApplicationId(),
      )
      setSelectedApplicationId(nextApplicationId)
      setSelectedProfile(selectProfileAfterReload(nextState.profiles.records, preferredProfileId, detailedProfile))
    }
  }

  async function refreshSelectedProfile(profileId: number) {
    try {
      const profile = await getProfile(profileId)
      setSelectedProfile(profile)
      await loadProfiles(profile.applicationId, profileId, profile)
    } catch {
      await loadProfiles(selectedApplicationId)
    }
  }

  async function handleApplicationChange(value: string) {
    const applicationId = Number(value)
    saveLastSelectedApplicationId(applicationId)
    setSelectedApplicationId(applicationId)
    setSelectedProfile(null)
    await loadProfiles(applicationId)
  }

  async function handleProfileSelect(profile: Profile) {
    try {
      setSelectedProfile(await getProfile(profile.profileId))
    } catch {
      setSelectedProfile(profile)
    }
  }

  useEffect(() => {
    let isMounted = true

    async function initialize() {
      const preferredApplicationId = loadLastSelectedApplicationId()
      const nextState = await fetchProfiles(preferredApplicationId)

      if (isMounted) {
        setState(nextState)

        if (nextState.status === 'ready') {
          const nextApplicationId = resolveSelectedApplicationId(
            nextState.applications,
            preferredApplicationId,
            preferredApplicationId,
          )
          setSelectedApplicationId(nextApplicationId)
          const firstProfile = nextState.profiles.records[0] ?? null
          if (firstProfile) {
            try {
              setSelectedProfile(await getProfile(firstProfile.profileId))
            } catch {
              setSelectedProfile(firstProfile)
            }
          } else {
            setSelectedProfile(null)
          }
        }
      }
    }

    void initialize()

    return () => {
      isMounted = false
    }
  }, [])

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white">Profiles</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">
            Configure a single Agent profile for an application, bind enabled tools, then use it in Chat.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => loadProfiles(selectedApplicationId)} variant="secondary">
            <RefreshCw className="h-4 w-4" strokeWidth={1.75} />
            Refresh
          </Button>
          <EditProfileDialog
            modelConfigs={state.modelConfigs}
            onUpdated={(profile) => {
              setSelectedProfile(profile)
              void loadProfiles(profile.applicationId, profile.profileId, profile)
            }}
            profile={selectedProfile}
          />
          <DisableProfileDialog
            onDisabled={(profile) => {
              setSelectedProfile(profile)
              void loadProfiles(profile.applicationId, profile.profileId, profile)
            }}
            profile={selectedProfile}
          />
          <CreateProfileDialog
            application={selectedApplication}
            modelConfigs={state.modelConfigs}
            onCreated={(profile) => {
              setSelectedProfile(profile)
              void loadProfiles(profile.applicationId)
            }}
          />
        </div>
      </div>

      {state.status === 'error' ? (
        <Alert variant="danger">
          <AlertTitle>Profiles unavailable</AlertTitle>
          <AlertDescription>{state.error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Application scope</CardTitle>
          <CardDescription>Profiles are listed under a single application.</CardDescription>
        </CardHeader>
        <CardContent>
          {state.status === 'loading' ? (
            <Skeleton className="h-10 max-w-xl" />
          ) : (
            <div className="max-w-xl space-y-2">
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
          )}
        </CardContent>
      </Card>

      {!selectedApplication && state.status === 'ready' ? (
        <Alert>
          <Bot className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
          <AlertTitle>No application</AlertTitle>
          <AlertDescription>Create an Application before creating Profiles.</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Profile list</CardTitle>
            <CardDescription>Click a row to inspect and bind tools.</CardDescription>
          </CardHeader>
          <CardContent>
            {state.status === 'loading' ? (
              <div className="space-y-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : state.status === 'ready' && state.profiles.records.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Model</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.profiles.records.map((profile) => {
                    const isSelected = selectedProfile?.profileId === profile.profileId
                    const modelConfig = state.modelConfigs.find(
                      (model) => model.modelConfigId === profile.modelConfigId,
                    )

                    return (
                      <TableRow
                        data-state={isSelected ? 'selected' : undefined}
                        key={profile.profileId}
                        onClick={() => void handleProfileSelect(profile)}
                      >
                        <TableCell>
                          <p className="font-medium text-white">{profile.name}</p>
                          <p className="mt-1 max-w-[320px] truncate text-xs text-zinc-500">
                            {profile.description || profile.profileType}
                          </p>
                        </TableCell>
                        <TableCell className="text-zinc-400">
                          {modelConfig?.displayName || modelConfig?.modelName || `#${profile.modelConfigId}`}
                        </TableCell>
                        <TableCell>
                          <Badge variant={getStatusVariant(profile.status)}>{profile.status}</Badge>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            ) : (
              <Alert>
                <AlertTitle>No profiles</AlertTitle>
                <AlertDescription>Create a profile for this application, then bind tools.</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>

        <ProfileDetailPanel modelConfigs={state.modelConfigs} profile={selectedProfile} />
      </div>

      <ProfileToolBindingPanel onProfileChanged={refreshSelectedProfile} profile={selectedProfile} />
    </section>
  )
}
