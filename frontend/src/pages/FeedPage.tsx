import { Link, useSearchParams } from 'react-router'
import { ChevronRight, Megaphone, Plus } from 'lucide-react'
import { useMeetups, useNotices } from '../api/queries'
import type { MeetupSummary } from '../api/types'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { EmptyState, ErrorState, Segmented } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useNow } from '../lib/hooks'
import { formatDate, formatDayLabel, kstDayKey } from '../lib/time'

function groupByDay(list: MeetupSummary[]) {
  const groups: { key: string; items: MeetupSummary[] }[] = []
  for (const m of list) {
    const key = kstDayKey(m.meetAt)
    const last = groups[groups.length - 1]
    if (last?.key === key) last.items.push(m)
    else groups.push({ key, items: [m] })
  }
  return groups
}

function PinnedNotice() {
  const { data } = useNotices()
  const pinned = data?.find((n) => n.pinned)
  if (!pinned) return null
  return (
    <Link
      to={`/notices/${pinned.id}`}
      className="mb-6 flex items-center gap-3 rounded-2xl bg-board px-4 py-3 text-on-board transition-colors hover:bg-board-2"
    >
      <span className="flex shrink-0 items-center gap-1.5 rounded-md bg-exit px-2 py-1 text-xs font-bold text-on-exit">
        <Megaphone className="size-3.5" aria-hidden />
        안내
      </span>
      <span className="min-w-0 flex-1 truncate text-[15px] font-semibold">{pinned.title}</span>
      <ChevronRight className="size-4 shrink-0 text-on-board-2" aria-hidden />
    </Link>
  )
}

export function FeedPage() {
  const [params, setParams] = useSearchParams()
  const scope = params.get('scope') === 'past' ? 'past' : 'upcoming'
  const { data, isPending, isError, error, refetch } = useMeetups(scope)
  const now = useNow()

  return (
    <div>
      <PinnedNotice />

      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">
            {scope === 'upcoming' ? '열린 번개' : '지난 번개'}
          </h1>
          <p className="mt-1 text-[15px] text-ink-2">
            {scope === 'upcoming' ? '수업 끝나고, 3교시는 여기서.' : '성사되어 실제로 모였던 번개예요.'}
          </p>
        </div>
        <Segmented
          label="번개 보기"
          value={scope}
          onChange={(v) => setParams(v === 'past' ? { scope: 'past' } : {}, { replace: true })}
          options={[
            { value: 'upcoming', label: '다가오는' },
            { value: 'past', label: '지난' },
          ]}
        />
      </div>

      {isPending ? (
        <div className={`${cardClass} divide-y divide-line`}>
          <MeetupRowSkeleton />
          <MeetupRowSkeleton />
          <MeetupRowSkeleton />
        </div>
      ) : isError ? (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <div className={cardClass}>
          {scope === 'upcoming' ? (
            <EmptyState
              title="아직 열린 번개가 없어요"
              body="먼저 열어 보는 건 어때요? 인원이 안 모이면 조용히 사라지니까 부담 없어요."
              action={
                <Link to="/meetups/new" className={buttonClass('primary')}>
                  <Plus className="size-4" strokeWidth={2.6} aria-hidden />
                  번개 열기
                </Link>
              }
            />
          ) : (
            <EmptyState title="아직 성사된 번개가 없어요" body="첫 번째 성사의 주인공이 되어 보세요." />
          )}
        </div>
      ) : (
        <div className="flex flex-col gap-7">
          {groupByDay(data).map((g) => {
            const label = formatDayLabel(g.items[0].meetAt, now)
            const date = formatDate(g.items[0].meetAt)
            return (
              <section key={g.key} aria-label={label}>
                <h2 className="mb-2.5 flex items-baseline gap-2 px-1">
                  <span className="text-[17px] font-extrabold text-ink">{label}</span>
                  {label !== date && <span className="text-sm text-ink-3">{date}</span>}
                  <span className="ml-auto text-sm text-ink-3">{g.items.length}개</span>
                </h2>
                <div className={`${cardClass} divide-y divide-line overflow-hidden`}>
                  {g.items.map((m) => (
                    <MeetupRow key={m.id} meetup={m} now={now} />
                  ))}
                </div>
              </section>
            )
          })}
        </div>
      )}
    </div>
  )
}
