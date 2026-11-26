import { describe, expect, it } from 'vitest'
import type { MeetupSummary } from '../api/types'
import { EMPTY_FEED_FILTER, filterMeetups, isFiltering } from './feedFilter'

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
