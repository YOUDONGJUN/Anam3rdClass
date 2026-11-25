// 정산 화면 규칙. 금액 계산과 송금 링크 확인은 서버(Settlement · PayLinks)와 같다.

export const SETTLE_MIN = 1_000
export const SETTLE_MAX = 3_000_000
/** 1인 금액은 이 단위로 올림 */
export const ROUND_UNIT = 100

/** 1인 금액: 계산한 사람 포함 headcount 명으로 나누고 100원 단위로 올림 */
export function perPerson(total: number, headcount: number): number {
  if (headcount < 1) return 0
  return Math.ceil(total / headcount / ROUND_UNIT) * ROUND_UNIT
}

/** 33400 → "33,400원" */
export function formatWon(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`
}

/** "128,000원" · "128000" → 128000. 숫자가 없으면 null */
export function parseWon(input: string): number | null {
  const digits = input.replace(/[^0-9]/g, '')
  if (!digits) return null
  return Number(digits.slice(0, 9))
}

/** 총액 확인 (null 이면 괜찮다) */
export function totalError(total: number | null): string | null {
  if (total === null) return '총액을 적어 주세요'
  if (total < SETTLE_MIN || total > SETTLE_MAX) return '총액은 1,000원 ~ 300만 원으로 적어 주세요'
  return null
}

/** 참여비가 있으면 참여비 × 인원을 총액 기본값으로 */
export function defaultTotal(fee: number | null, headcount: number): number | null {
  return fee ? fee * headcount : null
}

const TOSS_PREFIX = /^(https?:\/\/)?(www\.)?toss\.me\//i
const TOSS_ID = /^[\p{L}\p{N}._-]{1,30}$/u
const KAKAOPAY = /^(https?:\/\/)?qr\.kakaopay\.com\/([A-Za-z0-9]{4,60})\/*$/i

/** "toss.me/아이디/" · "아이디" → "아이디". 비었으면 '' · 형식이 틀리면 null */
export function normalizeTossId(raw: string): string | null {
  const s = raw.trim().replace(TOSS_PREFIX, '').replace(/\/+$/, '')
  if (!s) return ''
  return TOSS_ID.test(s) ? s : null
}

/** "qr.kakaopay.com/Abc" → "https://qr.kakaopay.com/Abc". 비었으면 '' · 형식이 틀리면 null */
export function normalizeKakaoPayUrl(raw: string): string | null {
  const s = raw.trim()
  if (!s) return ''
  const m = KAKAOPAY.exec(s)
  return m ? `https://qr.kakaopay.com/${m[2]}` : null
}

/** 송금 링크 입력 확인. 괜찮으면 null */
export function payLinksError(tossId: string, kakaoPayUrl: string): string | null {
  if (normalizeTossId(tossId) === null) return '토스 아이디를 확인해 주세요 (예: toss.me/아이디)'
  if (normalizeKakaoPayUrl(kakaoPayUrl) === null) return '카카오페이 송금 링크를 확인해 주세요 (qr.kakaopay.com/… 으로 시작해요)'
  return null
}
