import type { LucideIcon } from 'lucide-react'
import { Construction } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

type PlaceholderPageProps = {
  title: string
  description: string
  icon?: LucideIcon
}

export function PlaceholderPage({ description, icon: Icon = Construction, title }: PlaceholderPageProps) {
  return (
    <Card>
      <CardHeader>
        <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
          <Icon className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        </div>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        <Alert>
          <AlertTitle>Route ready</AlertTitle>
          <AlertDescription>
            This route is wired into the console shell. The next steps will replace this panel with
            API-backed feature content.
          </AlertDescription>
        </Alert>
      </CardContent>
    </Card>
  )
}
