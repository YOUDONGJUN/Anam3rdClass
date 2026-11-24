import { describe, expect, it } from 'vitest'
import { maskName } from './person'

describe('maskName', () => {
  it('서버와 같은 규칙으로 가운데를 가린다', () => {
    expect(maskName('김고대')).toBe('김*대')
    expect(maskName('이수')).toBe('이*')
    expect(maskName('남궁고대')).toBe('남**대')
    expect(maskName('한')).toBe('*')
  })
})
