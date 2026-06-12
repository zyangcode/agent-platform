import type { JsonValue } from '@/lib/api/types'

export type McpServerType = 'HTTP' | 'STDIO' | 'STREAMABLE_HTTP' | 'SSE'

export type McpServerPreset = {
  connectionConfig: JsonValue
  id: string
  label: string
  name: string
  serverType: McpServerType
}

export const MCP_SERVER_TYPE_OPTIONS: McpServerType[] = ['STDIO', 'HTTP', 'STREAMABLE_HTTP', 'SSE']

export const MCP_SERVER_PRESETS: McpServerPreset[] = [
  {
    connectionConfig: {
      command: 'builtin-demo-filesystem-mcp',
    },
    id: 'filesystem',
    label: 'Filesystem',
    name: 'Readonly Filesystem MCP',
    serverType: 'STDIO',
  },
  {
    connectionConfig: {
      command: 'builtin-demo-weather-mcp',
    },
    id: 'weather',
    label: 'Weather',
    name: 'Bundled Weather MCP',
    serverType: 'STDIO',
  },
  {
    connectionConfig: {
      baseUrl: 'http://localhost:8088/mcp',
    },
    id: 'http-calculator',
    label: 'HTTP Calculator',
    name: 'HTTP Calculator MCP',
    serverType: 'HTTP',
  },
  {
    connectionConfig: {
      baseUrl: 'http://localhost:8088',
      endpoint: '/mcp',
      initializationTimeoutSeconds: 10,
      requestTimeoutSeconds: 20,
    },
    id: 'streamable-http',
    label: 'Streamable HTTP',
    name: 'Streamable HTTP MCP',
    serverType: 'STREAMABLE_HTTP',
  },
]

export function mcpServerPresetToForm(preset: McpServerPreset) {
  return {
    connectionConfig: JSON.stringify(preset.connectionConfig, null, 2),
    name: preset.name,
    serverType: preset.serverType,
  }
}
