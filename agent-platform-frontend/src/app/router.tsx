import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ConsoleLayout } from '@/components/layout/ConsoleLayout'
import { RouteErrorBoundary } from '@/app/ErrorBoundary'
import { ProtectedRoute } from '@/app/protected-route'
import { RoleGuard } from '@/app/RoleGuard'
import { AdminSecurityPage } from '@/features/admin/AdminSecurityPage'
import { AdminUsersPage } from '@/features/admin/AdminUsersPage'
import { ApplicationsPage } from '@/features/applications/ApplicationsPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { ChatPage } from '@/features/chat/ChatPage'
import { ConsoleHomePage } from '@/features/dashboard/ConsoleHomePage'
import { ModelManagementPage } from '@/features/models/ModelManagementPage'
import { ProfilesPage } from '@/features/profiles/ProfilesPage'
import { ToolsPage } from '@/features/tools/ToolsPage'
import { TraceListPage } from '@/features/traces/TraceListPage'
import { TokenUsagePage } from '@/features/token-usage/TokenUsagePage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
    errorElement: <RouteErrorBoundary />,
  },
  {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <ConsoleLayout />,
        errorElement: <RouteErrorBoundary />,
        children: [
          {
            path: '/',
            element: <ConsoleHomePage />,
          },
          {
            path: '/applications',
            element: <ApplicationsPage />,
          },
          {
            path: '/profiles',
            element: <ProfilesPage />,
          },
          {
            path: '/chat',
            element: <ChatPage />,
          },
          {
            path: '/tools',
            element: <ToolsPage />,
          },
          {
            path: '/traces',
            element: <TraceListPage />,
          },
          {
            path: '/token-usage',
            element: <TokenUsagePage />,
          },
          {
            path: '/admin/models',
            element: (
              <RoleGuard roles={['ADMIN']}>
                <ModelManagementPage />
              </RoleGuard>
            ),
          },
          {
            path: '/admin/security',
            element: (
              <RoleGuard roles={['ADMIN']}>
                <AdminSecurityPage />
              </RoleGuard>
            ),
          },
          {
            path: '/admin/users',
            element: (
              <RoleGuard roles={['ADMIN']}>
                <AdminUsersPage />
              </RoleGuard>
            ),
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate replace to="/" />,
  },
])
