import { describe, expect, it } from 'vitest'
import { seatsCaption, statusView } from './meetup'

const base = { participantCount: 2, minPeople: 3, maxPeople: 6 }

describe('seatsCaption', () => {
  it('성사 전에는 남은 인원을 알려준다', () => {
    expect(seatsCaption({ ...base, status: 'RECRUITING' })).toBe('성사까지 1명')
  })

  it('성사 후에는 남은 자리', () => {
    expect(seatsCaption({ ...base, participantCount: 4, status: 'CONFIRMED' })).toBe('성사! 2자리 남음')
    expect(seatsCaption({ ...base, participantCount: 6, status: 'CONFIRMED' })).toBe('정원이 다 찼어요')
  })

  it('끝난 번개', () => {
    expect(seatsCaption({ ...base, status: 'FAILED' })).toBe('인원이 모이지 않았어요')
    expect(seatsCaption({ ...base, participantCount: 4, status: 'DONE' })).toBe('4명이 함께했어요')
    expect(seatsCaption({ ...base, status: 'CANCELED' })).toBe('취소된 번개')
  })
})

describe('statusView', () => {
  it('성사됐지만 더 못 들어오면 마감 표시', () => {
    expect(statusView({ status: 'CONFIRMED', joinable: true }).label).toBe('성사')
    expect(statusView({ status: 'CONFIRMED', joinable: false }).label).toBe('성사 · 마감')
    expect(statusView({ status: 'RECRUITING', joinable: true }).tone).toBe('accent')
  })
})
