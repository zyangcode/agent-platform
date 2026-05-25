import { ApiError, getApiErrorKind } from '@/lib/api/errors'
import type { ApiResponse, ChatStreamRequest } from '@/lib/api/types'
import { getAccessToken } from '@/lib/auth/token-storage'
import { parseSseChunk } from '@/lib/sse/parse-sse'
import type { ChatStreamEvent } from './types'

type StreamChatOptions = {
  onEvent: (event: ChatStreamEvent) => void
  signal?: AbortSignal
}

export async function streamChat(request: ChatStreamRequest, options: StreamChatOptions) {
  const token = getAccessToken()
  let response: Response

  try {
    response = await fetch('/api/chat/stream', {
      body: JSON.stringify(request),
      headers: {
        Accept: 'text/event-stream',
        Authorization: token ? `Bearer ${token}` : '',
        'Content-Type': 'application/json',
      },
      method: 'POST',
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(error instanceof Error ? error.message : 'Chat request failed', {
      code: 'NETWORK_ERROR',
      kind: 'network',
    })
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (!response.body) {
    throw new ApiError('Chat stream response has no body', {
      code: 'STREAM_EMPTY',
      kind: 'unknown',
      status: response.status,
    })
  }

  const decoder = new TextDecoder()
  const reader = response.body.getReader()
  let remainder = ''

  while (true) {
    const { done, value } = await reader.read()

    if (done) {
      break
    }

    const chunk = decoder.decode(value, { stream: true })
    const parsed = parseSseChunk<ChatStreamEvent>(remainder, chunk)
    remainder = parsed.remainder
    parsed.events.forEach(({ data }) => options.onEvent(data))
  }

  const tail = decoder.decode()
  if (tail) {
    const parsed = parseSseChunk<ChatStreamEvent>(remainder, tail)
    parsed.events.forEach(({ data }) => options.onEvent(data))
  }
}

async function toApiError(response: Response) {
  const text = await response.text()

  try {
    const payload = JSON.parse(text) as Partial<ApiResponse<unknown>>
    return new ApiError(payload.message ?? response.statusText, {
      code: payload.code,
      kind: getApiErrorKind(response.status),
      status: response.status,
    })
  } catch {
    return new ApiError(text || response.statusText, {
      code: 'STREAM_REQUEST_FAILED',
      kind: getApiErrorKind(response.status),
      status: response.status,
    })
  }
}
