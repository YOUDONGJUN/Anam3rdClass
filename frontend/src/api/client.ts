// fetch 래퍼: 토큰 첨부, 에러를 한국어 메시지로 통일, 무료 서버가 깨어나는 중인지 알려준다.

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const TOKEN_KEY = 'anam3.token'
const SLOW_AFTER_MS = 3500

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

let token: string | null = readStoredToken()
/** 로그인이 풀린 까닭: 만료(401) · 이용 제한(403 BANNED) */
export type SignOutReason = 'expired' | 'banned'
let unauthorizedHandler: ((reason: SignOutReason) => void) | null = null

export function getToken() {
  return token
}

export function setToken(next: string | null) {
  token = next
  try {
    if (next) localStorage.setItem(TOKEN_KEY, next)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 시크릿 모드 등에서 저장이 막혀도 이번 탭에서는 계속 쓴다
  }
}

/** 로그인이 풀렸을 때(401 · 이용 제한) 호출할 함수. AuthProvider 가 등록한다 */
export function setUnauthorizedHandler(handler: ((reason: SignOutReason) => void) | null) {
  unauthorizedHandler = handler
}

/** 다른 탭에서 로그아웃·로그인하면 이 탭도 따라간다 (공용 PC에서 한 탭만 로그아웃되지 않게) */
export function subscribeTokenChange(listener: (next: string | null) => void) {
  const onStorage = (e: StorageEvent) => {
    if (e.key !== TOKEN_KEY) return
    token = e.newValue
    listener(e.newValue)
  }
  window.addEventListener('storage', onStorage)
  return () => window.removeEventListener('storage', onStorage)
}

// ----- 느린 요청 감지 (Render 무료 서버는 잠들었다 깨는 데 30초~1분) -----
let slowCount = 0
const slowListeners = new Set<(slow: boolean) => void>()

function changeSlow(delta: number) {
  const before = slowCount > 0
  slowCount += delta
  const after = slowCount > 0
  if (before !== after) slowListeners.forEach((fn) => fn(after))
}

export function subscribeSlow(listener: (slow: boolean) => void) {
  slowListeners.add(listener)
  return () => {
    slowListeners.delete(listener)
  }
}

export function isSlowNow() {
  return slowCount > 0
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let markedSlow = false
  const timer = setTimeout(() => {
    markedSlow = true
    changeSlow(1)
  }, SLOW_AFTER_MS)

  try {
    const res = await fetch(BASE + path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
    // 쓰는 동안 서버가 로그인을 늘려 주면(새 토큰) 바꿔 끼운다
    const renewed = res.headers.get('X-Renewed-Token')
    if (renewed && token) setToken(renewed)
    if (res.status === 204) return undefined as T

    const text = await res.text()
    let data: unknown = null
    try {
      data = text ? JSON.parse(text) : null
    } catch {
      // 서버가 깨어나는 중이면 HTML 에러 페이지가 올 수 있다
      throw new ApiError(res.status, 'BAD_RESPONSE', '서버가 아직 준비 중이에요. 잠시 후 다시 시도해 주세요')
    }

    if (!res.ok) {
      const err = (data ?? {}) as { code?: string; message?: string }
      if (token && !path.startsWith('/api/auth/')) {
        if (res.status === 401) unauthorizedHandler?.('expired')
        else if (res.status === 403 && err.code === 'BANNED') unauthorizedHandler?.('banned')
      }
      throw new ApiError(res.status, err.code ?? 'ERROR', err.message ?? '문제가 생겼어요. 잠시 후 다시 시도해 주세요')
    }
    return data as T
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw new ApiError(0, 'NETWORK', '서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요')
  } finally {
    clearTimeout(timer)
    if (markedSlow) changeSlow(-1)
  }
}
