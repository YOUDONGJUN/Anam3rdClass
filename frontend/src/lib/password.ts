/** 이메일 로그인 비밀번호 규칙 (서버 AuthDtos.PASSWORD_PATTERN 과 같다): 영문과 숫자를 섞어 8~64자 */
export const PASSWORD_RULE = '영문과 숫자를 섞어 8자 이상'

export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < 8 || password.length > 64 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return '비밀번호는 영문과 숫자를 섞어 8자 이상으로 정해 주세요'
  }
  if (password !== confirm) return '비밀번호 확인이 맞지 않아요'
  return null
}
