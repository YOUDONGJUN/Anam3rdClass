import type { MeetupCategory } from '../api/types'
import { CATEGORY } from '../lib/meetup'

/** 지하철 노선 배지처럼 생긴 번개 종류 표시 */
export function CategoryBadge({
  category,
  size = 'md',
  withLabel = false,
}: {
  category: MeetupCategory
  size?: 'sm' | 'md' | 'lg'
  withLabel?: boolean
}) {
  const meta = CATEGORY[category]
  const box = size === 'sm' ? 'size-6' : size === 'lg' ? 'size-10' : 'size-8'
  const icon = size === 'sm' ? 'size-3.5' : size === 'lg' ? 'size-5' : 'size-4'
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className={`${box} inline-flex shrink-0 items-center justify-center rounded-full ring-2 ring-white/80 dark:ring-black/30`}
        style={{ backgroundColor: meta.color, color: meta.ink }}
        title={meta.label}
      >
        <meta.Icon className={icon} strokeWidth={2.4} aria-hidden />
      </span>
      {withLabel ? (
        <span className="text-sm font-semibold">{meta.label}</span>
      ) : (
        <span className="sr-only">{meta.label}</span>
      )}
    </span>
  )
}
