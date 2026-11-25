import { describe, expect, it } from 'vitest'
import { feedbackDeadline, percent, ratingShares } from './feedback'

describe('percent', () => {
  it('반올림한 백분율', () => {
    expect(percent(2, 3)).toBe(67)
    expect(percent(0, 5)).toBe(0)
  })

  it('알 수 없거나 분모가 0 이면 null', () => {
    expect(percent(1, 0)).toBeNull()
    expect(percent(null, 4)).toBeNull()
    expect(percent(3, null)).toBeNull()
  })
})

describe('ratingShares', () => {
  it('좋았어요 · 괜찮았어요 · 아쉬웠어요 순서, 0명인 칸은 뺀다', () => {
    const shares = ratingShares({ enough: true, good: 53, ok: 25, bad: 0 })
    expect(shares?.map((s) => [s.label, s.count, s.percent])).toEqual([
      ['좋았어요', 53, 68],
      ['괜찮았어요', 25, 32],
    ])
  })

  it('응답이 적어 숫자를 감춘 묶음이나 만족도 답이 없으면 null', () => {
    expect(ratingShares({ enough: false, good: null, ok: null, bad: null })).toBeNull()
    expect(ratingShares({ enough: true, good: 0, ok: 0, bad: 0 })).toBeNull()
  })
})

describe('feedbackDeadline', () => {
  const now = new Date('2026-03-02T16:00:00Z') // 3월 3일 01:00 (한국 시간)

  it('남은 날수 (하루가 안 남으면 오늘까지)', () => {
    expect(feedbackDeadline('2026-03-09T15:00:00Z', now)).toBe('6일 남음')
    expect(feedbackDeadline('2026-03-03T10:00:00Z', now)).toBe('오늘까지')
  })

  it('끝났으면 빈 글자', () => {
    expect(feedbackDeadline('2026-03-02T15:00:00Z', now)).toBe('')
  })
})
