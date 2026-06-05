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
  expiresIn: number
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

export type ModelProvider = {
  providerId: number
  name: string
  providerType: string
  baseUrl: string
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

export type ProfileExecutionMode = 'BASIC' | 'TEAM'

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
  executionMode?: ProfileExecutionMode | string | null
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

export type JarSkillRegistrationResult = {
  skillId: number
  skillVersionId: number
  skillCode: string
  status: string
  versionStatus: string
  validationMessage?: string | null
}

export type ExperienceSkill = {
  experienceSkillId: number
  code: string
  name: string
  domain?: string | null
  content: string
}

export type McpServer = {
  mcpServerId: number
  name: string
  serverType: string
  connectionConfig?: JsonValue
  status: string
}

export type McpTool = {
  mcpToolId: number
  mcpServerId: number
  name: string
  description?: string | null
  status: string
  parameterSchema?: JsonValue
}

export type RagIngestResult = {
  documentId: number
  title: string
  docHash: string
  chunkCount: number
  status: string
}

export type RagSearchResult = {
  documentId: number
  chunkId: number
  title: string
  content: string
  sourceUri: string
  score: number
}

export type MemoryRecord = {
  id: number
  applicationId?: number | null
  profileId?: number | null
  memoryType?: string | null
  memoryCategory?: string | null
  content: string
  tags?: string[] | null
  importance?: number | null
  confidence?: number | null
  accessCount?: number | null
  lastAccessedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  slotHint?: string | null
  status?: string | null
}

export type ModelMessage = {
  role: string
  content: string
}

export type ChatStreamRequest = {
  applicationId?: number
  agentMode?: 'agent' | 'team' | 'none'
  profileId?: number
  conversationId?: number
  message?: string
  enabledSkillIds?: number[]
  enabledMcpToolIds?: number[]
  clientRequestId?: string
  modelConfigId?: number
  messages?: ModelMessage[]
  stream?: boolean
  confirmedToolKeys?: string[]
  pendingToolCall?: PendingToolCall
}

export type PendingToolCall = {
  sourceType: 'MCP' | 'SKILL'
  toolName: string
  arguments?: JsonValue
}

export type ConversationSummary = {
  conversationId: number
  applicationId: number
  profileId: number
  title?: string | null
  channel?: string | null
  status: string
  createdAt?: string | null
  updatedAt?: string | null
}

export type ConversationMessage = {
  messageId?: number | null
  role: string
  content: string
  tokenCount?: number | null
  traceId?: string | null
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
