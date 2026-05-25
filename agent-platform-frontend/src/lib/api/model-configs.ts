import { apiClient } from '@/lib/api/client'
import type { ModelConfig } from '@/lib/api/types'

export function listModelConfigs() {
  return apiClient.get<ModelConfig[]>('/model-configs')
}
