import { PlugZap } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { McpTool } from '@/lib/api/types'
import { getToolStatusVariant } from './tool-filters'
import { ToolSchemaPreview } from './ToolSchemaPreview'

type McpToolTableProps = {
  tools: McpTool[]
  status: 'error' | 'loading' | 'ready'
}

export function McpToolTable({ status, tools }: McpToolTableProps) {
  if (status === 'loading') {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    )
  }

  if (tools.length === 0) {
    return (
      <Alert>
        <PlugZap className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        <AlertTitle>No MCP tools</AlertTitle>
        <AlertDescription>No MCP tools match the current filters.</AlertDescription>
      </Alert>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Server</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Schema</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {tools.map((tool) => (
          <TableRow key={tool.mcpToolId}>
            <TableCell>
              <p className="font-medium text-white">{tool.name}</p>
              <p className="mt-1 max-w-[360px] truncate text-xs text-zinc-500">
                {tool.description || 'No description'}
              </p>
            </TableCell>
            <TableCell className="font-mono text-xs text-zinc-400">#{tool.mcpServerId}</TableCell>
            <TableCell>
              <Badge variant={getToolStatusVariant(tool.status)}>{tool.status}</Badge>
            </TableCell>
            <TableCell>
              <ToolSchemaPreview schema={tool.parameterSchema} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
