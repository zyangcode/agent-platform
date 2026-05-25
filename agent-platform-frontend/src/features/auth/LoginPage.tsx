import { type FormEvent, useMemo, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Activity, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/errors'
import { useAuth } from './use-auth'

type LocationState = {
  from?: {
    pathname?: string
  }
}

const capabilityRows = [
  { label: 'Gateway治理链', value: 'Trace / Quota / Security', icon: ShieldCheck },
  { label: 'Agent Runtime', value: 'Profile + Skill + Memory', icon: Activity },
  { label: '浏览器入口', value: 'JWT session', icon: KeyRound },
]

export function LoginPage() {
  const { isAuthenticated, login, status } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const returnTo = useMemo(() => {
    const state = location.state as LocationState | null
    return state?.from?.pathname ?? '/'
  }, [location.state])

  if (isAuthenticated) {
    return <Navigate replace to={returnTo} />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      await login({ password, username })
      navigate(returnTo, { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
      } else {
        setError('登录失败，请稍后重试')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="min-h-[100dvh] bg-[radial-gradient(circle_at_12%_14%,rgba(20,184,166,0.14),transparent_30rem),linear-gradient(135deg,#09090b_0%,#111827_48%,#0f172a_100%)] px-4 py-6 text-zinc-100 md:px-8">
      <section className="mx-auto grid min-h-[calc(100dvh-3rem)] w-full max-w-6xl items-center gap-8 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="space-y-8">
          <div>
            <Badge variant="default">Agent Platform Console</Badge>
            <h1 className="mt-6 max-w-2xl text-4xl font-semibold tracking-tight text-white md:text-5xl">
              AI Infra 控制台入口
            </h1>
            <p className="mt-5 max-w-xl text-sm leading-7 text-zinc-400">
              登录后进入同一套控制台框架。不同角色通过菜单和操作权限区分，后端权限仍是最终防线。
            </p>
          </div>

          <div className="grid gap-3 md:grid-cols-3">
            {capabilityRows.map((item) => {
              const Icon = item.icon

              return (
                <Card className="bg-zinc-950/38" key={item.label}>
                  <CardContent className="p-4">
                    <Icon className="h-4 w-4 text-cyan-200" strokeWidth={1.75} />
                    <p className="mt-4 text-sm font-medium text-white">{item.label}</p>
                    <p className="mt-1 font-mono text-xs text-zinc-500">{item.value}</p>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        </div>

        <Card className="mx-auto w-full max-w-md bg-zinc-950/58">
          <CardHeader>
            <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
              <LockKeyhole className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
            </div>
            <CardTitle>登录控制台</CardTitle>
            <CardDescription>使用阶段 1 已初始化的账号进入前端 MVP。</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-5" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <Input
                  autoComplete="username"
                  id="username"
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="admin"
                  value={username}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">密码</Label>
                <Input
                  autoComplete="current-password"
                  id="password"
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="admin123"
                  type="password"
                  value={password}
                />
              </div>

              {error ? (
                <Alert variant="danger">
                  <AlertTitle>登录失败</AlertTitle>
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              ) : null}

              <Button className="w-full" disabled={isSubmitting || status === 'checking'} type="submit">
                {isSubmitting ? '正在登录' : '登录'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </section>
    </main>
  )
}
