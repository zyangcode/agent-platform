import { describe, expect, it } from 'vitest'
import { buildCreateModelConfigPayload } from './model-form-utils'

describe('model form utils', () => {
  it('normalizes model config form values for API submission', () => {
    expect(
      buildCreateModelConfigPayload({
        capabilitiesJson: '',
        defaultTemperature: '0.30',
        displayName: ' DeepSeek Chat ',
        maxContextTokens: '64000',
        modelName: ' deepseek-chat ',
        providerId: '7',
      }),
    ).toEqual({
      capabilitiesJson: '{}',
      defaultTemperature: 0.3,
      displayName: 'DeepSeek Chat',
      maxContextTokens: 64000,
      modelName: 'deepseek-chat',
      providerId: 7,
    })
  })

  it('rejects invalid capabilities json before hitting the API', () => {
    expect(() =>
      buildCreateModelConfigPayload({
        capabilitiesJson: '{broken',
        defaultTemperature: '0.70',
        displayName: 'Broken',
        maxContextTokens: '8192',
        modelName: 'broken-model',
        providerId: '1',
      }),
    ).toThrow('Capabilities must be valid JSON.')
  })
})
