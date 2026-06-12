import { describe, expect, it } from 'vitest'
import { DEFAULT_MCP_SERVER_STATUS } from './tool-defaults'

describe('tool defaults', () => {
  it('shows only active MCP servers by default', () => {
    expect(DEFAULT_MCP_SERVER_STATUS).toBe('ACTIVE')
  })
})
