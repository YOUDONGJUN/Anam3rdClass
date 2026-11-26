// 버튼 등 여러 곳에서 쓰는 클래스 묶음
// 입체감은 index.css 의 .key(누르면 내려가는 두께) · .card-depth(카드 아래 두께)

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'ok'
export type ButtonSize = 'sm' | 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 rounded-xl font-semibold whitespace-nowrap transition-[background-color,color] duration-150 disabled:pointer-events-none disabled:opacity-50 select-none'

const variants: Record<ButtonVariant, string> = {
  primary: 'key bg-accent font-bold text-on-accent hover:brightness-105',
  secondary: 'key key-line border border-line-strong bg-surface text-ink hover:bg-surface-2',
  ghost: 'text-ink-2 hover:bg-surface-2 hover:text-ink active:bg-surface-2',
  danger: 'key key-line border border-line-strong bg-surface text-danger-ink hover:bg-danger-soft',
  ok: 'key key-ok bg-ok font-bold text-white hover:brightness-105',
}

const sizes: Record<ButtonSize, string> = {
  // 작은 버튼도 손가락으로 누르는 영역은 44px (.hit)
  sm: 'hit h-9 px-3 text-sm',
  md: 'h-11 px-4 text-[15px]',
  lg: 'h-13 px-5 text-base',
}

export function buttonClass(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', extra = '') {
  return `${base} ${variants[variant]} ${sizes[size]} ${extra}`
}

export const inputClass =
  'w-full rounded-xl border border-line-strong bg-surface px-3.5 h-12 text-base text-ink transition-colors hover:border-ink-3 focus:border-accent focus:outline-none focus:ring-3 focus:ring-accent/25 aria-invalid:border-danger'

export const cardClass = 'card-depth rounded-2xl border border-line bg-surface'
