import { RefreshCw, ServerCog } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { McpServer } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { getToolStatusVariant } from './tool-filters'

type McpServerTableProps = {
  onDisable: (mcpServerId: number) => void
  onRefreshTools: (mcpServerId: number) => void
  servers: McpServer[]
  status: 'error' | 'loading' | 'ready'
  submitting?: boolean
}

export function McpServerTable({ onDisable, onRefreshTools, servers, status, submitting = false }: McpServerTableProps) {
  const { t } = useI18n()

  if (status === 'loading') {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    )
  }

  if (servers.length === 0) {
    return (
      <Alert>
        <ServerCog className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        <AlertTitle>{t('tools.noMcpServers')}</AlertTitle>
        <AlertDescription>{t('tools.noMcpServersDescription')}</AlertDescription>
      </Alert>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('profile.name')}</TableHead>
          <TableHead>{t('profile.type')}</TableHead>
          <TableHead>{t('profile.status')}</TableHead>
          <TableHead>{t('application.actions')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {servers.map((server) => (
          <TableRow key={server.mcpServerId}>
            <TableCell>
              <p className="font-medium text-white">{server.name}</p>
              <p className="mt-1 font-mono text-xs text-zinc-500">#{server.mcpServerId}</p>
            </TableCell>
            <TableCell className="text-zinc-400">{server.serverType}</TableCell>
            <TableCell>
              <Badge variant={getToolStatusVariant(server.status)}>{server.status}</Badge>
            </TableCell>
            <TableCell>
              <div className="flex flex-wrap gap-2">
                <Button disabled={submitting} onClick={() => onRefreshTools(server.mcpServerId)} size="sm" variant="secondary">
                  <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.75} />
                  {t('common.refresh')}
                </Button>
                <Button disabled={submitting} onClick={() => onDisable(server.mcpServerId)} size="sm" variant="danger">
                  {t('application.disable')}
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
