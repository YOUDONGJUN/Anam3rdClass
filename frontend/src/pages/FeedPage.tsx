import { Link, useSearchParams } from 'react-router'
import { ChevronRight, Globe2, Megaphone, Plus, School, Trophy } from 'lucide-react'
import { useMeetups, useNotices, useRanking } from '../api/queries'
import type { Board, MeetupSummary } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BoardHero, BoardSheet, HeroTabs } from '../components/BoardHero'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { NextDeparture } from '../components/NextDeparture'
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
      className={`${cardClass} mb-6 flex items-center gap-3 px-3.5 py-3 transition-colors hover:bg-surface-2`}
    >
      <span className="flex shrink-0 items-center gap-1.5 rounded-lg bg-exit px-2 py-1 text-xs font-extrabold text-on-exit shadow-[inset_0_-2px_0_rgb(0_0_0/0.14)]">
        <Megaphone className="size-3.5" aria-hidden />
        안내
      </span>
      <span className="min-w-0 flex-1 truncate text-[15px] font-semibold text-ink">{pinned.title}</span>
      <ChevronRight className="size-4 shrink-0 text-ink-3" aria-hidden />
    </Link>
  )
}

/** 이번 달 출석왕 한 줄 → 랭킹 (기록이 없으면 보이지 않는다) */
function RankingTeaser() {
  const { data } = useRanking('month', 'all')
  const top = data?.categories.find((c) => c.category === 'ATTEND')?.top[0]
  if (!top) return null
  return (
    <Link
      to="/ranking"
      className={`${cardClass} mb-6 flex items-center gap-3 px-3.5 py-3 transition-colors hover:bg-surface-2`}
    >
      <span className="flex shrink-0 items-center gap-1.5 rounded-lg bg-board px-2 py-1 text-xs font-extrabold text-on-board shadow-[inset_0_-2px_0_rgb(0_0_0/0.25)]">
        <Trophy className="size-3.5 text-accent" aria-hidden />
        이번 달
      </span>
      <span className="min-w-0 flex-1 truncate text-[15px] text-ink">
        출석왕 <b className="font-bold">{top.name}</b>
        <span className="text-ink-3"> · {top.school.shortName} · {top.value}번</span>
      </span>
      <ChevronRight className="size-4 shrink-0 text-ink-3" aria-hidden />
    </Link>
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
        ? 'IT연합회 학교 누구나 보고 참여해요.'
        : `${schoolName} 원우끼리, 수업 끝나고 3교시는 여기서.`

  // 다가오는 3교시: 가장 먼저 모이는 하나는 "다음 출발"로 크게, 나머지는 날짜별로
  const next = scope === 'upcoming' && data && data.length > 0 ? data[0] : null
  const rest = data ? (next ? data.slice(1) : data) : []

  return (
    <div>
      <BoardHero eyebrow={formatDate(now)} title={title} subtitle={subtitle}>
        <HeroTabs<Board>
          label="게시판"
          value={board}
          onChange={(b) => go({ board: b })}
          tabs={[
            { value: 'school', label: schoolName, Icon: School },
            { value: 'all', label: '연합', Icon: Globe2 },
          ]}
        />
      </BoardHero>

      <BoardSheet>
        <PinnedNotice />
        {scope === 'upcoming' && <RankingTeaser />}

        <div className="mb-3 flex items-center justify-between gap-4 px-1">
          <h2 className="text-[19px] font-extrabold tracking-[-0.02em] text-ink">
            {scope === 'past' ? '지난 기록' : '다음 출발'}
          </h2>
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
            {next && <NextDeparture meetup={next} now={now} />}
            {groupByDay(rest).map((g) => {
              const label = formatDayLabel(g.items[0].meetAt, now)
              const date = formatDate(g.items[0].meetAt)
              return (
                <section key={g.key} aria-label={label}>
                  <h2 className="mb-2.5 flex items-baseline gap-2 px-1">
                    <span className="text-[17px] font-extrabold text-ink">{label}</span>
                    {label !== date && <span className="text-sm text-ink-3">{date}</span>}
                    <span className="ml-auto text-sm text-ink-3">{g.items.length}개</span>
                  </h2>
                  <div className="flex flex-col gap-3">
                    {g.items.map((m) => (
                      <div key={m.id} className={`${cardClass} overflow-hidden`}>
                        <MeetupRow meetup={m} now={now} backLabel="3교시 목록" />
                      </div>
                    ))}
                  </div>
                </section>
              )
            })}
          </div>
        )}
      </BoardSheet>
    </div>
  )
}
