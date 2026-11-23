import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, ApiError, getToken, setToken, setUnauthorizedHandler } from '../api/client'
import type { AuthResponse, Member } from '../api/types'
import { AuthContext, type AuthState } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [member, setMember] = useState<Member | null>(null)
  const [status, setStatus] = useState<AuthState['status']>(() => (getToken() ? 'loading' : 'guest'))

  const signOut = useCallback(() => {
    setToken(null)
    setMember(null)
    setStatus('guest')
    qc.clear()
  }, [qc])

  const signIn = useCallback((res: AuthResponse) => {
    setToken(res.token)
    setMember(res.member)
    setStatus('authed')
  }, [])

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

  const value = useMemo<AuthState>(() => ({ status, member, signIn, signOut }), [status, member, signIn, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
