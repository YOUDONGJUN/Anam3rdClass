import { Link, useSearchParams } from 'react-router'
import { ChevronRight, Globe2, Megaphone, Plus, School } from 'lucide-react'
import { useMeetups, useNotices } from '../api/queries'
import type { Board, MeetupSummary } from '../api/types'
import { useAuth } from '../auth/AuthContext'
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

/** 우리 학교 게시판 · 연합 게시판 — 화면 맨 위 큰 탭 */
function BoardTabs({ board, onChange, schoolName }: { board: Board; onChange: (b: Board) => void; schoolName: string }) {
  const tabs: { value: Board; label: string; Icon: typeof School }[] = [
    { value: 'school', label: schoolName, Icon: School },
    { value: 'all', label: '연합', Icon: Globe2 },
  ]
  return (
    <div role="tablist" aria-label="게시판" className="mb-6 grid grid-cols-2 gap-1 rounded-2xl bg-surface-2 p-1">
      {tabs.map(({ value, label, Icon }) => {
        const active = board === value
        return (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(value)}
            className={`flex h-12 items-center justify-center gap-2 rounded-xl text-[15px] font-bold transition-colors ${
              active ? 'bg-board text-on-board shadow-[0_2px_8px_-2px_rgb(0_0_0/0.25)]' : 'text-ink-2 hover:text-ink'
            }`}
          >
            <Icon className="size-[18px]" aria-hidden />
            {label}
          </button>
        )
      })}
    </div>
  )
}

export function FeedPage() {
  const { member } = useAuth()
  const [params, setParams] = useSearchParams()
  const scope = params.get('scope') === 'past' ? 'past' : 'upcoming'
  const board: Board = params.get('board') === 'all' ? 'all' : 'school'
  const { data, isPending, isError, error, refetch } = useMeetups(scope, board)
  const now = useNow()
  const schoolName = member?.school.shortName ?? '우리 학교'

  function go(next: { scope?: 'upcoming' | 'past'; board?: Board }) {
    const s = next.scope ?? scope
    const b = next.board ?? board
    const p: Record<string, string> = {}
    if (s === 'past') p.scope = 'past'
    if (b === 'all') p.board = 'all'
    setParams(p, { replace: true })
  }

  const federated = board === 'all'
  const title = scope === 'past' ? (federated ? '지난 연합 3교시' : '지난 3교시') : federated ? '연합 3교시' : '열린 3교시'
  const subtitle =
    scope === 'past'
      ? '성사되어 실제로 모였던 3교시예요.'
      : federated
        ? 'IT연합회 다섯 학교 누구나 보고 참여해요.'
        : `${schoolName} 원우끼리, 수업 끝나고 3교시는 여기서.`

  return (
    <div>
      <PinnedNotice />

      <BoardTabs board={board} onChange={(b) => go({ board: b })} schoolName={schoolName} />

      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">{title}</h1>
          <p className="mt-1 text-[15px] text-ink-2">{subtitle}</p>
        </div>
        <Segmented
          label="3교시 보기"
          value={scope}
          onChange={(v) => go({ scope: v })}
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
              title={federated ? '아직 열린 연합 3교시가 없어요' : '아직 열린 3교시가 없어요'}
              body={
                federated
                  ? '다른 학교 원우와 만나 보세요'
                  : '안 모이면 조용히 사라져요. 부담 없이 열어 보세요'
              }
              action={
                <Link to={federated ? '/meetups/new?audience=ALL' : '/meetups/new'} className={buttonClass('primary')}>
                  <Plus className="size-4" strokeWidth={2.6} aria-hidden />
                  {federated ? '연합 3교시 열기' : '3교시 열기'}
                </Link>
              }
            />
          ) : (
            <EmptyState title="아직 성사된 3교시가 없어요" body="첫 성사의 주인공이 되어 보세요" />
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
