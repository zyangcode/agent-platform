import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/use-auth'

export function ProtectedRoute() {
  const { isAuthenticated, status } = useAuth()
  const location = useLocation()

  if (status === 'checking') {
    return (
      <main className="min-h-[100dvh] bg-zinc-950 px-6 py-8 text-zinc-100">
        <div className="mx-auto grid min-h-[calc(100dvh-4rem)] w-full max-w-7xl gap-6 lg:grid-cols-[280px_1fr]">
          <Skeleton className="h-full min-h-64" />
          <div className="space-y-4">
            <Skeleton className="h-24" />
            <Skeleton className="h-80" />
          </div>
        </div>
      </main>
    )
  }

  if (!isAuthenticated) {
    return <Navigate replace state={{ from: location }} to="/login" />
  }

  return <Outlet />
}
