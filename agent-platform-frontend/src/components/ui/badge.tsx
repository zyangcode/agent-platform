import { type HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'muted'

const variants: Record<BadgeVariant, string> = {
  default: 'border-cyan-200/20 bg-cyan-300/10 text-cyan-100',
  success: 'border-emerald-200/20 bg-emerald-300/10 text-emerald-100',
  warning: 'border-amber-200/20 bg-amber-300/10 text-amber-100',
  danger: 'border-rose-200/20 bg-rose-300/10 text-rose-100',
  muted: 'border-white/10 bg-white/[0.04] text-zinc-300',
}

export function Badge({
  className,
  variant = 'default',
  ...props
}: HTMLAttributes<HTMLSpanElement> & { variant?: BadgeVariant }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium',
        variants[variant],
        className,
      )}
      {...props}
    />
  )
}
