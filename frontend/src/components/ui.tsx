import { useId, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { CircleAlert, LoaderCircle, RefreshCw } from 'lucide-react'
import { buttonClass, type ButtonSize, type ButtonVariant } from './styles'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
}

export function Button({ variant, size, loading, className = '', children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      className={buttonClass(variant, size, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <LoaderCircle className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  )
}

export function Spinner({ label = '불러오는 중' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-16 text-ink-3">
      <LoaderCircle className="size-5 animate-spin" aria-hidden />
      <span className="text-sm">{label}</span>
    </div>
  )
}

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string | null
  optional?: boolean
  children: (props: { id: string; describedBy?: string; invalid: boolean }) => ReactNode
}

export function Field({ label, hint, error, optional, children }: FieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const describedBy = error || hint ? hintId : undefined
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="flex items-baseline gap-1.5 text-[15px] font-semibold text-ink">
        {label}
        {optional && <span className="text-xs font-medium text-ink-3">선택</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {(error || hint) && (
        <p id={hintId} className={`text-sm leading-relaxed ${error ? 'text-danger-ink' : 'text-ink-3'}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
}

export function FormError({ message }: { message?: string | null }) {
  if (!message) return null
  return (
    <div role="alert" className="flex items-start gap-2 rounded-xl bg-danger-soft px-4 py-3 text-[15px] text-danger-ink">
      <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
      <span>{message}</span>
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center gap-4 px-6 py-16 text-center">
      <CircleAlert className="size-8 text-ink-3" aria-hidden />
      <p className="text-[15px] text-ink-2">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          <RefreshCw className="size-4" aria-hidden />
          다시 시도
        </Button>
      )}
    </div>
  )
}

export function EmptyState({ title, body, action }: { title: string; body?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      <p className="text-lg font-bold text-balance text-ink">{title}</p>
      {body && <p className="max-w-sm text-[15px] leading-relaxed text-balance text-ink-2">{body}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

interface SegmentedProps<T extends string> {
  value: T
  onChange: (value: T) => void
  options: { value: T; label: string }[]
  label: string
}

export function Segmented<T extends string>({ value, onChange, options, label }: SegmentedProps<T>) {
  return (
    <div
      role="tablist"
      aria-label={label}
      className="inline-flex rounded-xl bg-surface-2 p-1 pb-1.5 shadow-[inset_0_1px_2px_rgb(0_0_0/0.1)]"
    >
      {options.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(o.value)}
            className={`hit h-9 rounded-lg px-3.5 text-sm font-semibold transition-colors ${
              active ? 'bg-surface font-bold text-ink shadow-[0_2px_0_var(--line-strong)]' : 'text-ink-3 hover:text-ink'
            }`}
          >
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
