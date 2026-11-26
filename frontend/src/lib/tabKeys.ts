import type { KeyboardEvent } from 'react'

/** 탭 목록에서 ←/→/Home/End 로 옆 탭을 고르고 그 탭으로 옮겨 간다 */
export function tabKeys<T extends string>(e: KeyboardEvent<HTMLElement>, values: readonly T[], value: T, onChange: (v: T) => void) {
  const i = values.indexOf(value)
  const next =
    e.key === 'ArrowRight' ? (i + 1) % values.length
    : e.key === 'ArrowLeft' ? (i - 1 + values.length) % values.length
    : e.key === 'Home' ? 0
    : e.key === 'End' ? values.length - 1
    : -1
  if (next < 0) return
  e.preventDefault()
  onChange(values[next])
  e.currentTarget.querySelectorAll<HTMLElement>('[role="tab"]')[next]?.focus()
}
