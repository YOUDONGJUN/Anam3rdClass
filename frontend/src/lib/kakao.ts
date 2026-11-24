// 카카오 로그인 (REST 방식, SDK 없이).
// 1) 카카오 로그인 창으로 보낸다 (state 를 세션에 적어 둔다)
// 2) 카카오가 /auth/kakao/callback?code=…&state=… 로 돌려보낸다 → state 확인 후 code 를 서버로
// 가입 중인 단계(가입 토큰)도 세션에 둬서 새로고침해도 이어진다.

const STATE_KEY = 'anam3.kakao.state'
const FROM_KEY = 'anam3.kakao.from'
const SIGNUP_KEY = 'anam3.signup'

export const KAKAO_CALLBACK_PATH = '/auth/kakao/callback'

export function kakaoRedirectUri(origin: string = window.location.origin): string {
  return `${origin}${KAKAO_CALLBACK_PATH}`
}

export function kakaoAuthorizeUrl(clientId: string, redirectUri: string, state: string): string {
  const params = new URLSearchParams({ client_id: clientId, redirect_uri: redirectUri, response_type: 'code', state })
  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`
}

function randomState(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

function session(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

/** 카카오 로그인 창으로 이동. 로그인 뒤 돌아갈 화면(from)도 기억한다 */
export function startKakaoLogin(clientId: string, from: string) {
  const state = randomState()
  session()?.setItem(STATE_KEY, state)
  session()?.setItem(FROM_KEY, from)
  window.location.assign(kakaoAuthorizeUrl(clientId, kakaoRedirectUri(), state))
}

/** 돌아온 state 가 내가 보낸 것인지 (다른 사이트가 만든 로그인 요청 막기). 한 번 쓰면 지운다 */
export function consumeKakaoState(state: string | null): boolean {
  const saved = session()?.getItem(STATE_KEY) ?? null
  session()?.removeItem(STATE_KEY)
  return Boolean(state) && saved === state
}

/** 로그인 뒤 돌아갈 화면을 기억한다 (이메일 로그인·비밀번호 정하기도 같은 곳으로 돌아간다) */
export function saveReturnPath(from: string) {
  session()?.setItem(FROM_KEY, from)
}

/** 로그아웃: 돌아갈 화면을 지운다 (같은 탭의 다음 사람이 이어받지 않게) */
export function clearReturnPath() {
  session()?.removeItem(FROM_KEY)
}

/** 로그인 뒤 돌아갈 화면 */
export function takeReturnPath(): string {
  const from = session()?.getItem(FROM_KEY) ?? '/'
  session()?.removeItem(FROM_KEY)
  return from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

/**
 * 가입 중인 단계 — 이메일 인증 전(email 없음) 또는 그 뒤(email 있음).
 * 이메일 인증 뒤: existing 이면 새 비밀번호, 아니면 이름·기수·학과(+ 이메일로 시작했으면 비밀번호)
 */
export interface SignupProgress {
  signupToken: string
  email: string | null
  /** 이메일 인증을 마친 학교 — 이 학교의 기수·학과를 고른다 */
  schoolId: number | null
  schoolName: string | null
  /** 어떻게 시작했나 (없으면 카카오 — 예전에 저장한 진행) */
  via?: 'kakao' | 'email'
  /** 비밀번호 찾기·만들기로 시작했나 (화면 문구만 다르다) */
  purpose?: 'signup' | 'password'
  /** 이미 가입한 학교 이메일 → 새 비밀번호 */
  existing?: boolean
  /** 미리 채워 둘 학교 이메일 (로그인한 채로 비밀번호를 만들 때) */
  presetEmail?: string | null
}

export function saveSignupProgress(progress: SignupProgress) {
  session()?.setItem(SIGNUP_KEY, JSON.stringify(progress))
}

export function loadSignupProgress(): SignupProgress | null {
  try {
    const raw = session()?.getItem(SIGNUP_KEY)
    return raw ? (JSON.parse(raw) as SignupProgress) : null
  } catch {
    return null
  }
}

export function clearSignupProgress() {
  session()?.removeItem(SIGNUP_KEY)
}
