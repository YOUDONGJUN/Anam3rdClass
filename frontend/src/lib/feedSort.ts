// 다가오는 3교시 정렬. 목록은 모두 받아 폰에서 정렬한다 — 가까운 순에 내 위치를 서버로 보내지 않으려고.
import type { GeoPoint, MeetupCategory, MeetupSummary } from '../api/types'
import { distanceMeters } from './geo'

export type FeedSort = 'soon' | 'recommended' | 'near' | 'almost' | 'popular' | 'deadline'

export const FEED_SORTS: readonly { value: FeedSort; label: string; hint: string }[] = [
  { value: 'soon', label: '곧 모이는 순', hint: '모임 시간이 가까운 것부터' },
  { value: 'recommended', label: '추천순', hint: '곧 성사되고 가까운 3교시 먼저' },
  { value: 'near', label: '가까운 순', hint: '내 위치에서 가까운 것부터' },
  { value: 'almost', label: '곧 성사 순', hint: '최소 인원까지 남은 자리가 적은 것부터' },
  { value: 'popular', label: '인원 많은 순', hint: '지금 모인 사람이 많은 것부터' },
  { value: 'deadline', label: '마감 임박 순', hint: '참여 마감이 가까운 것부터' },
]

export function isFeedSort(v: unknown): v is FeedSort {
  return FEED_SORTS.some((s) => s.value === v)
}

export function sortLabel(sort: FeedSort): string {
  return FEED_SORTS.find((s) => s.value === sort)?.label ?? ''
}

/** 거리를 쓰는 정렬 (기준점이 필요하다) */
export function usesDistance(sort: FeedSort): boolean {
  return sort === 'near' || sort === 'recommended'
}

export interface SortContext {
  now: Date
  /** 거리 기준점: 내 위치, 없으면 우리 학교 캠퍼스 (둘 다 없으면 null) */
  origin: GeoPoint | null
  /** 내 기수 (61기) — 같은 기수가 연 3교시를 조금 위로 */
  myCohortLabel?: string | null
  /** 내가 자주 간 마실 종류 */
  favoriteCategories?: readonly MeetupCategory[]
}

const time = (iso: string) => new Date(iso).getTime()

function distanceOf(m: MeetupSummary, origin: GeoPoint | null): number {
  return origin && m.location ? distanceMeters(origin, m.location) : Number.POSITIVE_INFINITY
}

/** 최소 인원까지 남은 자리 (모집 중이고 최소 인원이 있을 때만, 아니면 무한) */
export function seatsToReach(m: Pick<MeetupSummary, 'status' | 'minPeople' | 'participantCount'>): number {
  if (m.status !== 'RECRUITING' || m.minPeople === null) return Number.POSITIVE_INFINITY
  return Math.max(0, m.minPeople - m.participantCount)
}

/** 추천 점수: 높을수록 위 */
export function recommendScore(m: MeetupSummary, ctx: SortContext): number {
  let score = 0
  const canJoin = m.joinable && !m.joined && !m.hosting
  // 이미 탄 3교시는 추천할 필요가 없고, 못 들어가는 3교시는 맨 뒤로
  if (!canJoin) score -= m.joined || m.hosting ? 50 : 100
  const left = seatsToReach(m)
  if (left === 1) score += 30
  else if (left === 2) score += 15
  const d = distanceOf(m, ctx.origin)
  if (d <= 1000) score += 25
  else if (d <= 3000) score += 15
  else if (d <= 5000) score += 8
  const hours = (time(m.meetAt) - ctx.now.getTime()) / 3_600_000
  if (hours <= 24) score += 20
  else if (hours <= 72) score += 10
  if (ctx.myCohortLabel && m.host?.cohortLabel === ctx.myCohortLabel) score += 10
  if (ctx.favoriteCategories?.length && m.categories.some((c) => ctx.favoriteCategories!.includes(c))) score += 10
  score += Math.min(m.participantCount, 5) * 2
  return score
}

/** 정렬한 새 목록 (같으면 곧 모이는 순) */
export function sortMeetups(list: readonly MeetupSummary[], sort: FeedSort, ctx: SortContext): MeetupSummary[] {
  const bySoon = (a: MeetupSummary, b: MeetupSummary) => time(a.meetAt) - time(b.meetAt) || a.id - b.id
  const now = ctx.now.getTime()
  const copy = [...list]
  switch (sort) {
    case 'soon':
      return copy.sort(bySoon)
    case 'deadline': {
      // 마감이 지난 것(중간 합류만 받는 것 등)은 뒤로
      const key = (m: MeetupSummary) => (time(m.deadlineAt) > now ? time(m.deadlineAt) : Number.POSITIVE_INFINITY)
      return copy.sort((a, b) => cmp(key(a), key(b)) || bySoon(a, b))
    }
    case 'popular':
      return copy.sort((a, b) => b.participantCount - a.participantCount || bySoon(a, b))
    case 'almost':
      return copy.sort((a, b) => cmp(seatsToReach(a), seatsToReach(b)) || b.participantCount - a.participantCount || bySoon(a, b))
    case 'near':
      return copy.sort((a, b) => cmp(distanceOf(a, ctx.origin), distanceOf(b, ctx.origin)) || bySoon(a, b))
    case 'recommended': {
      const scores = new Map(copy.map((m) => [m.id, recommendScore(m, ctx)]))
      return copy.sort((a, b) => scores.get(b.id)! - scores.get(a.id)! || bySoon(a, b))
    }
  }
}

/** 무한대끼리는 같다고 본다 (Infinity - Infinity 는 NaN) */
function cmp(a: number, b: number): number {
  if (a === b) return 0
  return a < b ? -1 : 1
}

/** 내가 함께한 3교시에서 자주 간 마실 종류 (많은 순 2개, '아무거나' 빼고) */
export function favoriteCategories(mine: readonly MeetupSummary[]): MeetupCategory[] {
  const counts = new Map<MeetupCategory, number>()
  for (const m of mine) {
    if (m.status === 'CANCELED' || m.status === 'FAILED') continue
    for (const c of m.categories) if (c !== 'ANYTHING') counts.set(c, (counts.get(c) ?? 0) + 1)
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 2)
    .map(([c]) => c)
}
