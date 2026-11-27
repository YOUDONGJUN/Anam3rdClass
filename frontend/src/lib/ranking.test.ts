import { describe, expect, it } from 'vitest'
import { buildIcs, foldIcsLine, googleCalendarUrl, icsText, icsTime } from './calendar'
import { MAP_PROVIDERS } from './map'
import { optionsSummary, seatsToAdd } from './meetup'
import { canEditOwnName, cohortLocked, profileEditRule, profileLockedUntil } from './person'
import { mergePlaces } from './places'
import { crownLabel, rankingValue, schoolRate } from './ranking'

describe('랭킹 표시', () => {
  it('횟수와 성사율', () => {
    expect(rankingValue('HOST', { value: 3, of: null })).toBe('3번')
    expect(rankingValue('RATE', { value: 2, of: 3 })).toBe('67% · 2/3')
  })
  it('학교 대항 참여율 (가입 원우가 없으면 0)', () => {
    expect(schoolRate({ members: 40, active: 10 })).toBe(25)
    expect(schoolRate({ members: 0, active: 0 })).toBe(0)
  })
  it('배지 이름', () => {
    expect(crownLabel('HOST')).toBe('방장왕')
    expect(crownLabel('ATTEND')).toBe('출석왕')
  })
})

describe('캘린더', () => {
  const event = {
    id: 12,
    title: '수업 끝나고 치맥, 한잔?',
    place: '안암역 2번 출구; 치킨집',
    meetAt: '2026-03-03T13:00:00.000Z',
    url: 'https://anam3.example/meetups/12',
  }

  it('UTC 시각과 글자 이스케이프', () => {
    expect(icsTime(new Date('2026-03-03T13:00:00.000Z'))).toBe('20260303T130000Z')
    expect(icsText('a,b;c\\d\ne')).toBe('a\\,b\\;c\\\\d\\ne')
  })

  it('1시간 전 알림이 있는 일정, 2시간 길이', () => {
    const ics = buildIcs(event, new Date('2026-03-02T09:00:00Z'))
    expect(ics).toContain('DTSTART:20260303T130000Z\r\n')
    expect(ics).toContain('DTEND:20260303T150000Z\r\n')
    expect(ics).toContain('TRIGGER:-PT1H\r\n')
    expect(ics).toContain('UID:meetup-12@anam3\r\n')
    expect(ics).toContain('LOCATION:안암역 2번 출구\\; 치킨집\r\n')
    expect(ics.startsWith('BEGIN:VCALENDAR\r\n')).toBe(true)
    expect(ics.endsWith('END:VCALENDAR\r\n')).toBe(true)
  })

  it('긴 줄은 75바이트로 접고 한글을 자르지 않는다', () => {
    const line = `SUMMARY:${'가'.repeat(40)}`
    const folded = foldIcsLine(line)
    const parts = folded.split('\r\n ')
    expect(parts.length).toBeGreaterThan(1)
    const encoder = new TextEncoder()
    parts.forEach((p, i) => expect(encoder.encode(p).length).toBeLessThanOrEqual(i === 0 ? 75 : 74))
    expect(parts.join('')).toBe(line)
  })

  it('구글 캘린더 링크', () => {
    const url = new URL(googleCalendarUrl(event))
    expect(url.hostname).toBe('calendar.google.com')
    expect(url.searchParams.get('action')).toBe('TEMPLATE')
    expect(url.searchParams.get('text')).toBe('[안암3교시] 수업 끝나고 치맥, 한잔?')
    expect(url.searchParams.get('dates')).toBe('20260303T130000Z/20260303T150000Z')
    expect(url.searchParams.get('location')).toBe(event.place)
  })
})

describe('장소 추천', () => {
  const named = (name: string) => ({ name, location: null, region: null })
  const names = (list: { name: string }[]) => list.map((p) => p.name)

  it('많이 쓴 장소 먼저, 겹치는 기본 장소는 한 번만, 8개까지', () => {
    expect(names(mergePlaces([named('참살이길'), named(' 개운사길 ')], ['안암역 2번 출구', '참살이길']))).toEqual([
      '참살이길',
      '개운사길',
      '안암역 2번 출구',
    ])
    expect(mergePlaces([], Array.from({ length: 12 }, (_, i) => `곳${i}`))).toHaveLength(8)
    expect(names(mergePlaces([named('Pub  A')], ['pub a']))).toEqual(['Pub  A'])
  })

  it('많이 쓴 장소의 위치·지역은 그대로 가져온다', () => {
    const located = { name: '참살이 치킨', location: { lat: 37.5868, lng: 127.0297 }, region: '서울 성북구' }
    expect(mergePlaces([located], ['참살이 치킨'])).toEqual([located])
  })
})

describe('정원 늘리기 · 옵션 요약', () => {
  it('기다리는 사람만큼, 30명까지', () => {
    expect(seatsToAdd(4, 2)).toBe(2)
    expect(seatsToAdd(4, 0)).toBe(1)
    expect(seatsToAdd(29, 5)).toBe(1)
    expect(seatsToAdd(30, 3)).toBe(0)
  })
  it('접힌 옵션 한 줄', () => {
    expect(optionsSummary({ minPeople: 3, maxPeople: 6, audience: 'SCHOOL', lateJoin: false, fee: null })).toBe(
      '3~6명 · 우리 학교 · 참여비 없음',
    )
    expect(optionsSummary({ minPeople: null, maxPeople: null, audience: 'ALL', lateJoin: true, fee: 10000 })).toBe(
      '인원 무관 · 연합 · 중간 합류 OK · 참여비 10,000원',
    )
    expect(optionsSummary({ minPeople: 4, maxPeople: 4, audience: 'COHORT', lateJoin: false, fee: null })).toBe(
      '4명 · 우리 기수만 · 참여비 없음',
    )
  })
})

describe('내 정보 고치기 규칙', () => {
  const now = new Date('2026-03-10T00:00:00Z')
  it('이름은 대학원장만', () => {
    expect(canEditOwnName('OWNER')).toBe(true)
    expect(canEditOwnName('MANAGER')).toBe(false)
    expect(canEditOwnName('USER')).toBe(false)
  })
  it('원우는 30일 제한이 끝날 때까지 잠긴다', () => {
    expect(profileLockedUntil({ profileEditableAt: null }, now)).toBeNull()
    expect(profileLockedUntil({ profileEditableAt: '2026-03-01T00:00:00Z' }, now)).toBeNull()
    expect(profileLockedUntil({ profileEditableAt: '2026-04-01T09:00:00Z' }, now)?.toISOString()).toBe(
      '2026-04-01T09:00:00.000Z',
    )
  })
  it('원우의 기수는 가입한 날(하루)이 지나면 잠긴다 · 교수·대학원장은 언제나', () => {
    expect(cohortLocked({ cohortEditableUntil: null }, now)).toBe(false)
    expect(cohortLocked({ cohortEditableUntil: '2026-03-10T09:00:00Z' }, now)).toBe(false)
    expect(cohortLocked({ cohortEditableUntil: '2026-03-09T09:00:00Z' }, now)).toBe(true)
  })
  it('역할마다 안내', () => {
    expect(profileEditRule('USER')).toContain('30일')
    expect(profileEditRule('MANAGER')).toContain('대학원장에게')
    expect(profileEditRule('OWNER')).toContain('언제든')
  })
})

describe('지도 바로가기', () => {
  it('장소 글자로 카카오맵 · 네이버지도 검색을 연다', () => {
    const [kakao, naver] = MAP_PROVIDERS
    expect(kakao.label).toBe('카카오맵')
    expect(kakao.url(' 안암역 2번 출구 & 치킨집 ')).toBe(
      `https://map.kakao.com/link/search/${encodeURIComponent('안암역 2번 출구 & 치킨집')}`,
    )
    expect(naver.label).toBe('네이버지도')
    expect(new URL(naver.url('참살이길 #2')).pathname).toBe(`/p/search/${encodeURIComponent('참살이길 #2')}`)
  })
})
