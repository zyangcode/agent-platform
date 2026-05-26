import { apiClient } from '@/lib/api/client'
import type { ModelConfig, ModelProvider } from '@/lib/api/types'

export type CreateModelProviderRequest = {
  apiKey?: string
  baseUrl: string
  name: string
  providerType: string
}

export type CreateModelConfigRequest = {
  capabilitiesJson?: string
  defaultTemperature?: number
  displayName: string
  maxContextTokens: number
  modelName: string
  providerId: number
}

export function listModelProviders() {
  return apiClient.get<ModelProvider[]>('/admin/model-providers')
}

export function createModelProvider(payload: CreateModelProviderRequest) {
  return apiClient.post<ModelProvider>('/admin/model-providers', payload)
}

export function listModelConfigs() {
  return apiClient.get<ModelConfig[]>('/model-configs')
}

export function createModelConfig(payload: CreateModelConfigRequest) {
  return apiClient.post<ModelConfig>('/admin/model-configs', payload)
}
