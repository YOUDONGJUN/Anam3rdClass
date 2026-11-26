import type { MeetupCategory } from '../api/types'
import { CATEGORY, categoryLabel, sortCategories } from '../lib/meetup'

type BadgeSize = 'sm' | 'md' | 'lg'

const BOX: Record<BadgeSize, string> = { sm: 'size-6', md: 'size-8', lg: 'size-10' }
const ICON: Record<BadgeSize, string> = { sm: 'size-3.5', md: 'size-4', lg: 'size-5' }

function Dot({ category, size }: { category: MeetupCategory; size: BadgeSize }) {
  const meta = CATEGORY[category]
  return (
    <span
      className={`${BOX[size]} inline-flex shrink-0 items-center justify-center rounded-full shadow-[inset_0_-2px_0_rgb(0_0_0/0.2)] ring-2 ring-surface`}
      style={{ backgroundColor: meta.color, color: meta.ink }}
      title={meta.label}
    >
      <meta.Icon className={ICON[size]} strokeWidth={2.4} aria-hidden />
    </span>
  )
}

/**
 * 지하철 노선 배지처럼 생긴 종류 표시.
 * 여러 개면 환승역 표지판처럼 노선 배지가 살짝 겹쳐 나란히 붙는다.
 */
export function CategoryBadges({
  categories,
  size = 'md',
  withLabel = false,
}: {
  categories: readonly MeetupCategory[]
  size?: BadgeSize
  withLabel?: boolean
}) {
  const list = sortCategories(categories)
  const label = categoryLabel(list)
  // 예전에 4개 넘게 고른 3교시: 배지는 3개까지만 그리고 나머지는 +N (제목 칸을 밀어내지 않게)
  const shown = list.slice(0, 3)
  const more = list.length - shown.length
  return (
    <span className="inline-flex min-w-0 items-center gap-2">
      <span className={`inline-flex shrink-0 items-center ${size === 'sm' ? '-space-x-1.5' : '-space-x-2'}`}>
        {shown.map((c) => (
          <Dot key={c} category={c} size={size} />
        ))}
      </span>
      {more > 0 && !withLabel && (
        <span className="-ml-1 shrink-0 text-xs font-bold text-ink-3" aria-hidden>
          +{more}
        </span>
      )}
      {withLabel ? <span className="min-w-0 text-sm font-semibold">{label}</span> : <span className="sr-only">{label}</span>}
    </span>
  )
}
