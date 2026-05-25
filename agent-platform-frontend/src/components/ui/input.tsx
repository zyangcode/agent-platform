import { type InputHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

export type InputProps = InputHTMLAttributes<HTMLInputElement>

export function Input({ className, type = 'text', ...props }: InputProps) {
  return (
    <input
      className={cn(
        'flex h-10 w-full rounded-xl border border-white/10 bg-zinc-950/50 px-3 py-2 text-sm text-zinc-100 shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] transition-[border-color,background,box-shadow] placeholder:text-zinc-600 focus:border-cyan-200/45 focus:bg-zinc-950/70 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      type={type}
      {...props}
    />
  )
}
