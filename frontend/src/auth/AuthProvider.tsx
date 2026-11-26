import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, ApiError, getToken, setToken, setUnauthorizedHandler, subscribeTokenChange, type SignOutReason } from '../api/client'
import type { AuthResponse, Member } from '../api/types'
import { clearReturnPath, clearSignupProgress } from '../lib/kakao'
import { currentPushToken } from '../lib/native'
import { AuthContext, type AuthState } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [member, setMember] = useState<Member | null>(null)
  const [status, setStatus] = useState<AuthState['status']>(() => (getToken() ? 'loading' : 'guest'))
  /** 지금 로그인한 원우 — 다른 사람으로 로그인하면 앞사람 화면 데이터(캐시)를 지운다 */
  const memberIdRef = useRef<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [offline, setOffline] = useState(false)
  /** 다시 시도: 늘리면 내 정보를 바로 다시 불러온다 */
  const [attemptKey, setAttemptKey] = useState(0)

  const signOut = useCallback(() => {
    // 앱: 이 폰으로 그 사람 알림을 그만 보낸다 (로그인 없이 토큰만으로)
    const pushToken = currentPushToken()
    if (pushToken) void api('/api/push/unregister', { method: 'POST', body: { token: pushToken } }).catch(() => {})
    setToken(null)
    setMember(null)
    setStatus('guest')
    memberIdRef.current = null
    clearSignupProgress()
    clearReturnPath()
    qc.clear()
  }, [qc])

  const signIn = useCallback(
    (res: AuthResponse) => {
      if (memberIdRef.current !== null && memberIdRef.current !== res.member.id) qc.clear()
      memberIdRef.current = res.member.id
      clearSignupProgress()
      setToken(res.token)
      setMember(res.member)
      setStatus('authed')
    },
    [qc],
  )

  useEffect(() => {
    setUnauthorizedHandler((reason: SignOutReason) => {
      setNotice(
        reason === 'banned'
          ? '이용이 제한된 계정이에요. 교수·대학원장에게 문의해 주세요'
          : '로그인이 만료됐어요. 다시 로그인해 주세요',
      )
      signOut()
    })
    return () => setUnauthorizedHandler(null)
  }, [signOut])

  // 다른 탭에서 로그아웃하면 이 탭도 로그아웃, 다른 탭에서 로그인하면 새로 불러온다
  useEffect(
    () =>
      subscribeTokenChange((next) => {
        if (!next) {
          setMember(null)
          setStatus('guest')
          memberIdRef.current = null
          qc.clear()
        } else {
          setStatus('loading')
          setAttemptKey((n) => n + 1)
        }
      }),
    [qc],
  )

  // 화면으로 돌아오면 내 정보를 다시 읽는다 (교수 임명·해임, 이름·기수 수정이 앱을 다시 켜지 않아도 보이게). 1분에 한 번까지
  useEffect(() => {
    if (status !== 'authed') return
    let last = Date.now()
    const refresh = () => {
      if (document.visibilityState !== 'visible' || Date.now() - last < 60_000) return
      last = Date.now()
      api<Member>('/api/me')
        .then((me) => setMember(me))
        .catch(() => {}) // 401 은 client 가 로그아웃시킨다
    }
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener('focus', refresh)
    return () => {
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener('focus', refresh)
    }
  }, [status])

  // 저장된 토큰이 있으면 내 정보를 불러와 로그인 상태를 복원.
  // 토큰이 무효(401)일 때만 로그아웃하고, 네트워크 오류·서버 깨우는 중이면 잠시 후 다시 시도한다.
  useEffect(() => {
    if (!getToken()) return
    let alive = true
    let timer: number | undefined
    const load = (attempt: number) => {
      api<Member>('/api/me')
        .then((me) => {
          if (!alive) return
          memberIdRef.current = me.id
          setOffline(false)
          setMember(me)
          setStatus('authed')
        })
        .catch((e: unknown) => {
          if (!alive) return
          if (e instanceof ApiError && [401, 403, 404].includes(e.status)) {
            signOut()
            return
          }
          // 인터넷이 안 되면 스피너만 돌지 않게 알린다 (계속 다시 시도한다)
          if (e instanceof ApiError && e.code === 'NETWORK') setOffline(true)
          timer = window.setTimeout(() => load(attempt + 1), Math.min(10_000, 1000 * 2 ** attempt))
        })
    }
    load(0)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [signOut, attemptKey])

  const retry = useCallback(() => setAttemptKey((n) => n + 1), [])
  const clearNotice = useCallback(() => setNotice(null), [])

  const value = useMemo<AuthState>(
    () => ({ status, member, signIn, signOut, updateMember: setMember, notice, clearNotice, offline, retry }),
    [status, member, signIn, signOut, notice, clearNotice, offline, retry],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
