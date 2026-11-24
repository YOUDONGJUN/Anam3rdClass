import { createContext, useContext } from 'react'
import type { ThemeColor, ThemeMode } from '../lib/theme'

export interface ThemeState {
  color: ThemeColor
  mode: ThemeMode
  /** 테마 색을 고른 학교 (로그인한 사람, 없으면 이 기기에서 마지막으로 로그인한 사람) */
  schoolCode: string | null
  setColor: (color: ThemeColor) => void
  setMode: (mode: ThemeMode) => void
}

export const ThemeContext = createContext<ThemeState | null>(null)

export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('ThemeProvider 안에서만 쓸 수 있어요')
  return ctx
}
