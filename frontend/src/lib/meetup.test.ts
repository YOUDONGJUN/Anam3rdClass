import { describe, expect, it } from 'vitest'
import {
  addPurposes,
  categoryLabel,
  fitPeople,
  headcount,
  peopleError,
  peopleRule,
  seatsCaption,
  statusView,
  toggleCategory,
} from './meetup'

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
