import { useEffect, useState, useSyncExternalStore } from 'react'
import { isSlowNow, subscribeSlow } from '../api/client'

/** 일정 간격으로 갱신되는 현재 시각 (남은 시간 표시용) */
export function useNow(intervalMs = 30_000): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return now
}

/** 서버 응답이 3.5초 넘게 늦어지는 중인지 (무료 서버 깨우는 중) */
export function useSlowServer(): boolean {
  return useSyncExternalStore(subscribeSlow, isSlowNow, () => false)
}
