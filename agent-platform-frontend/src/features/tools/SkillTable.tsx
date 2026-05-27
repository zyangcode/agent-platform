import { Boxes } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { Skill } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'
import { getToolStatusVariant } from './tool-filters'
import { ToolSchemaPreview } from './ToolSchemaPreview'

type SkillTableProps = {
  skills: Skill[]
  status: 'error' | 'loading' | 'ready'
}

export function SkillTable({ skills, status }: SkillTableProps) {
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

  if (skills.length === 0) {
    return (
      <Alert>
        <Boxes className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        <AlertTitle>{t('tools.noSkills')}</AlertTitle>
        <AlertDescription>{t('tools.noSkillsDescription')}</AlertDescription>
      </Alert>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('profile.name')}</TableHead>
          <TableHead>{t('profile.type')}</TableHead>
          <TableHead>{t('tools.skillScope')}</TableHead>
          <TableHead>{t('profile.status')}</TableHead>
          <TableHead>{t('tools.schema')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {skills.map((skill) => (
          <TableRow key={skill.skillId}>
            <TableCell>
              <p className="font-medium text-white">{skill.name}</p>
              <p className="mt-1 font-mono text-xs text-zinc-500">{skill.code}</p>
              <p className="mt-1 max-w-[360px] truncate text-xs text-zinc-500">
                {skill.description || t('profile.noDescription')}
              </p>
            </TableCell>
            <TableCell className="text-zinc-400">{skill.skillType}</TableCell>
            <TableCell>
              <Badge variant="muted">{skill.scope}</Badge>
            </TableCell>
            <TableCell>
              <Badge variant={getToolStatusVariant(skill.status)}>{skill.status}</Badge>
            </TableCell>
            <TableCell>
              <ToolSchemaPreview schema={skill.parameterSchema} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
