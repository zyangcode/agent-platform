import type { CreateModelConfigRequest } from '@/lib/api/model-configs'

export type ModelConfigFormValues = {
  capabilitiesJson: string
  defaultTemperature: string
  displayName: string
  maxContextTokens: string
  modelName: string
  providerId: string
}

export function buildCreateModelConfigPayload(values: ModelConfigFormValues): CreateModelConfigRequest {
  const capabilitiesJson = values.capabilitiesJson.trim() || '{}'

  try {
    JSON.parse(capabilitiesJson)
  } catch {
    throw new Error('Capabilities must be valid JSON.')
  }

  return {
    capabilitiesJson,
    defaultTemperature: Number(values.defaultTemperature),
    displayName: values.displayName.trim(),
    maxContextTokens: Number(values.maxContextTokens),
    modelName: values.modelName.trim(),
    providerId: Number(values.providerId),
  }
}
