// 버튼 등 여러 곳에서 쓰는 클래스 묶음

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'ok'
export type ButtonSize = 'sm' | 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 rounded-xl font-semibold whitespace-nowrap transition-[background-color,box-shadow,transform,color] duration-150 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 select-none'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-on-accent hover:brightness-105 shadow-[0_1px_0_rgb(0_0_0/0.08),0_4px_12px_-4px_color-mix(in_oklab,var(--accent)_60%,transparent)]',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-surface-2',
  ghost: 'text-ink-2 hover:bg-surface-2 hover:text-ink',
  danger: 'bg-surface text-danger-ink border border-line-strong hover:bg-danger-soft',
  ok: 'bg-ok text-white hover:brightness-105',
}

const sizes: Record<ButtonSize, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-11 px-4 text-[15px]',
  lg: 'h-13 px-5 text-base',
}

export function buttonClass(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', extra = '') {
  return `${base} ${variants[variant]} ${sizes[size]} ${extra}`
}

export const inputClass =
  'w-full rounded-xl border border-line-strong bg-surface px-3.5 h-12 text-base text-ink transition-colors hover:border-ink-3 focus:border-accent focus:outline-none focus:ring-3 focus:ring-accent/25 aria-invalid:border-danger'

export const cardClass = 'rounded-2xl border border-line bg-surface'
