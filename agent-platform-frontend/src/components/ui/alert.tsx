import { type HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type AlertVariant = 'default' | 'danger' | 'warning'

const variants: Record<AlertVariant, string> = {
  default: 'border-cyan-200/15 bg-cyan-300/8 text-cyan-50',
  danger: 'border-rose-200/20 bg-rose-300/10 text-rose-50',
  warning: 'border-amber-200/20 bg-amber-300/10 text-amber-50',
}

export function Alert({
  className,
  variant = 'default',
  ...props
}: HTMLAttributes<HTMLDivElement> & { variant?: AlertVariant }) {
  return (
    <div
      className={cn('rounded-2xl border p-4 text-sm leading-6', variants[variant], className)}
      {...props}
    />
  )
}

export function AlertTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return <h4 className={cn('mb-1 font-medium text-white', className)} {...props} />
}

export function AlertDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-current/75', className)} {...props} />
}
