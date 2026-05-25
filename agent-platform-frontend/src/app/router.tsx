import { createBrowserRouter, Navigate } from 'react-router-dom'
import { Settings, Shield, UserCog } from 'lucide-react'
import { ConsoleLayout } from '@/components/layout/ConsoleLayout'
import { RouteErrorBoundary } from '@/app/ErrorBoundary'
import { ProtectedRoute } from '@/app/protected-route'
import { ApplicationsPage } from '@/features/applications/ApplicationsPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { ChatPage } from '@/features/chat/ChatPage'
import { ConsoleHomePage } from '@/features/dashboard/ConsoleHomePage'
import { PlaceholderPage } from '@/features/dashboard/PlaceholderPage'
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
              <PlaceholderPage
                description="Reserved Admin route for model provider and model config management."
                icon={Settings}
                title="Model Configs"
              />
            ),
          },
          {
            path: '/admin/security',
            element: (
              <PlaceholderPage
                description="Reserved Admin route for security policy and sensitive data governance."
                icon={Shield}
                title="Security"
              />
            ),
          },
          {
            path: '/admin/users',
            element: (
              <PlaceholderPage
                description="Reserved Admin route for user and role management."
                icon={UserCog}
                title="Users"
              />
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
