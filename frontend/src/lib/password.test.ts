import { describe, expect, it } from 'vitest'
import { passwordProblem } from './password'

describe('passwordProblem', () => {
  it('영문과 숫자를 섞어 8자 이상, 확인과 같아야 한다', () => {
    expect(passwordProblem('anam3rd2026', 'anam3rd2026')).toBeNull()
    expect(passwordProblem('abcdefgh', 'abcdefgh')).toBe('비밀번호는 영문과 숫자를 섞어 8자 이상으로 정해 주세요')
    expect(passwordProblem('12345678', '12345678')).toBe('비밀번호는 영문과 숫자를 섞어 8자 이상으로 정해 주세요')
    expect(passwordProblem('abc123', 'abc123')).toBe('비밀번호는 영문과 숫자를 섞어 8자 이상으로 정해 주세요')
    expect(passwordProblem('anam3rd2026', 'anam3rd2025')).toBe('비밀번호 확인이 맞지 않아요')
  })
})
