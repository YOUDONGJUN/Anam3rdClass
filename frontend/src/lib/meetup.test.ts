import { describe, expect, it } from 'vitest'
import {
  addPurposes,
  categoryLabel,
  fitPeople,
  headcount,
  hiddenNamesNote,
  isReachedWith,
  joinedLabel,
  peopleError,
  peopleRule,
  seatsCaption,
  showsLateJoin,
  statusView,
  toggleCategory,
} from './meetup'

describe('지난 3교시 표시', () => {
  it('참여 표시: 모이는 중이면 "참여 중", 끝났으면 "참여함", 무산·취소면 없음', () => {
    expect(joinedLabel('RECRUITING')).toBe('참여 중')
    expect(joinedLabel('CONFIRMED')).toBe('참여 중')
    expect(joinedLabel('DONE')).toBe('참여함')
    expect(joinedLabel('FAILED')).toBeNull()
    expect(joinedLabel('CANCELED')).toBeNull()
  })

  it('중간 합류 OK 는 모이는 중일 때만', () => {
    expect(showsLateJoin({ lateJoin: true, status: 'CONFIRMED' })).toBe(true)
    expect(showsLateJoin({ lateJoin: true, status: 'DONE' })).toBe(false)
    expect(showsLateJoin({ lateJoin: true, status: 'CANCELED' })).toBe(false)
    expect(showsLateJoin({ lateJoin: false, status: 'RECRUITING' })).toBe(false)
  })

  it('가려진 이름 안내: 이미 성사·완료된 3교시에 "성사되면"이라 하지 않는다', () => {
    expect(hiddenNamesNote('RECRUITING')).toBe('성사되면 함께하는 사람끼리만 실명이 보여요.')
    expect(hiddenNamesNote('CONFIRMED')).toBe('참여한 사람끼리만 실명이 보여요.')
    expect(hiddenNamesNote('DONE')).toBe('함께한 사람끼리만 실명이 보여요.')
    expect(hiddenNamesNote('FAILED')).toBe('성사되지 않은 3교시라 이름은 계속 가려져요.')
  })
})

describe('fitPeople / peopleError', () => {
  const people = { minPeople: 3, noMinimum: false, maxPeople: 6, unlimited: false }

  it('"최소 인원 없음"을 켰다 끄면 최대 인원을 최소 인원에 맞춰 올린다', () => {
    // 최소 인원 없음을 켠 채 최대를 2명으로 줄임 → 최소(3)는 무시되니 그대로 둔다
    const lowered = fitPeople({ ...people, noMinimum: true, maxPeople: 2 })
    expect(lowered.maxPeople).toBe(2)
    // 다시 끄면 최소 3명 > 최대 2명이 되므로 최대를 3명으로
    expect(fitPeople({ ...lowered, noMinimum: false })).toMatchObject({ minPeople: 3, maxPeople: 3 })
  })

  it('최소 인원을 올리면 최대도 따라 오른다', () => {
    expect(fitPeople({ ...people, minPeople: 8 }).maxPeople).toBe(8)
    expect(fitPeople({ ...people, minPeople: 4 }).maxPeople).toBe(6)
  })

  it('인원 제한 없음이면 최대 인원은 보지 않는다', () => {
    expect(fitPeople({ ...people, unlimited: true, maxPeople: 2 }).maxPeople).toBe(2)
  })

  it('제출 직전 검사', () => {
    expect(peopleError(3, 2)).toBe('최대 인원은 최소 인원보다 같거나 많아야 해요')
    expect(peopleError(3, 3)).toBeNull()
    expect(peopleError(null, 2)).toBeNull()
    expect(peopleError(3, null)).toBeNull()
  })

  it('수정: 참여한 사람보다 최대를 줄일 수 없고, 성사된 뒤에는 최소를 지금 인원보다 올릴 수 없다', () => {
    const recruiting = { joined: 2, reached: false }
    const reached = { joined: 3, reached: true }
    expect(peopleError(2, 6, recruiting)).toBeNull() // 낮추면 바로 성사 — 괜찮다
    expect(peopleError(5, 6, recruiting)).toBeNull() // 성사 전에는 올려도 된다
    expect(peopleError(3, 2, reached)).toBe('최대 인원은 최소 인원보다 같거나 많아야 해요')
    expect(peopleError(2, 2, reached)).toBe('이미 3명이 참여했어요. 최대 인원을 3명보다 적게 줄일 수 없어요')
    expect(peopleError(4, 6, reached)).toBe('이미 성사된 3교시예요. 최소 인원을 지금 인원(3명)보다 늘릴 수 없어요')
    expect(peopleError(3, 3, reached)).toBeNull()
    expect(peopleError(null, null, reached)).toBeNull()
  })

  it('성사 여부는 서버와 같은 규칙', () => {
    expect(isReachedWith(null, 1)).toBe(true)
    expect(isReachedWith(3, 2)).toBe(false)
    expect(isReachedWith(3, 3)).toBe(true)
  })
})

const base = { participantCount: 2, minPeople: 3, maxPeople: 6 }

describe('seatsCaption', () => {
  it('성사 전에는 남은 인원을 알려준다', () => {
    expect(seatsCaption({ ...base, status: 'RECRUITING' })).toBe('성사까지 1명')
  })

  it('성사 후에는 남은 자리', () => {
    expect(seatsCaption({ ...base, participantCount: 4, status: 'CONFIRMED' })).toBe('성사! 2자리 남음')
    expect(seatsCaption({ ...base, participantCount: 6, status: 'CONFIRMED' })).toBe('정원이 다 찼어요')
  })

  it('인원 제한이 없으면 정원 마감이 없다', () => {
    expect(seatsCaption({ ...base, maxPeople: null, status: 'RECRUITING' })).toBe('성사까지 1명')
    expect(seatsCaption({ ...base, maxPeople: null, participantCount: 40, status: 'CONFIRMED' })).toBe('성사! 인원 제한 없어요')
    expect(headcount({ participantCount: 4, maxPeople: null })).toBe('4명')
    expect(headcount({ participantCount: 4, maxPeople: 6 })).toBe('4/6명')
  })

  it('최소 인원이 없으면 무조건 진행', () => {
    expect(seatsCaption({ participantCount: 1, minPeople: null, maxPeople: null, status: 'CONFIRMED' })).toBe(
      '무조건 진행 · 누구나 와요',
    )
    expect(seatsCaption({ participantCount: 2, minPeople: null, maxPeople: 4, status: 'CONFIRMED' })).toBe(
      '무조건 진행 · 2자리 남음',
    )
    expect(seatsCaption({ participantCount: 4, minPeople: null, maxPeople: 4, status: 'CONFIRMED' })).toBe(
      '정원이 다 찼어요',
    )
  })

  it('끝난 3교시', () => {
    expect(seatsCaption({ ...base, status: 'FAILED' })).toBe('인원이 모이지 않았어요')
    expect(seatsCaption({ ...base, participantCount: 4, status: 'DONE' })).toBe('4명이 함께했어요')
    expect(seatsCaption({ ...base, participantCount: 1, minPeople: null, status: 'DONE' })).toBe('함께한 사람이 없었어요')
    expect(seatsCaption({ ...base, status: 'CANCELED' })).toBe('취소된 3교시')
  })
})

describe('statusView', () => {
  it('성사됐지만 더 못 들어오면 마감 표시', () => {
    expect(statusView({ status: 'CONFIRMED', joinable: true, minPeople: 3 }).label).toBe('성사')
    expect(statusView({ status: 'CONFIRMED', joinable: false, minPeople: 3 }).label).toBe('성사 · 마감')
    expect(statusView({ status: 'RECRUITING', joinable: true, minPeople: 3 }).tone).toBe('accent')
  })

  it('최소 인원이 없으면 확정', () => {
    expect(statusView({ status: 'CONFIRMED', joinable: true, minPeople: null }).label).toBe('확정')
    expect(statusView({ status: 'CONFIRMED', joinable: false, minPeople: null }).label).toBe('확정 · 마감')
  })
})

describe('peopleRule', () => {
  it('최소·최대 인원을 한 줄로', () => {
    expect(peopleRule(3, 6)).toBe('나 포함 3명이 모이면 성사 · 최대 6명')
    expect(peopleRule(null, null)).toBe('최소 인원 없이 무조건 진행 · 인원 제한 없음')
  })
})

describe('종류 여러 개 고르기', () => {
  it('여러 개를 고르면 정해진 순서로 쌓인다', () => {
    expect(toggleCategory(['BEER'], 'SOJU')).toEqual(['SOJU', 'BEER'])
    expect(toggleCategory(['SOJU', 'BEER'], 'BEER')).toEqual(['SOJU'])
    expect(categoryLabel(['BEER', 'SOJU'])).toBe('소주 · 맥주')
  })

  it('아무거나는 혼자만', () => {
    expect(toggleCategory(['SOJU', 'BEER'], 'ANYTHING')).toEqual(['ANYTHING'])
    expect(toggleCategory(['ANYTHING'], 'WINE')).toEqual(['WINE'])
  })
})

describe('모임 목적 직접 쓰기', () => {
  it('쉼표로 나눠 붙이고 겹치는 건 뺀다', () => {
    expect(addPurposes(['친목'], ' 잡담, 친목 ,, 스터디 ')).toEqual(['친목', '잡담', '스터디'])
  })

  it('3개까지만, 하나에 12자까지만', () => {
    expect(addPurposes(['a', 'b', 'c'], 'd')).toEqual(['a', 'b', 'c'])
    expect(addPurposes([], '열세글자가넘는아주긴모임목적')[0]).toHaveLength(12)
  })
})
