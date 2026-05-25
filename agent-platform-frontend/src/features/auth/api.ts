import { apiClient } from '@/lib/api/client'
import type { CurrentUser, LoginResponse, RegisterResult } from '@/lib/api/types'

export type LoginRequest = {
  username: string
  password: string
}

export type RegisterRequest = {
  username: string
  password: string
  displayName: string
}

export function login(request: LoginRequest) {
  return apiClient.post<LoginResponse>('/auth/login', request)
}

export function register(request: RegisterRequest) {
  return apiClient.post<RegisterResult>('/auth/register', request)
}

export function getCurrentUser() {
  return apiClient.get<CurrentUser>('/auth/me')
}
