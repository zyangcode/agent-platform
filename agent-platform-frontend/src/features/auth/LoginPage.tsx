import { type FormEvent, useMemo, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Activity, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/errors'
import { register as registerRequest } from './api'
import { useAuth } from './use-auth'

type LocationState = { from?: { pathname?: string } }

const capabilityRows = [
  { label: 'Gateway 治理链', value: 'Trace / Quota / Security', icon: ShieldCheck },
  { label: 'Agent Runtime', value: 'Profile + Skill + Memory', icon: Activity },
  { label: '浏览器 / API Key', value: 'JWT + sk-* 双入口', icon: KeyRound },
]

export function LoginPage() {
  const { isAuthenticated, login, status } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [mode, setMode] = useState<'login' | 'register'>('login')

  const returnTo = useMemo(() => {
    const state = location.state as LocationState | null
    return state?.from?.pathname ?? '/'
  }, [location.state])

  if (isAuthenticated) return <Navigate replace to={returnTo} />

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      const normalizedUsername = username.trim()
      if (mode === 'register') {
        await registerRequest({ displayName: displayName.trim(), password, username: normalizedUsername })
      }
      await login({ password, username: normalizedUsername })
      navigate(returnTo, { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.message)
      else setError('登录失败，请稍后重试')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="min-h-[100dvh] bg-bg px-4 py-6 md:px-8">
      <div className="substrate-grid" />
      <div className="relative mx-auto grid min-h-[calc(100dvh-3rem)] w-full max-w-5xl items-center gap-10 lg:grid-cols-[1fr_400px]">
        {/* Left */}
        <div className="space-y-8">
          <div>
            <div className="flex items-center gap-3 mb-6">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-[rgba(56,189,248,0.25)] bg-[rgba(56,189,248,0.1)]">
                <span className="text-lg font-bold text-accent-cyan">N</span>
              </div>
              <span className="text-xl font-semibold text-text">Nexus</span>
            </div>
            <h1 className="max-w-lg text-4xl font-semibold tracking-tight text-text md:text-5xl">
              Agent 编排与可观测工作台
            </h1>
            <p className="mt-5 max-w-md text-sm leading-7 text-text-muted">
              登录后进入统一控制台。多模型接入、永续记忆、Team 多 Agent 协作、全链路 Trace。
            </p>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            {capabilityRows.map((item) => {
              const Icon = item.icon
              return (
                <div className="glass-panel p-4" key={item.label}>
                  <Icon className="h-4 w-4 text-accent-cyan" strokeWidth={1.75} />
                  <p className="mt-3 text-sm font-medium text-text">{item.label}</p>
                  <p className="mt-1 font-mono text-xs text-text-muted">{item.value}</p>
                </div>
              )
            })}
          </div>
        </div>

        {/* Right card */}
        <div className="glass-panel-strong p-8 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl border border-[rgba(56,189,248,0.3)] bg-[rgba(56,189,248,0.1)] mb-5">
            <LockKeyhole className="h-6 w-6 text-accent-cyan" strokeWidth={1.75} />
          </div>
          <h2 className="text-2xl font-semibold text-text">Nexus</h2>
          <p className="mt-1 text-sm text-text-muted">Agent 编排与可观测工作台</p>

          {/* Login/Register tabs */}
          <div className="mt-7 grid grid-cols-2 gap-1 p-1 rounded-lg border border-[rgba(148,163,184,0.12)] bg-surface-soft">
            <button
              className={`h-9 rounded-md text-sm font-medium transition ${
                mode === 'login'
                  ? 'bg-[rgba(59,130,246,0.18)] text-text border border-[rgba(96,165,250,0.28)]'
                  : 'text-text-muted hover:text-text'
              }`}
              onClick={() => { setError(null); setMode('login') }}
            >
              登录
            </button>
            <button
              className={`h-9 rounded-md text-sm font-medium transition ${
                mode === 'register'
                  ? 'bg-[rgba(59,130,246,0.18)] text-text border border-[rgba(96,165,250,0.28)]'
                  : 'text-text-muted hover:text-text'
              }`}
              onClick={() => { setError(null); setMode('register') }}
            >
              注册
            </button>
          </div>

          <form className="mt-6 space-y-4 text-left" onSubmit={handleSubmit}>
            {mode === 'register' ? (
              <div>
                <label className="text-xs text-text-muted mb-1.5 block">显示名称</label>
                <Input
                  autoComplete="name"
                  maxLength={128}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Blue Mountain User"
                  required
                  value={displayName}
                  className="glass-input"
                />
              </div>
            ) : null}
            <div>
              <label className="text-xs text-text-muted mb-1.5 block">用户名</label>
              <Input
                autoComplete="username"
                maxLength={64}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                required
                value={username}
                className="glass-input"
              />
            </div>
            <div>
              <label className="text-xs text-text-muted mb-1.5 block">密码</label>
              <Input
                autoComplete="current-password"
                maxLength={128}
                minLength={6}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="admin123"
                required
                type="password"
                value={password}
                className="glass-input"
              />
            </div>

            {error ? (
              <Alert variant="danger">
                <AlertTitle>登录失败</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}

            <Button className="w-full btn-accent" disabled={isSubmitting || status === 'checking'} type="submit">
              {isSubmitting ? (mode === 'login' ? '正在登录' : '正在注册') : mode === 'login' ? '登录' : '注册并登录'}
            </Button>
          </form>

          <div className="mt-5 border-t border-[rgba(148,163,184,0.1)] pt-4">
            <button
              className="w-full text-xs text-text-muted hover:text-text transition"
              onClick={() => {
                setError(null)
                setMode((c) => (c === 'login' ? 'register' : 'login'))
                if (mode === 'login') { setUsername(''); setPassword('') }
                else { setUsername('admin'); setPassword('admin123'); setDisplayName('') }
              }}
            >
              {mode === 'login' ? '没有账号？注册一个' : '已有账号？返回登录'}
            </button>
          </div>
        </div>
      </div>
    </main>
  )
}
