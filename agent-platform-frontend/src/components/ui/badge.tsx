import { type HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'muted'

const variants: Record<BadgeVariant, string> = {
  default: 'border-[rgba(96,165,250,0.32)] bg-[rgba(59,130,246,0.12)] text-[#bfdbfe]',
  success: 'border-[rgba(34,197,94,0.32)] bg-[rgba(34,197,94,0.1)] text-[#bbf7d0]',
  warning: 'border-[rgba(245,158,11,0.32)] bg-[rgba(245,158,11,0.1)] text-[#fde68a]',
  danger: 'border-[rgba(251,113,133,0.32)] bg-[rgba(251,113,133,0.1)] text-[#fecdd3]',
  muted: 'border-[rgba(148,163,184,0.16)] bg-[rgba(255,255,255,0.055)] text-[#cbd5e1]',
}

export function Badge({
  className,
  variant = 'default',
  ...props
}: HTMLAttributes<HTMLSpanElement> & { variant?: BadgeVariant }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium font-mono',
        variants[variant],
        className,
      )}
      {...props}
    />
  )
}
