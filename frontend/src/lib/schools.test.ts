import { describe, expect, it } from 'vitest'
import { withEmailDomain } from './schools'

describe('withEmailDomain', () => {
  it('아이디 뒤에 학교 메일 주소를 붙인다', () => {
    expect(withEmailDomain('hong', 'korea.ac.kr')).toBe('hong@korea.ac.kr')
    expect(withEmailDomain(' hong ', 'korea.ac.kr')).toBe('hong@korea.ac.kr')
  })

  it('@ 뒤에 쓴 것은 바꾼다', () => {
    expect(withEmailDomain('hong@', 'korea.ac.kr')).toBe('hong@korea.ac.kr')
    expect(withEmailDomain('hong@ko', 'korea.ac.kr')).toBe('hong@korea.ac.kr')
    expect(withEmailDomain('hong@yonsei.ac.kr', 'g.skku.edu')).toBe('hong@g.skku.edu')
  })

  it('아직 아이디를 안 썼으면 주소만', () => {
    expect(withEmailDomain('', 'korea.ac.kr')).toBe('@korea.ac.kr')
  })
})
