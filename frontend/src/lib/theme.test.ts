import { describe, expect, it } from 'vitest'
import { resolveTheme } from './theme'

describe('resolveTheme', () => {
  it('기본값은 학교 색 + 자동 밝기', () => {
    expect(resolveTheme({ color: 'school', mode: 'system' }, 'KU', false)).toEqual({
      theme: 'school',
      school: 'KU',
      mode: 'light',
    })
    expect(resolveTheme({ color: 'school', mode: 'system' }, 'SKKU', true).mode).toBe('dark')
  })

  it('기본 색을 고르면 학교와 상관없이 기본 테마', () => {
    expect(resolveTheme({ color: 'basic', mode: 'light' }, 'YONSEI', true)).toEqual({
      theme: 'basic',
      school: 'YONSEI',
      mode: 'light',
    })
  })

  it('밝기를 직접 고르면 휴대폰 설정을 따르지 않는다', () => {
    expect(resolveTheme({ color: 'school', mode: 'dark' }, 'HANYANG', false).mode).toBe('dark')
    expect(resolveTheme({ color: 'school', mode: 'light' }, 'HANYANG', true).mode).toBe('light')
  })

  it('테마가 없는 학교(관리 화면에서 새로 넣은 학교)나 로그인 전에는 기본 색', () => {
    expect(resolveTheme({ color: 'school', mode: 'system' }, null, false)).toEqual({
      theme: 'basic',
      school: null,
      mode: 'light',
    })
    expect(resolveTheme({ color: 'school', mode: 'system' }, 'KOOKMIN', false).theme).toBe('basic')
  })
})
