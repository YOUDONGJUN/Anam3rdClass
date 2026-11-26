import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ChevronRight, Globe2, Megaphone, Plus, School, Search, Trophy, X } from 'lucide-react'
import { PAST_PAGE, useMeetups, useNotices, useRanking } from '../api/queries'
import type { Board, MeetupCategory, MeetupSummary } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BoardHero, BoardSheet, HeroTabs } from '../components/BoardHero'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { NextDeparture } from '../components/NextDeparture'
import { EmptyState, ErrorState, Segmented } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { EMPTY_FEED_FILTER, type FeedFilter, filterMeetups, isFiltering } from '../lib/feedFilter'
import { useNow } from '../lib/hooks'
import { readDraft, writeDraft } from '../lib/leaveGuard'
import { CATEGORY, CATEGORY_ORDER } from '../lib/meetup'
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

const FILTER_KEY = 'anam3.feedFilter'

/** 목록 거르기: 찾기 칸 + 가로로 미는 칩 (참여 가능만 · 언제 · 마실 종류) */
function FeedFilterBar({ value, onChange, past }: { value: FeedFilter; onChange: (f: FeedFilter) => void; past: boolean }) {
  const set = (patch: Partial<FeedFilter>) => onChange({ ...value, ...patch })
  const chip = (active: boolean) =>
    `hit inline-flex h-9 shrink-0 items-center gap-1.5 rounded-full border px-3 text-sm font-semibold transition-colors ${
      active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink-2 hover:text-ink'
    }`
  const toggleCategory = (c: MeetupCategory) =>
    set({ categories: value.categories.includes(c) ? value.categories.filter((x) => x !== c) : [...value.categories, c] })
  return (
    <div className="mb-4 flex flex-col gap-2.5">
      <label className="relative block">
        <span className="sr-only">3교시 찾기</span>
        <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
        <input
          type="search"
          enterKeyHint="search"
          value={value.q}
          onChange={(e) => set({ q: e.target.value })}
          placeholder="제목·장소·목적으로 찾기"
          className="h-11 w-full rounded-xl border border-line-strong bg-surface pr-10 pl-10 text-[15px] text-ink placeholder:text-ink-3 focus:border-ink-3 focus:outline-none"
        />
        {value.q && (
          <button
            type="button"
            aria-label="찾는 말 지우기"
            onClick={() => set({ q: '' })}
            className="hit absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-ink-3 hover:text-ink"
          >
            <X className="size-4" aria-hidden />
          </button>
        )}
      </label>
      <div className="-mx-4 flex gap-1.5 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0" role="group" aria-label="거르기">
        {!past && (
          <>
            <button type="button" aria-pressed={value.joinable} className={chip(value.joinable)} onClick={() => set({ joinable: !value.joinable })}>
              참여 가능만
            </button>
            {(
              [
                ['today', '오늘'],
                ['tomorrow', '내일'],
                ['week', '이번 주'],
              ] as const
            ).map(([when, label]) => (
              <button
                key={when}
                type="button"
                aria-pressed={value.when === when}
                className={chip(value.when === when)}
                onClick={() => set({ when: value.when === when ? 'all' : when })}
              >
                {label}
              </button>
            ))}
            <span className="mx-0.5 w-px shrink-0 self-stretch bg-line" aria-hidden />
          </>
        )}
        {CATEGORY_ORDER.map((c) => {
          const meta = CATEGORY[c]
          const active = value.categories.includes(c)
          return (
            <button key={c} type="button" aria-pressed={active} className={chip(active)} onClick={() => toggleCategory(c)}>
              <span className="size-2.5 rounded-full" style={{ backgroundColor: meta.color }} aria-hidden />
              {meta.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function FeedPage() {
  const { member } = useAuth()
  const [params, setParams] = useSearchParams()
  const scope = params.get('scope') === 'past' ? 'past' : 'upcoming'
  const board: Board = params.get('board') === 'all' ? 'all' : 'school'
  // 지난 3교시는 '더 보기'로 늘린다 (게시판·보기가 바뀌면 처음 개수로)
  const [pastLimit, setPastLimit] = useState(PAST_PAGE)
  const limit = scope === 'past' ? pastLimit : PAST_PAGE
  const { data: loaded, isError, error, refetch, isFetching } = useMeetups(scope, board, limit)
  const now = useNow()
  // 거르기는 이 탭에 기억한다 (상세를 보고 돌아와도 그대로)
  const [filter, setFilterState] = useState<FeedFilter>(() => readDraft<FeedFilter>(FILTER_KEY) ?? EMPTY_FEED_FILTER)
  const setFilter = (next: FeedFilter) => {
    setFilterState(next)
    writeDraft(FILTER_KEY, next)
  }
  // 지난 3교시에는 '참여 가능만'·'언제'가 의미 없다
  const activeFilter: FeedFilter = scope === 'past' ? { ...filter, joinable: false, when: 'all' } : filter
  const filtering = isFiltering(activeFilter)
  const data = loaded ? filterMeetups(loaded, activeFilter, now) : loaded
  const canLoadMore = scope === 'past' && (loaded?.length ?? 0) >= pastLimit
  const schoolName = member?.school.shortName ?? '우리 학교'

  function go(next: { scope?: 'upcoming' | 'past'; board?: Board }) {
    const s = next.scope ?? scope
    const b = next.board ?? board
    const p: Record<string, string> = {}
    if (s === 'past') p.scope = 'past'
    if (b === 'all') p.board = 'all'
    setPastLimit(PAST_PAGE)
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

  // 다가오는 3교시: 가장 먼저 모이는 하나는 "다음 출발"로 크게, 나머지는 날짜별로 (거르는 중이면 모두 같은 줄로)
  const next = scope === 'upcoming' && !filtering && data && data.length > 0 ? data[0] : null
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

        <FeedFilterBar value={filter} onChange={setFilter} past={scope === 'past'} />

        {!data && !isError ? (
          <div className={`${cardClass} divide-y divide-line`}>
            <MeetupRowSkeleton />
            <MeetupRowSkeleton />
            <MeetupRowSkeleton />
          </div>
        ) : !data ? (
          <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
        ) : data.length === 0 && filtering ? (
          <div className={cardClass}>
            <EmptyState
              title="조건에 맞는 3교시가 없어요"
              action={
                <button type="button" className={buttonClass('secondary')} onClick={() => setFilter(EMPTY_FEED_FILTER)}>
                  조건 지우기
                </button>
              }
            />
          </div>
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
            {canLoadMore && (
              <button
                type="button"
                className={buttonClass('secondary', 'md', 'self-center')}
                disabled={isFetching}
                onClick={() => setPastLimit((n) => n + PAST_PAGE)}
              >
                {isFetching ? '불러오는 중' : '지난 3교시 더 보기'}
              </button>
            )}
          </div>
        )}
      </BoardSheet>
    </div>
  )
}
