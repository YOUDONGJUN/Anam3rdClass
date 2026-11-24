import { describe, expect, it } from 'vitest'
import { feeLabel, formatFee } from './meetup'

describe('참여비 표시', () => {
  it('만 원 단위는 "만 원", 나머지는 쉼표', () => {
    expect(formatFee(10000)).toBe('1만 원')
    expect(formatFee(30000)).toBe('3만 원')
    expect(formatFee(15000)).toBe('15,000원')
    expect(formatFee(5000)).toBe('5,000원')
  })

  it('선입금이면 붙인다', () => {
    expect(feeLabel(20000, true)).toBe('참여비 2만 원 · 선입금')
    expect(feeLabel(5000, false)).toBe('참여비 5,000원')
  })
})
