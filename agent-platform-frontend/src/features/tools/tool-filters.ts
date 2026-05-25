export type SearchableTool = {
  description?: string | null
  key: string
  name: string
  status: string
}

export function filterToolsBySearch<TTool extends SearchableTool>(tools: TTool[], search: string) {
  const normalizedSearch = search.trim().toLowerCase()

  if (!normalizedSearch) {
    return tools
  }

  return tools.filter((tool) => {
    return [tool.name, tool.key, tool.description ?? '', tool.status]
      .join(' ')
      .toLowerCase()
      .includes(normalizedSearch)
  })
}

export function getToolStatusVariant(status: string) {
  const normalized = status.toUpperCase()

  if (normalized === 'ENABLED' || normalized === 'LOADED' || normalized === 'ACTIVE') {
    return 'success'
  }
  if (normalized === 'FAILED' || normalized === 'DISABLED' || normalized === 'UNINSTALLED') {
    return 'danger'
  }
  if (normalized === 'VALIDATING' || normalized === 'UPLOADED') {
    return 'warning'
  }

  return 'muted'
}
