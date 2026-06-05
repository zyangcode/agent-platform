import { clearAccessToken, getAccessToken } from '@/lib/auth/token-storage'
import { ApiError, getApiErrorKind } from './errors'
import type { ApiResponse } from './types'

type RequestMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

type ApiRequestOptions = Omit<RequestInit, 'body' | 'method'> & {
  body?: BodyInit | unknown
  method?: RequestMethod
  query?: Record<string, boolean | number | string | null | undefined>
}

const API_BASE_URL = '/api'

function buildUrl(path: string, query?: ApiRequestOptions['query']) {
  const normalizedPath = path.startsWith('/api/')
    ? path.slice('/api'.length)
    : path.startsWith('/')
      ? path
      : `/${path}`
  const url = new URL(`${API_BASE_URL}${normalizedPath}`, window.location.origin)

  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })

  return `${url.pathname}${url.search}`
}

async function readJsonSafely(response: Response) {
  const text = await response.text()

  if (!text) {
    return null
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    throw new ApiError('Response is not valid JSON', {
      code: 'INVALID_JSON',
      kind: 'unknown',
      status: response.status,
    })
  }
}

function createHeaders(options?: ApiRequestOptions) {
  const headers = new Headers(options?.headers)
  const token = getAccessToken()
  const isFormDataBody = typeof FormData !== 'undefined' && options?.body instanceof FormData

  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  if (options?.body !== undefined && !isFormDataBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  return headers
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { body, method = 'GET', query, ...fetchOptions } = options
  const isFormDataBody = typeof FormData !== 'undefined' && body instanceof FormData

  let response: Response

  try {
    response = await fetch(buildUrl(path, query), {
      ...fetchOptions,
      body: body === undefined ? undefined : isFormDataBody ? body : JSON.stringify(body),
      headers: createHeaders(options),
      method,
    })
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : 'Network request failed', {
      code: 'NETWORK_ERROR',
      kind: 'network',
    })
  }

  const payload = await readJsonSafely(response)

  if (!response.ok) {
    const apiPayload = payload as Partial<ApiResponse<unknown>> | null
    const kind = getApiErrorKind(response.status)

    if (response.status === 401) {
      clearAccessToken()
    }

    throw new ApiError(apiPayload?.message ?? response.statusText, {
      code: apiPayload?.code,
      kind,
      status: response.status,
    })
  }

  const apiPayload = payload as ApiResponse<T> | null

  if (!apiPayload || typeof apiPayload.success !== 'boolean') {
    throw new ApiError('Unexpected API response shape', {
      code: 'INVALID_RESPONSE',
      kind: 'unknown',
      status: response.status,
    })
  }

  if (!apiPayload.success) {
    throw new ApiError(apiPayload.message, {
      code: apiPayload.code,
      kind: 'business',
      status: response.status,
    })
  }

  return apiPayload.data
}

export const apiClient = {
  get: <T>(path: string, options?: Omit<ApiRequestOptions, 'body' | 'method'>) =>
    apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'body' | 'method'>) =>
    apiRequest<T>(path, { ...options, body, method: 'POST' }),
  put: <T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'body' | 'method'>) =>
    apiRequest<T>(path, { ...options, body, method: 'PUT' }),
  patch: <T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'body' | 'method'>) =>
    apiRequest<T>(path, { ...options, body, method: 'PATCH' }),
  delete: <T>(path: string, options?: Omit<ApiRequestOptions, 'body' | 'method'>) =>
    apiRequest<T>(path, { ...options, method: 'DELETE' }),
}
