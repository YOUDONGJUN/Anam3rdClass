import { describe, expect, it } from 'vitest'
import { SERVICE, serviceName } from './credit'

describe('화면 이름', () => {
  it('우리 학교 동네 + 3교시', () => {
    expect(serviceName({ place: '신촌' })).toBe('신촌3교시')
    expect(serviceName({ place: ' 왕십리 ' })).toBe('왕십리3교시')
  })

  it('동네가 없거나 로그인 전이면 공식 이름', () => {
    expect(serviceName({ place: null })).toBe(SERVICE)
    expect(serviceName({ place: '  ' })).toBe(SERVICE)
    expect(serviceName(null)).toBe(SERVICE)
    expect(SERVICE).toBe('안암3교시')
  })
})
