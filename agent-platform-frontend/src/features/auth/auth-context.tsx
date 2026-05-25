import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/lib/auth/token-storage'
import { ApiError } from '@/lib/api/errors'
import type { CurrentUser } from '@/lib/api/types'
import { getCurrentUser, login as loginRequest, type LoginRequest } from './api'
import { AuthContext, type AuthContextValue, type AuthStatus } from './auth-context-value'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('checking')

  const logout = useCallback(() => {
    clearAccessToken()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const refreshCurrentUser = useCallback(async () => {
    const token = getAccessToken()

    if (!token) {
      setUser(null)
      setStatus('anonymous')
      return
    }

    try {
      const currentUser = await getCurrentUser()
      setUser(currentUser)
      setStatus('authenticated')
    } catch (error) {
      if (error instanceof ApiError && error.kind === 'unauthorized') {
        logout()
        return
      }

      setStatus('anonymous')
      throw error
    }
  }, [logout])

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await loginRequest(request)
      setAccessToken(response.accessToken)
      setUser(response.user)
      setStatus('authenticated')
    },
    [],
  )

  useEffect(() => {
    let isMounted = true

    async function initialize() {
      try {
        await refreshCurrentUser()
      } catch {
        if (isMounted) {
          setUser(null)
          setStatus('anonymous')
        }
      }
    }

    void initialize()

    return () => {
      isMounted = false
    }
  }, [refreshCurrentUser])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated',
      login,
      logout,
      refreshCurrentUser,
    }),
    [login, logout, refreshCurrentUser, status, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
