import type { MeetupCategory, MeetupSummary } from '../api/types'
import { kstDayKey } from './time'

/** 3교시 목록 거르기: 찾는 말(제목·장소·목적), 참여 가능만, 언제, 마실 종류 */
export interface FeedFilter {
  q: string
  joinable: boolean
  when: 'all' | 'today' | 'tomorrow' | 'week'
  categories: MeetupCategory[]
}

export const EMPTY_FEED_FILTER: FeedFilter = { q: '', joinable: false, when: 'all', categories: [] }

export function isFiltering(f: FeedFilter): boolean {
  return f.q.trim() !== '' || f.joinable || f.when !== 'all' || f.categories.length > 0
}

/** 한국 시간 날짜로 오늘에서 며칠 뒤인지 */
function daysFrom(now: Date, iso: string): number {
  const day = (d: Date | string) => Date.parse(`${kstDayKey(d)}T00:00:00Z`)
  return Math.round((day(iso) - day(now)) / 86_400_000)
}

export function filterMeetups(list: readonly MeetupSummary[], f: FeedFilter, now: Date): MeetupSummary[] {
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
    if (!q) return true
    return [m.title, m.place, ...m.purposes].some((s) => s.toLowerCase().includes(q))
  })
}
