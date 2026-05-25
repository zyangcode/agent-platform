export type JsonObject = Record<string, unknown>
export type JsonValue = string | number | boolean | null | JsonObject | JsonValue[]

export type ApiResponse<T> = {
  success: boolean
  code: string
  message: string
  data: T
}

export type PageResult<T> = {
  records: T[]
  pageNo: number
  pageSize: number
  total: number
  totalPages: number
}

export type CurrentUser = {
  userId: number
  tenantId: number
  username: string
  displayName: string
  roles: string[]
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: CurrentUser
}

export type RegisterResult = {
  userId: number
  username: string
  displayName: string
}

export type Application = {
  applicationId: number
  name: string
  description?: string | null
  status: string
  createdAt: string
}

export type CreatedApiKey = {
  apiKeyId: number
  name: string
  key: string
  keyPrefix: string
  status: string
}

export type ApiKey = {
  apiKeyId: number
  name: string
  keyPrefix: string
  status: string
  lastUsedAt?: string | null
  createdAt: string
}

export type CreateApplicationResult = {
  applicationId: number
  name: string
  status: string
  apiKey: CreatedApiKey
}

export type RevokeApiKeyResult = {
  apiKeyId: number
  status: string
}

export type ModelConfig = {
  modelConfigId: number
  providerId: number
  modelName: string
  displayName: string
  capabilities?: JsonValue
  defaultTemperature?: number | null
  maxContextTokens?: number | null
  status: string
}

export type ProfileSkillBinding = {
  skillId: number
  code?: string
  name?: string
  status?: string
}

export type ProfileMcpToolBinding = {
  mcpToolId: number
  name?: string
  status?: string
}

export type Profile = {
  profileId: number
  applicationId: number
  name: string
  profileType: string
  description?: string | null
  modelConfigId: number
  promptExtra?: string | null
  memoryStrategy?: JsonValue
  maxSteps?: number | null
  visibility: string
  status: string
  skillBindings: ProfileSkillBinding[]
  mcpToolBindings: ProfileMcpToolBinding[]
}

export type Skill = {
  skillId: number
  code: string
  name: string
  description?: string | null
  skillType: string
  scope: string
  status: string
  parameterSchema?: JsonValue
}

export type McpTool = {
  mcpToolId: number
  mcpServerId: number
  name: string
  description?: string | null
  status: string
  parameterSchema?: JsonValue
}

export type ModelMessage = {
  role: string
  content: string
}

export type ChatStreamRequest = {
  applicationId?: number
  agentMode?: 'agent' | 'none'
  profileId?: number
  conversationId?: number
  message?: string
  enabledSkillIds?: number[]
  enabledMcpToolIds?: number[]
  clientRequestId?: string
  modelConfigId?: number
  messages?: ModelMessage[]
  stream?: boolean
}

export type TraceSummary = {
  traceId: string
  applicationId?: number | null
  userId?: number | null
  profileId?: number | null
  conversationId?: number | null
  entrypoint?: string | null
  agentMode?: string | null
  status: string
  latencyMs?: number | null
  totalTokens?: number | null
  estimated?: boolean | null
  startedAt?: string | null
  endedAt?: string | null
}

export type TraceSpan = {
  id: number
  traceId: string
  parentSpanId?: number | null
  spanName: string
  spanType: string
  component: string
  status: string
  startedAt?: string | null
  endedAt?: string | null
  latencyMs?: number | null
  errorCode?: string | null
  errorMessage?: string | null
  attributes?: JsonValue
  createdAt?: string | null
}

export type TokenUsage = {
  id: number
  traceId: string
  spanId?: number | null
  tenantId: number
  applicationId?: number | null
  userId: number
  profileId?: number | null
  modelConfigId?: number | null
  providerId?: number | null
  modelName?: string | null
  providerType?: string | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimated: boolean
  createdAt: string
}

export type TraceDetail = TraceSummary & {
  tenantId?: number | null
  clientRequestId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  metadata?: JsonValue
  spans: TraceSpan[]
  tokenUsages: TokenUsage[]
}

export type TokenUsageTopModel = {
  modelConfigId?: number | null
  modelName?: string | null
  providerType?: string | null
  requestCount: number
  totalTokens: number
}

export type TokenUsageSummary = {
  applicationId?: number | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  requestCount: number
  estimatedCount: number
  realUsageCount: number
  topModels: TokenUsageTopModel[]
}
