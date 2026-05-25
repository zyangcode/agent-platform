import { Slot } from '@radix-ui/react-slot'
import { type ButtonHTMLAttributes, type ComponentPropsWithoutRef } from 'react'
import { cn } from '@/lib/utils'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'border-cyan-200/20 bg-cyan-200 text-zinc-950 shadow-[inset_0_1px_0_rgba(255,255,255,0.45)] hover:bg-cyan-100',
  secondary:
    'border-white/10 bg-white/[0.06] text-zinc-100 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)] hover:border-white/15 hover:bg-white/[0.09]',
  ghost: 'border-transparent bg-transparent text-zinc-300 hover:bg-white/[0.06] hover:text-white',
  danger:
    'border-rose-300/20 bg-rose-400/12 text-rose-100 hover:border-rose-300/30 hover:bg-rose-400/18',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-10 px-4 text-sm',
  lg: 'h-11 px-5 text-sm',
  icon: 'h-10 w-10 p-0',
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  ComponentPropsWithoutRef<'button'> & {
    asChild?: boolean
    variant?: ButtonVariant
    size?: ButtonSize
  }

export function Button({
  asChild = false,
  className,
  size = 'md',
  variant = 'primary',
  type = 'button',
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot : 'button'

  return (
    <Comp
      className={cn(
        'inline-flex shrink-0 items-center justify-center gap-2 rounded-full border font-medium transition-[background,border-color,color,transform,box-shadow] duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] active:translate-y-px disabled:pointer-events-none disabled:opacity-45',
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      type={asChild ? undefined : type}
      {...props}
    />
  )
}
