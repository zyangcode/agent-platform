import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient, apiRequest } from './client'

describe('apiRequest', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends FormData without JSON stringifying the body', async () => {
    vi.stubGlobal('window', {
      localStorage: {
        getItem: vi.fn().mockReturnValue(null),
        removeItem: vi.fn(),
      },
      location: {
        origin: 'http://localhost',
      },
    })
    const formData = new FormData()
    formData.append('manifest', new Blob(['{}'], { type: 'application/json' }), 'manifest.json')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'OK', data: { uploaded: true }, message: 'success', success: true }), {
        headers: { 'Content-Type': 'application/json' },
        status: 200,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/skills/jar', {
      body: formData,
      method: 'POST',
    })

    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(request.body).toBe(formData)
    expect(new Headers(request.headers).has('Content-Type')).toBe(false)
  })

  it('sends PATCH requests through the apiClient helper', async () => {
    vi.stubGlobal('window', {
      localStorage: {
        getItem: vi.fn().mockReturnValue(null),
        removeItem: vi.fn(),
      },
      location: {
        origin: 'http://localhost',
      },
    })
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'OK', data: { id: 70001 }, message: 'success', success: true }), {
        headers: { 'Content-Type': 'application/json' },
        status: 200,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await apiClient.patch('/memories/70001', { content: 'updated' })

    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(request.method).toBe('PATCH')
    expect(request.body).toBe(JSON.stringify({ content: 'updated' }))
  })
})
