export type ApiErrorKind =
  | 'unauthorized'
  | 'forbidden'
  | 'quota_exceeded'
  | 'server'
  | 'network'
  | 'business'
  | 'unknown'

export class ApiError extends Error {
  readonly code: string
  readonly kind: ApiErrorKind
  readonly status?: number

  constructor(message: string, options: { code?: string; kind?: ApiErrorKind; status?: number } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = options.code ?? 'UNKNOWN'
    this.kind = options.kind ?? 'unknown'
    this.status = options.status
  }
}

export function getApiErrorKind(status: number): ApiErrorKind {
  if (status === 401) {
    return 'unauthorized'
  }
  if (status === 403) {
    return 'forbidden'
  }
  if (status === 429) {
    return 'quota_exceeded'
  }
  if (status >= 500) {
    return 'server'
  }

  return 'business'
}
