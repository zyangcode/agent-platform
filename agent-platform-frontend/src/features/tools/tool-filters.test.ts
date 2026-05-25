import { describe, expect, it } from 'vitest'
import { filterToolsBySearch } from './tool-filters'

describe('filterToolsBySearch', () => {
  it('matches by name, code, description, and status case-insensitively', () => {
    const tools = [
      {
        description: 'Runs arithmetic expressions',
        key: 'calculator',
        name: 'Calculator',
        status: 'ENABLED',
      },
      {
        description: 'Queries weather data',
        key: 'weather_query',
        name: 'Weather',
        status: 'DISABLED',
      },
    ]

    expect(filterToolsBySearch(tools, 'arith')).toEqual([tools[0]])
    expect(filterToolsBySearch(tools, 'WEATHER_QUERY')).toEqual([tools[1]])
    expect(filterToolsBySearch(tools, 'enabled')).toEqual([tools[0]])
  })

  it('returns all tools when search is blank', () => {
    const tools = [
      {
        description: null,
        key: 'calculator',
        name: 'Calculator',
        status: 'ENABLED',
      },
    ]

    expect(filterToolsBySearch(tools, '   ')).toEqual(tools)
  })
})
