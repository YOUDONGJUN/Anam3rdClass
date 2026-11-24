import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import {
  applyTheme,
  loadPrefs,
  loadSchoolCode,
  savePrefs,
  saveSchoolCode,
  type ThemeColor,
  type ThemeMode,
  type ThemePrefs,
} from '../lib/theme'
import { ThemeContext } from './ThemeContext'

/** 로그인한 사람의 학교 색으로 화면을 칠한다. 색·밝기 선택은 이 기기에만 저장된다 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { member } = useAuth()
  const [prefs, setPrefs] = useState<ThemePrefs>(loadPrefs)
  // 로그아웃해도 마지막 학교 색을 기억한다 (로그인 화면도 우리 학교 색으로)
  const schoolCode = member ? member.school.code : loadSchoolCode()

  useEffect(() => {
    if (member) saveSchoolCode(member.school.code)
  }, [member])

  useEffect(() => {
    applyTheme(prefs, schoolCode)
    if (prefs.mode !== 'system') return
    const media = window.matchMedia?.('(prefers-color-scheme: dark)')
    const onChange = () => applyTheme(prefs, schoolCode)
    media?.addEventListener('change', onChange)
    return () => media?.removeEventListener('change', onChange)
  }, [prefs, schoolCode])

  const update = useCallback((patch: Partial<ThemePrefs>) => {
    setPrefs((p) => {
      const next = { ...p, ...patch }
      savePrefs(next)
      return next
    })
  }, [])

  const value = useMemo(
    () => ({
      ...prefs,
      schoolCode,
      setColor: (color: ThemeColor) => update({ color }),
      setMode: (mode: ThemeMode) => update({ mode }),
    }),
    [prefs, schoolCode, update],
  )
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
