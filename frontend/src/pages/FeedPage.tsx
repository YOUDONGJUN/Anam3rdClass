import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ChevronRight, Globe2, Megaphone, Plus, School, Trophy } from 'lucide-react'
import { PAST_PAGE, useMeetups, useMyMeetups, useNotices, useRanking } from '../api/queries'
import type { Board, GeoPoint, MeetupSummary } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BoardHero, BoardSheet, HeroTabs } from '../components/BoardHero'
import { FeedFilterBar, FeedToolbar, FilterSheet, OriginNote, SortSheet } from '../components/FeedControls'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { NextDeparture } from '../components/NextDeparture'
import { EmptyState, ErrorState, Segmented } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import {
  EMPTY_FEED_FILTER,
  type FeedFilter,
  filterMeetups,
  isFiltering,
  normalizeFilter,
  sheetFilterCount,
} from '../lib/feedFilter'
import { type FeedSort, favoriteCategories, isFeedSort, sortMeetups, usesDistance } from '../lib/feedSort'
import { distanceMeters, refreshLocationIfGranted, requestMyLocation, useMyLocation } from '../lib/geo'
import { useNow } from '../lib/hooks'
import { readDraft, writeDraft } from '../lib/leaveGuard'
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
/** 정렬은 이 폰에 기억한다 (다음에 열어도 그대로) */
const SORT_KEY = 'anam3.feedSort'

function readSort(): FeedSort {
  try {
    const v = window.localStorage.getItem(SORT_KEY)
    return isFeedSort(v) ? v : 'soon'
  } catch {
    return 'soon'
  }
}

function writeSort(sort: FeedSort) {
  try {
    window.localStorage.setItem(SORT_KEY, sort)
  } catch {
    // 저장이 막혀 있으면 이번에만
  }
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
  const [filter, setFilterState] = useState<FeedFilter>(() => normalizeFilter(readDraft<Partial<FeedFilter>>(FILTER_KEY)))
  const setFilter = (next: FeedFilter) => {
    setFilterState(next)
    writeDraft(FILTER_KEY, next)
  }
  const [sort, setSortState] = useState<FeedSort>(readSort)
  const [sheet, setSheet] = useState<'sort' | 'filter' | null>(null)
  const past = scope === 'past'
  const federated = board === 'all'
  // 지난 3교시에는 '참여 가능만'·'언제'가 의미 없고, 서버가 준 순서(최근 것부터) 그대로 본다.
  // 학교 거르기는 연합 게시판에서만
  const activeFilter = useMemo<FeedFilter>(
    () => ({
      ...filter,
      ...(past ? { joinable: false, when: 'all' as const } : {}),
      ...(federated ? {} : { schools: [] }),
    }),
    [filter, past, federated],
  )
  const activeSort: FeedSort = past ? 'soon' : sort

  // 거리 기준점: 내 위치(이 폰 안에서만), 없으면 우리 학교 캠퍼스
  const geo = useMyLocation()
  const campus = member?.campus ?? null
  const origin: GeoPoint | null = geo.point ?? campus
  const distanceActive = usesDistance(activeSort) || activeFilter.radiusKm !== null
  // 거리를 쓰는 중이고 이미 허락한 적이 있으면 묻지 않고 내 위치를 받아 둔다
  useEffect(() => {
    if (distanceActive) void refreshLocationIfGranted()
  }, [distanceActive])

  const mine = useMyMeetups()
  const favorites = useMemo(() => favoriteCategories(mine.data ?? []), [mine.data])
  const data = useMemo(() => {
    if (!loaded) return loaded
    const filtered = filterMeetups(loaded, activeFilter, now, origin)
    if (past) return filtered
    return sortMeetups(filtered, activeSort, {
      now,
      origin,
      myCohortLabel: member?.cohortLabel ?? null,
      favoriteCategories: favorites,
    })
  }, [loaded, activeFilter, past, now, origin, activeSort, member?.cohortLabel, favorites])
  const filtering = isFiltering(activeFilter)
  const canLoadMore = past && (loaded?.length ?? 0) >= pastLimit
  const schoolName = member?.school.shortName ?? '우리 학교'
  // 줄마다 거리: 내 위치를 받았거나, 거리를 쓰는 정렬·거르기 중일 때 (우리 학교 기준)
  const showDistance = origin !== null && (geo.point !== null || distanceActive)
  const distanceOf = (m: MeetupSummary) =>
    showDistance && origin && m.location ? distanceMeters(origin, m.location) : null

  function setSort(next: FeedSort) {
    setSortState(next)
    writeSort(next)
    // 가까운 순은 고르는 순간 내 위치를 묻는다 (거절하면 우리 학교 기준)
    if (next === 'near' && geo.point === null) void requestMyLocation()
  }

  function setRadius(km: FeedFilter['radiusKm']) {
    setFilter({ ...filter, radiusKm: km })
    if (km !== null && geo.point === null) void requestMyLocation()
  }

  const originNote = (
    <OriginNote geo={geo} campusName={schoolName} hasCampus={campus !== null} onUseMyLocation={() => void requestMyLocation()} />
  )

  function go(next: { scope?: 'upcoming' | 'past'; board?: Board }) {
    const s = next.scope ?? scope
    const b = next.board ?? board
    const p: Record<string, string> = {}
    if (s === 'past') p.scope = 'past'
    if (b === 'all') p.board = 'all'
    setPastLimit(PAST_PAGE)
    setParams(p, { replace: true })
  }

  const title = scope === 'past' ? (federated ? '지난 연합 3교시' : '지난 3교시') : federated ? '연합 3교시' : '열린 3교시'
  const subtitle =
    scope === 'past'
      ? '성사되어 실제로 모였던 3교시예요.'
      : federated
        ? 'IT연합회 학교 누구나 보고 참여해요.'
        : `${schoolName} 원우끼리, 수업 끝나고 3교시는 여기서.`

  // 곧 모이는 순: 가장 먼저 모이는 하나는 "다음 출발"로 크게(거르는 중이 아니면), 나머지는 날짜별로.
  // 다른 정렬은 날짜를 섞어 한 줄로 (줄마다 날짜를 보인다)
  const byDay = activeSort === 'soon'
  const next = !past && byDay && !filtering && data && data.length > 0 ? data[0] : null
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

        <div className="mb-4 flex flex-col gap-2.5">
          <FeedFilterBar value={filter} onChange={setFilter} past={past} />
          <FeedToolbar
            sort={activeSort}
            showSort={!past}
            count={data ? data.length : null}
            filterCount={sheetFilterCount(activeFilter)}
            onOpenSort={() => setSheet('sort')}
            onOpenFilter={() => setSheet('filter')}
          />
          {distanceActive && originNote}
        </div>
        {sheet === 'sort' && (
          <SortSheet
            value={sort}
            onChange={(s) => {
              setSort(s)
              setSheet(null)
            }}
            onClose={() => setSheet(null)}
          />
        )}
        {sheet === 'filter' && (
          <FilterSheet
            value={activeFilter}
            // 창 안의 조건만 바꾼다 (지난 3교시에서 열어도 '참여 가능만'·'언제'·다른 게시판의 학교는 그대로)
            onChange={(f) =>
              setFilter({
                ...filter,
                radiusKm: f.radiusKm,
                regions: f.regions,
                schools: federated ? f.schools : filter.schools,
                sizes: f.sizes,
                noFee: f.noFee,
                lateJoin: f.lateJoin,
              })
            }
            onClose={() => setSheet(null)}
            list={loaded ?? []}
            federated={federated}
            resultCount={data?.length ?? 0}
            originNote={originNote}
            onRadius={setRadius}
          />
        )}

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
            {!byDay && (
              <div className="flex flex-col gap-3">
                {rest.map((m) => (
                  <div key={m.id} className={`${cardClass} overflow-hidden`}>
                    <MeetupRow meetup={m} now={now} showDate backLabel="3교시 목록" distance={distanceOf(m)} />
                  </div>
                ))}
              </div>
            )}
            {byDay && groupByDay(rest).map((g) => {
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
                        <MeetupRow meetup={m} now={now} backLabel="3교시 목록" distance={distanceOf(m)} />
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
