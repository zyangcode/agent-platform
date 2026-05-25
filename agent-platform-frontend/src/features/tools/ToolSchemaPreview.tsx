import { Code2 } from 'lucide-react'
import type { JsonValue } from '@/lib/api/types'

type ToolSchemaPreviewProps = {
  schema?: JsonValue
}

export function ToolSchemaPreview({ schema }: ToolSchemaPreviewProps) {
  if (!schema) {
    return <span className="text-xs text-zinc-600">No schema</span>
  }

  const text = JSON.stringify(schema)

  return (
    <div className="flex max-w-[360px] items-center gap-2 rounded-lg border border-white/10 bg-zinc-950/60 px-2.5 py-1.5">
      <Code2 className="h-3.5 w-3.5 shrink-0 text-zinc-500" strokeWidth={1.75} />
      <span className="truncate font-mono text-xs text-zinc-400">{text}</span>
    </div>
  )
}
