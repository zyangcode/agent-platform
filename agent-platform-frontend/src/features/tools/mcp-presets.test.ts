import { describe, expect, it } from 'vitest'
import { MCP_SERVER_PRESETS, MCP_SERVER_TYPE_OPTIONS, mcpServerPresetToForm } from './mcp-presets'

describe('MCP presets', () => {
  it('includes advanced server types supported by the backend', () => {
    expect(MCP_SERVER_TYPE_OPTIONS).toContain('STREAMABLE_HTTP')
    expect(MCP_SERVER_TYPE_OPTIONS).toContain('SSE')
  })

  it('includes bundled and HTTP examples', () => {
    expect(MCP_SERVER_PRESETS.map((preset) => preset.id)).toEqual([
      'filesystem',
      'weather',
      'http-calculator',
      'streamable-http',
    ])
  })

  it('formats a preset into the MCP server form shape', () => {
    const form = mcpServerPresetToForm(MCP_SERVER_PRESETS[0])

    expect(form.name).toBe('Readonly Filesystem MCP')
    expect(form.serverType).toBe('STDIO')
    expect(JSON.parse(form.connectionConfig)).toEqual({
      command: 'builtin-demo-filesystem-mcp',
    })
  })
})
