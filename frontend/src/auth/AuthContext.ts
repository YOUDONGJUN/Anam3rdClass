import { createContext, useContext } from 'react'
import type { AuthResponse, Member } from '../api/types'

export interface AuthState {
  status: 'loading' | 'authed' | 'guest'
  member: Member | null
  signIn: (res: AuthResponse) => void
  signOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('AuthProvider 안에서만 쓸 수 있어요')
  return ctx
}
