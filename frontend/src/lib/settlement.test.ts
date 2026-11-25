import { describe, expect, it } from 'vitest'
import {
  defaultTotal,
  formatWon,
  normalizeKakaoPayUrl,
  normalizeTossId,
  parseWon,
  payLinksError,
  perPerson,
  totalError,
} from './settlement'

describe('정산 금액', () => {
  it('계산한 사람 포함 인원으로 나누고 100원 단위로 올림', () => {
    expect(perPerson(100_000, 3)).toBe(33_400)
    expect(perPerson(128_000, 4)).toBe(32_000)
    expect(perPerson(90_000, 2)).toBe(45_000)
    expect(perPerson(1_001, 1)).toBe(1_100)
  })
  it('원 표시와 입력 읽기', () => {
    expect(formatWon(33_400)).toBe('33,400원')
    expect(parseWon('128,000원')).toBe(128_000)
    expect(parseWon(' 9 000 ')).toBe(9_000)
    expect(parseWon('원')).toBeNull()
  })
  it('총액은 1,000원 ~ 300만 원', () => {
    expect(totalError(null)).toContain('적어')
    expect(totalError(999)).toContain('1,000원')
    expect(totalError(3_000_001)).toContain('300만')
    expect(totalError(50_000)).toBeNull()
  })
  it('참여비가 있으면 참여비 × 인원이 기본 총액', () => {
    expect(defaultTotal(15_000, 4)).toBe(60_000)
    expect(defaultTotal(null, 4)).toBeNull()
  })
})

describe('송금 링크', () => {
  it('토스 아이디만 남긴다', () => {
    expect(normalizeTossId('https://toss.me/anam_kim/')).toBe('anam_kim')
    expect(normalizeTossId('toss.me/김고대')).toBe('김고대')
    expect(normalizeTossId('  ')).toBe('')
    expect(normalizeTossId('a b')).toBeNull()
    expect(normalizeTossId('x/../y')).toBeNull()
  })
  it('카카오페이는 qr.kakaopay.com 링크만', () => {
    expect(normalizeKakaoPayUrl('qr.kakaopay.com/Ab12Cd34')).toBe('https://qr.kakaopay.com/Ab12Cd34')
    expect(normalizeKakaoPayUrl('HTTPS://QR.KAKAOPAY.COM/FAbc1234/')).toBe('https://qr.kakaopay.com/FAbc1234')
    expect(normalizeKakaoPayUrl('https://evil.example/qr.kakaopay.com/abcd')).toBeNull()
    expect(normalizeKakaoPayUrl('')).toBe('')
  })
  it('입력 확인', () => {
    expect(payLinksError('anam_kim', '')).toBeNull()
    expect(payLinksError('a b', '')).toContain('토스')
    expect(payLinksError('', 'kakao.com/x')).toContain('카카오페이')
  })
})
