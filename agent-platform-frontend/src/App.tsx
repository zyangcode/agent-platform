import { Activity, Database, KeyRound, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const navigationItems = [
  'Dashboard',
  'Applications',
  'Profiles',
  'Agent Chat',
  'Traces',
  'Token Usage',
  'Tools',
]

const metrics = [
  { label: 'Applications', value: '0', icon: KeyRound },
  { label: 'Profiles', value: '0', icon: ShieldCheck },
  { label: 'Trace roots', value: '0', icon: Activity },
  { label: 'Token usage', value: '0', icon: Database },
]

const apiReadinessChecks: Array<{ layer: string; status: string; signal: string }> = [
  { layer: 'API client', status: 'Ready', signal: 'ApiResponse<T>' },
  { layer: 'DTO map', status: 'Typed', signal: 'Stage 3 scope' },
]

function App() {
  return (
    <main className="min-h-[100dvh] bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.12),transparent_34rem),linear-gradient(135deg,#09090b_0%,#111827_45%,#0f172a_100%)] text-zinc-100">
      <div className="mx-auto flex min-h-[100dvh] w-full max-w-7xl flex-col px-6 py-8">
        <header className="flex items-center justify-between border-b border-white/10 pb-6">
          <div>
            <p className="text-xs uppercase tracking-[0.22em] text-cyan-200/70">
              Agent Platform
            </p>
            <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white">
              Console scaffold
            </h1>
          </div>
          <Badge variant="success">Frontend ready</Badge>
        </header>

        <section className="grid flex-1 gap-6 py-8 lg:grid-cols-[280px_1fr]">
          <Card className="p-4">
            <nav className="space-y-2 text-sm text-zinc-300">
              {navigationItems.map((item) => (
                <div
                  className="rounded-xl border border-transparent px-3 py-2 transition hover:border-white/10 hover:bg-white/[0.05]"
                  key={item}
                >
                  {item}
                </div>
              ))}
            </nav>
          </Card>

          <section className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {metrics.map((metric) => {
                const Icon = metric.icon

                return (
                  <Card className="bg-zinc-950/45" key={metric.label}>
                    <CardContent className="p-5">
                      <div className="flex items-center justify-between">
                        <span className="text-sm text-zinc-400">{metric.label}</span>
                        <Icon className="h-4 w-4 text-cyan-200/80" strokeWidth={1.75} />
                      </div>
                      <p className="mt-5 font-mono text-3xl text-white">{metric.value}</p>
                    </CardContent>
                  </Card>
                )
              })}
            </div>

            <Card>
              <CardHeader>
                <CardTitle>Stage 3 frontend foundation</CardTitle>
                <CardDescription>
                  React, Vite, TypeScript, Tailwind CSS, routing dependencies, charting,
                  icons, path aliases, and the local API proxy are ready for the console MVP.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-5">
                <Alert>
                  <AlertTitle>Theme baseline</AlertTitle>
                  <AlertDescription>
                    The console uses charcoal surfaces, restrained cyan and emerald accents,
                    subtle inner borders, and tactile component states.
                  </AlertDescription>
                </Alert>

                <div className="flex flex-wrap gap-3">
                  <Button>Primary action</Button>
                  <Button variant="secondary">Secondary</Button>
                  <Button variant="ghost">Ghost</Button>
                </div>

                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Layer</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Signal</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {apiReadinessChecks.map((check) => (
                      <TableRow key={check.layer}>
                        <TableCell>{check.layer}</TableCell>
                        <TableCell>
                          <Badge variant={check.status === 'Ready' ? 'success' : 'default'}>
                            {check.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="font-mono text-zinc-300">{check.signal}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>

                <div className="grid gap-3 md:grid-cols-3">
                  <Skeleton className="h-3" />
                  <Skeleton className="h-3" />
                  <Skeleton className="h-3" />
                </div>
              </CardContent>
            </Card>
          </section>
        </section>
      </div>
    </main>
  )
}

export default App
