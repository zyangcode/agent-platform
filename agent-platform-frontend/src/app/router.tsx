import { createBrowserRouter, Navigate } from 'react-router-dom'
import {
  Activity,
  Bot,
  Boxes,
  KeyRound,
  Settings,
  Shield,
  TerminalSquare,
  UserCog,
  Wrench,
} from 'lucide-react'
import { ConsoleLayout } from '@/components/layout/ConsoleLayout'
import { ProtectedRoute } from '@/app/protected-route'
import { LoginPage } from '@/features/auth/LoginPage'
import { ConsoleHomePage } from '@/features/dashboard/ConsoleHomePage'
import { PlaceholderPage } from '@/features/dashboard/PlaceholderPage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <ConsoleLayout />,
        children: [
          {
            path: '/',
            element: <ConsoleHomePage />,
          },
          {
            path: '/applications',
            element: (
              <PlaceholderPage
                description="Create applications, reveal one-time API keys, and manage key status."
                icon={KeyRound}
                title="Applications"
              />
            ),
          },
          {
            path: '/profiles',
            element: (
              <PlaceholderPage
                description="Create agent profiles and bind existing Skills and MCP tools."
                icon={Bot}
                title="Profiles"
              />
            ),
          },
          {
            path: '/chat',
            element: (
              <PlaceholderPage
                description="Run Agent conversations through Web and observe SSE events."
                icon={TerminalSquare}
                title="Agent Chat"
              />
            ),
          },
          {
            path: '/tools',
            element: (
              <PlaceholderPage
                description="Browse built-in Skills and MCP tools available to profiles."
                icon={Wrench}
                title="Tools"
              />
            ),
          },
          {
            path: '/traces',
            element: (
              <PlaceholderPage
                description="Inspect trace roots and span timelines from the Gateway governance chain."
                icon={Activity}
                title="Traces"
              />
            ),
          },
          {
            path: '/token-usage',
            element: (
              <PlaceholderPage
                description="Review token usage summaries, estimated records, and call details."
                icon={Boxes}
                title="Token Usage"
              />
            ),
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
