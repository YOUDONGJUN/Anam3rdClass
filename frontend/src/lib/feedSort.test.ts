import { describe, expect, it } from 'vitest'
import type { MeetupSummary } from '../api/types'
import { favoriteCategories, recommendScore, seatsToReach, sortMeetups, type SortContext } from './feedSort'

const now = new Date('2026-03-02T09:00:00Z') // 3/2(월) 18:00 KST
const ku = { lat: 37.5894, lng: 127.0322 }

function meetup(over: Partial<MeetupSummary>): MeetupSummary {
  return {
    id: 1,
    title: '치맥 번개',
    place: '안암역 앞',
    categories: ['BEER'],
    purposes: [],
    meetAt: '2026-03-02T13:00:00Z',
    deadlineAt: '2026-03-02T12:30:00Z',
    minPeople: 3,
    maxPeople: 6,
    participantCount: 1,
    status: 'RECRUITING',
    joinable: true,
    joined: false,
    hosting: false,
    host: { cohortLabel: '62기' },
    location: null,
    region: null,
    ...over,
  } as MeetupSummary
}

const ctx: SortContext = { now, origin: ku }
const ids = (list: MeetupSummary[], sort: Parameters<typeof sortMeetups>[1], c: SortContext = ctx) =>
  sortMeetups(list, sort, c).map((m) => m.id)

describe('3교시 정렬', () => {
  it('곧 모이는 순: 모임 시간, 같으면 번호', () => {
    const list = [
      meetup({ id: 3, meetAt: '2026-03-03T11:00:00Z' }),
      meetup({ id: 2, meetAt: '2026-03-02T13:00:00Z' }),
      meetup({ id: 1, meetAt: '2026-03-02T13:00:00Z' }),
    ]
    expect(ids(list, 'soon')).toEqual([1, 2, 3])
  })

  it('마감 임박 순: 마감이 지난 것은 뒤로', () => {
    const list = [
      meetup({ id: 1, deadlineAt: '2026-03-02T08:00:00Z', meetAt: '2026-03-02T10:00:00Z' }), // 마감 지남
      meetup({ id: 2, deadlineAt: '2026-03-03T08:00:00Z', meetAt: '2026-03-03T09:00:00Z' }),
      meetup({ id: 3, deadlineAt: '2026-03-02T10:00:00Z', meetAt: '2026-03-04T09:00:00Z' }),
    ]
    expect(ids(list, 'deadline')).toEqual([3, 2, 1])
  })

  it('인원 많은 순', () => {
    const list = [meetup({ id: 1, participantCount: 2 }), meetup({ id: 2, participantCount: 5 }), meetup({ id: 3, participantCount: 3 })]
    expect(ids(list, 'popular')).toEqual([2, 3, 1])
  })

  it('곧 성사 순: 최소 인원까지 남은 자리가 적은 것, 이미 성사·최소 없음은 뒤로', () => {
    const list = [
      meetup({ id: 1, minPeople: 4, participantCount: 1 }), // 3자리
      meetup({ id: 2, minPeople: 3, participantCount: 2 }), // 1자리
      meetup({ id: 3, minPeople: 3, participantCount: 3, status: 'CONFIRMED' }),
      meetup({ id: 4, minPeople: null, participantCount: 1 }),
    ]
    expect(ids(list, 'almost')).toEqual([2, 1, 3, 4])
    expect(seatsToReach(list[1])).toBe(1)
    expect(seatsToReach(list[3])).toBe(Number.POSITIVE_INFINITY)
  })

  it('가까운 순: 위치를 모르는 3교시는 뒤로 (모임 시간 순)', () => {
    const list = [
      meetup({ id: 1 }),
      meetup({ id: 2, location: { lat: 37.5597, lng: 126.9423 } }), // 신촌, 약 8km
      meetup({ id: 3, location: { lat: 37.5868, lng: 127.0297 } }), // 참살이길, 약 400m
      meetup({ id: 4, meetAt: '2026-03-02T12:00:00Z' }),
    ]
    expect(ids(list, 'near')).toEqual([3, 2, 4, 1])
    // 기준점이 없으면 모임 시간 순
    expect(ids(list, 'near', { now, origin: null })).toEqual([4, 1, 2, 3])
  })

  it('추천순: 들어갈 수 있고 곧 성사되는 가까운 3교시가 먼저, 못 들어가는 것은 맨 뒤', () => {
    const list = [
      meetup({ id: 1, joinable: false, participantCount: 6, maxPeople: 6 }), // 만석
      meetup({ id: 2, minPeople: 3, participantCount: 2, location: { lat: 37.5868, lng: 127.0297 } }), // 1자리 · 가까움
      meetup({ id: 3, minPeople: 5, participantCount: 1, meetAt: '2026-03-08T11:00:00Z' }), // 멀리 · 나중
      meetup({ id: 4, joined: true }), // 이미 탐
    ]
    expect(ids(list, 'recommended')).toEqual([2, 3, 4, 1])
  })

  it('추천순: 같은 기수가 연 것 · 자주 가는 종류가 조금 위로', () => {
    const base = meetup({ id: 1, categories: ['SOJU'] })
    const c: SortContext = { now, origin: null, myCohortLabel: '61기', favoriteCategories: ['WINE'] }
    const sameCohort = meetup({ id: 2, categories: ['SOJU'], host: { cohortLabel: '61기' } as MeetupSummary['host'] })
    const favorite = meetup({ id: 3, categories: ['WINE'] })
    expect(recommendScore(sameCohort, c)).toBeGreaterThan(recommendScore(base, c))
    expect(recommendScore(favorite, c)).toBeGreaterThan(recommendScore(base, c))
  })

  it('정렬은 새 목록을 만든다 (받은 목록은 그대로)', () => {
    const list = [meetup({ id: 2, participantCount: 1 }), meetup({ id: 1, participantCount: 3 })]
    sortMeetups(list, 'popular', ctx)
    expect(list.map((m) => m.id)).toEqual([2, 1])
  })
})

describe('자주 가는 마실 종류', () => {
  it('함께한 3교시에서 많은 순 2개 (아무거나 · 무산 · 취소 빼고)', () => {
    const mine = [
      meetup({ id: 1, categories: ['BEER', 'SOJU'], status: 'DONE' }),
      meetup({ id: 2, categories: ['BEER'], status: 'DONE' }),
      meetup({ id: 3, categories: ['WINE', 'ANYTHING'], status: 'CONFIRMED' }),
      meetup({ id: 4, categories: ['MAKGEOLLI'], status: 'CANCELED' }),
      meetup({ id: 5, categories: ['MAKGEOLLI'], status: 'FAILED' }),
      meetup({ id: 6, categories: ['SOJU'], status: 'DONE' }),
    ]
    expect(favoriteCategories(mine)).toEqual(['BEER', 'SOJU'])
    expect(favoriteCategories([])).toEqual([])
  })
})
