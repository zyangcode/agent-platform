import { Navigate } from 'react-router-dom'
import { useAuth } from '@/features/auth/use-auth'
import { hasAnyRequiredRole } from './role-guard-utils'

type RoleGuardProps = {
  children: React.ReactNode
  roles: string[]
}

export function RoleGuard({ children, roles }: RoleGuardProps) {
  const { user } = useAuth()

  if (!hasAnyRequiredRole(user?.roles, roles)) {
    return <Navigate replace to="/" />
  }

  return children
}
