import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { tabKeys } from '../lib/tabKeys'

/**
 * 화면 맨 위 안내판 — 모바일에서는 위쪽 안내판 머리와 이어지고, 그 아래 내용(BoardSheet)이 살짝 겹쳐 올라온다.
 * 데스크톱에서는 둥근 안내판 카드.
 */
export function BoardHero({
  eyebrow,
  title,
  subtitle,
  children,
}: {
  eyebrow?: ReactNode
  title: ReactNode
  subtitle?: ReactNode
  children?: ReactNode
}) {
  return (
    <section className="-mx-4 -mt-6 bg-board px-5 pt-5 pb-12 text-on-board sm:-mx-6 sm:px-7 lg:mx-0 lg:mt-0 lg:rounded-3xl lg:px-8 lg:pt-7 lg:pb-8">
      {eyebrow && <p className="text-[13px] font-semibold text-on-board-2">{eyebrow}</p>}
      <h1 className="mt-1 text-[28px] leading-tight font-extrabold tracking-[-0.03em]">{title}</h1>
      {subtitle && <p className="mt-1.5 text-[15px] leading-relaxed text-on-board-2">{subtitle}</p>}
      {children}
    </section>
  )
}

/** BoardHero 아래 내용. 모바일에서는 안내판 위로 겹쳐 올라오는 시트 */
export function BoardSheet({ children }: { children: ReactNode }) {
  return (
    <div className="relative -mx-4 -mt-6 rounded-t-[22px] bg-bg px-4 pt-5 shadow-[0_-10px_24px_-18px_rgb(0_0_0/0.55)] sm:-mx-6 sm:px-6 lg:mx-0 lg:mt-8 lg:rounded-none lg:bg-transparent lg:p-0 lg:shadow-none">
      {children}
    </div>
  )
}

export interface HeroTab<T extends string> {
  value: T
  label: ReactNode
  Icon?: LucideIcon
}

/** 안내판 위의 큰 탭 — 홈 파인 틀 안에서 고른 칸이 볼록 튀어나온다 */
export function HeroTabs<T extends string>({
  label,
  value,
  onChange,
  tabs,
}: {
  label: string
  value: T
  onChange: (v: T) => void
  tabs: HeroTab<T>[]
}) {
  return (
    <div
      role="tablist"
      aria-label={label}
      onKeyDown={(e) => tabKeys(e, tabs.map((t) => t.value), value, onChange)}
      className="mt-5 grid gap-1 rounded-2xl bg-black/20 p-1 pb-1.5 shadow-[inset_0_2px_3px_rgb(0_0_0/0.25)]"
      style={{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }}
    >
      {tabs.map(({ value: v, label: text, Icon }) => {
        const active = v === value
        return (
          <button
            key={v}
            type="button"
            role="tab"
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            onClick={() => onChange(v)}
            className={`flex h-11 items-center justify-center gap-2 rounded-xl text-[15px] font-bold transition-colors ${
              active ? 'bg-on-board text-board shadow-[0_3px_0_rgb(0_0_0/0.28)]' : 'text-on-board/85 hover:text-on-board'
            }`}
          >
            {Icon && <Icon className="size-[18px]" aria-hidden />}
            {text}
          </button>
        )
      })}
    </div>
  )
}
