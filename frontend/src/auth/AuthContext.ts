import { createContext, useContext } from 'react'
import type { AuthResponse, Member } from '../api/types'

export interface AuthState {
  status: 'loading' | 'authed' | 'guest'
  member: Member | null
  signIn: (res: AuthResponse) => void
  signOut: () => void
  /** 내 정보를 고친 뒤 화면의 내 정보도 바꾼다 */
  updateMember: (member: Member) => void
  /** 저절로 로그아웃된 까닭 (첫 화면에 한 번 보여 준다) */
  notice: string | null
  clearNotice: () => void
  /** 들어가는 중인데 인터넷이 안 될 때 (스피너 대신 안내와 다시 시도) */
  offline: boolean
  retry: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('AuthProvider 안에서만 쓸 수 있어요')
  return ctx
}
