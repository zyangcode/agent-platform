import { type HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'animate-pulse rounded-xl bg-white/[0.06] shadow-[inset_0_1px_0_rgba(255,255,255,0.05)]',
        className,
      )}
      {...props}
    />
  )
}
