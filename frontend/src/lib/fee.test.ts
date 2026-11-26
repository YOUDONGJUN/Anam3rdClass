import { describe, expect, it } from 'vitest'
import { feeLabel, formatFee } from './meetup'

describe('참여비 표시', () => {
  it('정산 금액과 같이 쉼표로 쓴다', () => {
    expect(formatFee(10000)).toBe('10,000원')
    expect(formatFee(15000)).toBe('15,000원')
    expect(formatFee(5000)).toBe('5,000원')
  })

  it('선입금이면 붙인다', () => {
    expect(feeLabel(20000, true)).toBe('참여비 20,000원 · 선입금')
    expect(feeLabel(5000, false)).toBe('참여비 5,000원')
  })
})
