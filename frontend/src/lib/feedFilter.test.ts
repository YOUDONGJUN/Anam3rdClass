import { describe, expect, it } from 'vitest'
import type { MeetupSummary } from '../api/types'
import {
  EMPTY_FEED_FILTER,
  filterMeetups,
  isFiltering,
  normalizeFilter,
  regionOptions,
  schoolOptions,
  sheetFilterCount,
  sizeBand,
} from './feedFilter'

const now = new Date('2026-03-02T09:00:00Z') // 3/2(월) 18:00 KST

function meetup(over: Partial<MeetupSummary>): MeetupSummary {
  return {
    id: 1,
    title: '치맥 번개',
    place: '안암역 앞',
    categories: ['BEER'],
    purposes: ['친목'],
    meetAt: '2026-03-02T13:00:00Z',
    deadlineAt: '2026-03-02T12:30:00Z',
    minPeople: 3,
    maxPeople: 6,
    participantCount: 1,
    status: 'RECRUITING',
    joinable: true,
    joined: false,
    hosting: false,
    ...over,
  } as MeetupSummary
}

describe('3교시 목록 거르기', () => {
  const list = [
    meetup({ id: 1 }),
    meetup({ id: 2, title: '와인 한잔', categories: ['WINE'], meetAt: '2026-03-03T11:00:00Z', joinable: false }),
    meetup({ id: 3, title: '아무거나', categories: ['ANYTHING'], meetAt: '2026-03-07T11:00:00Z', place: '참살이길' }),
    meetup({ id: 4, title: '카페', categories: ['NO_ALCOHOL'], meetAt: '2026-03-12T11:00:00Z', joined: true }),
  ]
  const ids = (f: Partial<typeof EMPTY_FEED_FILTER>) => filterMeetups(list, { ...EMPTY_FEED_FILTER, ...f }, now).map((m) => m.id)

  it('조건이 없으면 그대로', () => {
    expect(ids({})).toEqual([1, 2, 3, 4])
    expect(isFiltering(EMPTY_FEED_FILTER)).toBe(false)
  })

  it('찾는 말은 제목·장소·목적에서', () => {
    expect(ids({ q: '참살이' })).toEqual([3])
    expect(ids({ q: '친목' })).toEqual([1, 2, 3, 4])
  })

  it('참여 가능만: 들어갈 수 있고 아직 안 탄 것', () => {
    expect(ids({ joinable: true })).toEqual([1, 3])
  })

  it('언제: 오늘 · 내일 · 이번 주(7일)', () => {
    expect(ids({ when: 'today' })).toEqual([1])
    expect(ids({ when: 'tomorrow' })).toEqual([2])
    expect(ids({ when: 'week' })).toEqual([1, 2, 3])
  })

  it('종류: 아무거나는 어떤 술에도 나오고, 술 없이는 그것만', () => {
    expect(ids({ categories: ['WINE'] })).toEqual([2, 3])
    expect(ids({ categories: ['NO_ALCOHOL'] })).toEqual([4])
  })
})

describe('3교시 목록 거르기 창', () => {
  const anam = { lat: 37.5868, lng: 127.0297 }
  const ku = { id: 1, code: 'KU', name: '고려대학교', shortName: '고려대' }
  const yonsei = { id: 2, code: 'YONSEI', name: '연세대학교', shortName: '연세대' }
  const list = [
    meetup({ id: 1, location: anam, region: '서울 성북구', school: ku, maxPeople: 4, fee: null, lateJoin: false }),
    meetup({ id: 2, location: { lat: 37.5597, lng: 126.9423 }, region: '서울 서대문구', school: yonsei, maxPeople: 8, fee: 10000, lateJoin: true }),
    meetup({ id: 3, location: null, region: null, school: ku, maxPeople: null, fee: null, lateJoin: true }),
  ]
  const origin = { lat: 37.5894, lng: 127.0322 } // 고려대
  const ids = (f: Partial<typeof EMPTY_FEED_FILTER>, o: typeof origin | null = origin) =>
    filterMeetups(list, { ...EMPTY_FEED_FILTER, ...f }, now, o).map((m) => m.id)

  it('거리: 기준점에서 몇 km 안, 위치를 모르는 3교시는 빠진다', () => {
    expect(ids({ radiusKm: 1 })).toEqual([1])
    expect(ids({ radiusKm: 5 })).toEqual([1])
    // 신촌은 고려대에서 약 9km
    expect(filterMeetups(list, { ...EMPTY_FEED_FILTER, radiusKm: 5 }, now, { lat: 37.5658, lng: 126.9386 }).map((m) => m.id)).toEqual([2])
    // 기준점이 없으면 거리 조건은 무시
    expect(ids({ radiusKm: 1 }, null)).toEqual([1, 2, 3])
  })

  it('지역 · 학교 · 모임 크기 · 참여비 없음 · 중간 합류', () => {
    expect(ids({ regions: ['서울 서대문구'] })).toEqual([2])
    expect(ids({ schools: [1] })).toEqual([1, 3])
    expect(ids({ sizes: ['S'] })).toEqual([1])
    expect(ids({ sizes: ['M', 'L'] })).toEqual([2, 3])
    expect(ids({ noFee: true })).toEqual([1, 3])
    expect(ids({ lateJoin: true })).toEqual([2, 3])
    expect(ids({ noFee: true, lateJoin: true })).toEqual([3])
  })

  it('찾는 말은 지역에서도', () => {
    expect(ids({ q: '서대문' })).toEqual([2])
  })

  it('모임 크기는 최대 인원 기준, 제한 없음은 큰 모임', () => {
    expect(sizeBand({ maxPeople: 4 })).toBe('S')
    expect(sizeBand({ maxPeople: 5 })).toBe('M')
    expect(sizeBand({ maxPeople: 9 })).toBe('M')
    expect(sizeBand({ maxPeople: 10 })).toBe('L')
    expect(sizeBand({ maxPeople: null })).toBe('L')
  })

  it('창 안 조건 수와 거르는 중인지', () => {
    const f = { ...EMPTY_FEED_FILTER, radiusKm: 3 as const, regions: ['서울 성북구'], noFee: true }
    expect(sheetFilterCount(f)).toBe(3)
    expect(isFiltering(f)).toBe(true)
    expect(sheetFilterCount(EMPTY_FEED_FILTER)).toBe(0)
  })

  it('예전에 저장한 거르기도 빈 칸을 채워 받는다', () => {
    expect(normalizeFilter({ q: '치맥', joinable: true })).toEqual({ ...EMPTY_FEED_FILTER, q: '치맥', joinable: true })
    expect(normalizeFilter(null)).toEqual(EMPTY_FEED_FILTER)
    expect(normalizeFilter({ radiusKm: 7 as never }).radiusKm).toBeNull()
  })

  it('지역 칩은 많은 순, 고른 지역은 목록에 없어도 남는다', () => {
    expect(regionOptions(list, ['부산 해운대구'])).toEqual([
      { region: '서울 서대문구', count: 1 },
      { region: '서울 성북구', count: 1 },
      { region: '부산 해운대구', count: 0 },
    ])
    expect(schoolOptions(list).map((s) => [s.school.shortName, s.count])).toEqual([
      ['고려대', 2],
      ['연세대', 1],
    ])
  })
})
