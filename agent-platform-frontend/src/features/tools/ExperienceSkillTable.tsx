import { BrainCircuit } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { ExperienceSkill } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/use-i18n'

type ExperienceSkillTableProps = {
  onDisable: (experienceSkillId: number) => void
  skills: ExperienceSkill[]
  status: 'error' | 'loading' | 'ready'
}

export function ExperienceSkillTable({ onDisable, skills, status }: ExperienceSkillTableProps) {
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
        <BrainCircuit className="mb-3 h-5 w-5 text-cyan-100" strokeWidth={1.75} />
        <AlertTitle>{t('tools.noExperienceSkills')}</AlertTitle>
        <AlertDescription>{t('tools.noExperienceSkillsDescription')}</AlertDescription>
      </Alert>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('profile.name')}</TableHead>
          <TableHead>{t('tools.domain')}</TableHead>
          <TableHead>{t('tools.content')}</TableHead>
          <TableHead>{t('application.actions')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {skills.map((skill) => (
          <TableRow key={skill.experienceSkillId}>
            <TableCell>
              <p className="font-medium text-white">{skill.name}</p>
              <p className="mt-1 font-mono text-xs text-zinc-500">{skill.code}</p>
            </TableCell>
            <TableCell className="text-zinc-400">{skill.domain || t('tools.noDomain')}</TableCell>
            <TableCell>
              <p className="max-w-[420px] truncate text-sm text-zinc-300">{skill.content}</p>
            </TableCell>
            <TableCell>
              <Button onClick={() => onDisable(skill.experienceSkillId)} size="sm" variant="danger">
                {t('application.disable')}
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
