import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, ApiError, getToken, setToken, setUnauthorizedHandler } from '../api/client'
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
    setUnauthorizedHandler(signOut)
    return () => setUnauthorizedHandler(null)
  }, [signOut])

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
          setMember(me)
          setStatus('authed')
        })
        .catch((e: unknown) => {
          if (!alive) return
          if (e instanceof ApiError && [401, 403, 404].includes(e.status)) {
            signOut()
            return
          }
          timer = window.setTimeout(() => load(attempt + 1), Math.min(10_000, 1000 * 2 ** attempt))
        })
    }
    load(0)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [signOut])

  const value = useMemo<AuthState>(
    () => ({ status, member, signIn, signOut, updateMember: setMember }),
    [status, member, signIn, signOut],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
