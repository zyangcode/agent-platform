import { isRouteErrorResponse, useRouteError } from 'react-router-dom'
import { AlertTriangle, RotateCcw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

function getRouteErrorMessage(error: unknown) {
  if (isRouteErrorResponse(error)) {
    return `${error.status} ${error.statusText}`
  }

  if (error instanceof Error) {
    return error.message
  }

  return 'The current page failed to render.'
}

export function RouteErrorBoundary() {
  const error = useRouteError()

  return (
    <main className="min-h-[100dvh] bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.12),transparent_34rem),linear-gradient(135deg,#09090b_0%,#111827_45%,#0f172a_100%)] px-4 py-6 text-zinc-100 md:px-6">
      <section className="mx-auto flex min-h-[calc(100dvh-3rem)] w-full max-w-3xl items-center">
        <Alert className="w-full" variant="danger">
          <AlertTriangle className="mb-3 h-5 w-5 text-rose-100" strokeWidth={1.75} />
          <AlertTitle>Page crashed</AlertTitle>
          <AlertDescription>{getRouteErrorMessage(error)}</AlertDescription>
          <Button className="mt-4" onClick={() => window.location.assign('/')} size="sm" variant="secondary">
            <RotateCcw className="h-4 w-4" strokeWidth={1.75} />
            Back to dashboard
          </Button>
        </Alert>
      </section>
    </main>
  )
}
