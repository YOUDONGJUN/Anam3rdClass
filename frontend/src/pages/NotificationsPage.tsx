import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import {
  AlarmClock,
  Bell,
  CalendarClock,
  Globe2,
  Hourglass,
  Megaphone,
  PartyPopper,
  ReceiptText,
  TrainFront,
  Users,
  Armchair,
  type LucideIcon,
} from 'lucide-react'
import { useNotifications, useReadAllNotifications } from '../api/queries'
import type { PushType } from '../api/types'
import { EmptyState, ErrorState, Spinner } from '../components/ui'
import { cardClass } from '../components/styles'
import { useNow } from '../lib/hooks'
import { formatRelative } from '../lib/time'

const ICON: Record<PushType, LucideIcon> = {
  JOIN: Users, // 참여 · 나감
  REACHED: PartyPopper,
  DEADLINE: Hourglass,
  REMINDER: AlarmClock,
  CHANGED: CalendarClock,
  SPOT: Armchair,
  SETTLE: ReceiptText,
  SCHOOL: TrainFront,
  FEDERATION: Globe2,
  NOTICE: Megaphone,
}

/**
 * 알림함: 내 3교시·참여한 3교시 소식. 열면 모두 읽음이 되고(종 숫자가 사라진다),
 * 이번에 처음 본 알림은 보는 동안 강조해 둔다. 한 줄을 누르면 바로 그 화면으로
 */
export function NotificationsPage() {
  const { data, isPending, isError, error, refetch, dataUpdatedAt } = useNotifications()
  const { mutate: readAll } = useReadAllNotifications()
  const now = useNow(60_000)
  const marked = useRef(0)

  // 불러올 때마다(앱에서 새 알림이 와서 다시 불러와도) 안 읽은 게 있으면 모두 읽음
  useEffect(() => {
    if (!data || data.unreadCount === 0 || marked.current === dataUpdatedAt) return
    marked.current = dataUpdatedAt
    readAll()
  }, [data, dataUpdatedAt, readAll])

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">알림</h1>
        <p className="mt-1 text-[15px] text-ink-2">내 3교시와 참여한 3교시 소식이 30일 동안 모여요.</p>
      </div>

      {isPending ? (
        <Spinner />
      ) : isError ? (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      ) : data.items.length === 0 ? (
        <div className={cardClass}>
          <EmptyState
            title="아직 알림이 없어요"
            body="내 3교시에 누가 참여하거나, 참여한 3교시가 성사되면 여기 알려 드려요."
          />
        </div>
      ) : (
        <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
          {data.items.map((n) => {
            const Icon = ICON[n.type] ?? Bell
            const fresh = !n.read
            return (
              <li
                key={n.id}
                className={`relative flex gap-3.5 px-4 py-4 transition-colors focus-within:bg-surface-2 hover:bg-surface-2/70 sm:px-5 ${
                  fresh ? 'bg-accent-soft/45' : ''
                }`}
              >
                <span
                  className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
                    fresh ? 'bg-accent text-on-accent' : 'bg-surface-2 text-ink-2'
                  }`}
                >
                  <Icon className="size-5" aria-hidden />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline gap-2">
                    <h2 className="min-w-0 flex-1 truncate text-[15px] font-bold text-ink">
                      <Link
                        to={n.path}
                        className="outline-none after:absolute after:inset-0 after:content-[''] focus-visible:underline"
                      >
                        {fresh && <span className="sr-only">새 알림, </span>}
                        {n.title}
                      </Link>
                    </h2>
                    <time dateTime={n.createdAt} className="shrink-0 text-[13px] text-ink-3">
                      {formatRelative(n.createdAt, now)}
                    </time>
                  </div>
                  <p className={`mt-0.5 text-[15px] leading-snug ${fresh ? 'font-semibold text-ink' : 'text-ink-2'}`}>
                    {n.body}
                  </p>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
