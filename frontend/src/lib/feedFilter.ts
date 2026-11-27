import type { GeoPoint, MeetupCategory, MeetupSummary, SchoolTag } from '../api/types'
import { distanceMeters } from './geo'
import { kstDayKey } from './time'

/** 정원 기준 모임 크기: 4명 이하 · 5~9명 · 10명 이상(제한 없음 포함) */
export type SizeBand = 'S' | 'M' | 'L'

export const SIZE_BANDS: readonly { value: SizeBand; label: string }[] = [
  { value: 'S', label: '4명 이하' },
  { value: 'M', label: '5~9명' },
  { value: 'L', label: '10명 이상' },
]

export const RADIUS_CHOICES = [1, 3, 5] as const
export type RadiusKm = (typeof RADIUS_CHOICES)[number]

/**
 * 3교시 목록 거르기.
 * 칩으로 바로: 찾는 말(제목·장소·목적), 참여 가능만, 언제, 마실 종류.
 * 거르기 창: 거리(기준점에서 몇 km 안), 지역(시·구), 연 사람 학교(연합), 모임 크기, 참여비 없음, 중간 합류 OK
 */
export interface FeedFilter {
  q: string
  joinable: boolean
  when: 'all' | 'today' | 'tomorrow' | 'week'
  categories: MeetupCategory[]
  /** 기준점(내 위치 · 우리 학교)에서 몇 km 안. null 이면 거르지 않는다 */
  radiusKm: RadiusKm | null
  /** 지역 (서울 성북구). 비면 거르지 않는다 */
  regions: string[]
  /** 연 사람 학교 번호 (연합 게시판에서만) */
  schools: number[]
  sizes: SizeBand[]
  noFee: boolean
  lateJoin: boolean
}

export const EMPTY_FEED_FILTER: FeedFilter = {
  q: '',
  joinable: false,
  when: 'all',
  categories: [],
  radiusKm: null,
  regions: [],
  schools: [],
  sizes: [],
  noFee: false,
  lateJoin: false,
}

/** 예전에 저장한 거르기(칸이 덜 있는 것)도 받는다 */
export function normalizeFilter(raw: Partial<FeedFilter> | null | undefined): FeedFilter {
  const f = { ...EMPTY_FEED_FILTER, ...(raw ?? {}) }
  return {
    ...f,
    categories: Array.isArray(f.categories) ? f.categories : [],
    radiusKm: RADIUS_CHOICES.includes(f.radiusKm as RadiusKm) ? f.radiusKm : null,
    regions: Array.isArray(f.regions) ? f.regions : [],
    schools: Array.isArray(f.schools) ? f.schools : [],
    sizes: Array.isArray(f.sizes) ? f.sizes : [],
  }
}

/** 거르기 창 안의 조건 수 (버튼 배지) */
export function sheetFilterCount(f: FeedFilter): number {
  return (
    (f.radiusKm !== null ? 1 : 0) +
    f.regions.length +
    f.schools.length +
    f.sizes.length +
    (f.noFee ? 1 : 0) +
    (f.lateJoin ? 1 : 0)
  )
}

export function isFiltering(f: FeedFilter): boolean {
  return f.q.trim() !== '' || f.joinable || f.when !== 'all' || f.categories.length > 0 || sheetFilterCount(f) > 0
}

export function sizeBand(m: Pick<MeetupSummary, 'maxPeople'>): SizeBand {
  if (m.maxPeople === null) return 'L'
  if (m.maxPeople <= 4) return 'S'
  if (m.maxPeople <= 9) return 'M'
  return 'L'
}

/** 한국 시간 날짜로 오늘에서 며칠 뒤인지 */
function daysFrom(now: Date, iso: string): number {
  const day = (d: Date | string) => Date.parse(`${kstDayKey(d)}T00:00:00Z`)
  return Math.round((day(iso) - day(now)) / 86_400_000)
}

/** origin: 거리 거르기의 기준점 (없으면 거리 조건은 무시한다) */
export function filterMeetups(
  list: readonly MeetupSummary[],
  f: FeedFilter,
  now: Date,
  origin: GeoPoint | null = null,
): MeetupSummary[] {
  const q = f.q.trim().toLowerCase()
  return list.filter((m) => {
    if (f.joinable && !(m.joinable && !m.joined && !m.hosting)) return false
    if (f.when !== 'all') {
      const d = daysFrom(now, m.meetAt)
      if (f.when === 'today' && d !== 0) return false
      if (f.when === 'tomorrow' && d !== 1) return false
      if (f.when === 'week' && (d < 0 || d > 6)) return false
    }
    // '아무거나'로 연 3교시는 어떤 종류를 골라도 나온다. '술 없이'는 그것만
    if (
      f.categories.length > 0 &&
      !m.categories.some((c) => f.categories.includes(c) || (c === 'ANYTHING' && !f.categories.includes('NO_ALCOHOL')))
    )
      return false
    if (f.radiusKm !== null && origin) {
      if (!m.location || distanceMeters(origin, m.location) > f.radiusKm * 1000) return false
    }
    if (f.regions.length > 0 && !(m.region && f.regions.includes(m.region))) return false
    if (f.schools.length > 0 && !(m.school && f.schools.includes(m.school.id))) return false
    if (f.sizes.length > 0 && !f.sizes.includes(sizeBand(m))) return false
    if (f.noFee && m.fee !== null) return false
    if (f.lateJoin && !m.lateJoin) return false
    if (!q) return true
    return [m.title, m.place, m.region ?? '', ...m.purposes].some((s) => s.toLowerCase().includes(q))
  })
}

/** 거르기 창의 지역 칩: 지금 목록에 있는 지역을 많은 순으로 (고른 것은 목록에 없어도 남긴다) */
export function regionOptions(list: readonly MeetupSummary[], selected: readonly string[]): { region: string; count: number }[] {
  const counts = new Map<string, number>()
  for (const m of list) if (m.region) counts.set(m.region, (counts.get(m.region) ?? 0) + 1)
  for (const r of selected) if (!counts.has(r)) counts.set(r, 0)
  return [...counts.entries()]
    .map(([region, count]) => ({ region, count }))
    .sort((a, b) => b.count - a.count || a.region.localeCompare(b.region, 'ko'))
}

/** 거르기 창의 학교 칩 (연합): 지금 목록에서 3교시를 연 학교 */
export function schoolOptions(list: readonly MeetupSummary[]): { school: SchoolTag; count: number }[] {
  const byId = new Map<number, { school: SchoolTag; count: number }>()
  for (const m of list) {
    if (!m.school) continue
    const row = byId.get(m.school.id)
    if (row) row.count += 1
    else byId.set(m.school.id, { school: m.school, count: 1 })
  }
  return [...byId.values()].sort((a, b) => b.count - a.count || a.school.shortName.localeCompare(b.school.shortName, 'ko'))
}
