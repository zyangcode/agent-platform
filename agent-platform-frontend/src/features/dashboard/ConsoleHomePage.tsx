import { Activity, Database, KeyRound, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const metrics = [
  { label: 'Applications', value: '0', icon: KeyRound },
  { label: 'Profiles', value: '0', icon: ShieldCheck },
  { label: 'Trace roots', value: '0', icon: Activity },
  { label: 'Token usage', value: '0', icon: Database },
]

const readinessChecks: Array<{ layer: string; status: string; signal: string }> = [
  { layer: 'Auth flow', status: 'Ready', signal: 'JWT session' },
  { layer: 'Console shell', status: 'Ready', signal: 'Role-aware nav' },
  { layer: 'API client', status: 'Ready', signal: 'ApiResponse<T>' },
]

export function ConsoleHomePage() {
  return (
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
          <CardTitle>Stage 3 console shell</CardTitle>
          <CardDescription>
            Login, route protection, role-aware navigation, token storage, API client, and UI
            primitives are ready.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <Alert>
            <AlertTitle>Role strategy</AlertTitle>
            <AlertDescription>
              User, Developer, and Admin share this shell. Feature routes will filter menus and
              actions by role while backend permissions remain authoritative.
            </AlertDescription>
          </Alert>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Layer</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Signal</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {readinessChecks.map((check) => (
                <TableRow key={check.layer}>
                  <TableCell>{check.layer}</TableCell>
                  <TableCell>
                    <Badge variant="success">{check.status}</Badge>
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
  )
}
