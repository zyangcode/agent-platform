import { createContext } from 'react'
import type { CurrentUser } from '@/lib/api/types'
import type { LoginRequest } from './api'

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous'

export type AuthContextValue = {
  user: CurrentUser | null
  status: AuthStatus
  isAuthenticated: boolean
  login: (request: LoginRequest) => Promise<void>
  logout: () => void
  refreshCurrentUser: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
