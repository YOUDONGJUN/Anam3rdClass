/** 회사 (법인 등기 전이라 (주) 없이) */
export const COMPANY = '써클'

/**
 * 공식 서비스 이름. 로그인 전 화면 · 링크 미리보기 · 앱 이름 · 약관 · 인증 메일에 쓴다.
 * 바꿀 때는 여기와 index.html · 앱 이름(capacitor) · 인증 메일(SmtpVerificationMailer) · 약관을 함께 고친다
 */
export const SERVICE = '안암3교시'

/** 화면 이름: 우리 학교 동네 + 3교시 (안암3교시 · 신촌3교시 · 서강3교시 · 혜화3교시 · 왕십리3교시). 동네가 없으면 공식 이름 */
export function serviceName(school?: { place?: string | null } | null): string {
  const place = school?.place?.trim()
  return place ? `${place}3교시` : SERVICE
}

/** 만든 사람 (내 정보 맨 아래 크레딧) */
export const MADE_BY = { name: '유동준', affiliation: '고려대 SW·AI융합대학원 61기' } as const
