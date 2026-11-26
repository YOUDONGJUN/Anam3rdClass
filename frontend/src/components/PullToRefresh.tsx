import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { isApp } from '../lib/native'

/** 이만큼 당기면 새로 불러온다 (px) */
const TRIGGER = 80

/**
 * 앱에서 맨 위에서 아래로 당기면 보고 있는 화면을 새로 불러온다 (웹 브라우저는 자기 당겨서 새로고침이 있다).
 * 창(dialog)이 열려 있으면 하지 않는다
 */
export function PullToRefresh() {
  const qc = useQueryClient()
  const [pull, setPull] = useState(0)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    if (!isApp) return
    let startY: number | null = null
    let distance = 0
    const onStart = (e: TouchEvent) => {
      startY = window.scrollY <= 0 && !document.querySelector('dialog[open]') ? e.touches[0].clientY : null
      distance = 0
    }
    const onMove = (e: TouchEvent) => {
      if (startY === null) return
      distance = Math.max(0, e.touches[0].clientY - startY)
      if (window.scrollY > 0) distance = 0 // 당기다 위로 밀어 올렸으면 취소
      setPull(Math.min(distance, 120))
    }
    const onEnd = () => {
      if (startY !== null && distance >= TRIGGER) {
        setRefreshing(true)
        void qc.refetchQueries({ type: 'active' }).finally(() => setRefreshing(false))
      }
      startY = null
      distance = 0
      setPull(0)
    }
    window.addEventListener('touchstart', onStart, { passive: true })
    window.addEventListener('touchmove', onMove, { passive: true })
    window.addEventListener('touchend', onEnd)
    window.addEventListener('touchcancel', onEnd)
    return () => {
      window.removeEventListener('touchstart', onStart)
      window.removeEventListener('touchmove', onMove)
      window.removeEventListener('touchend', onEnd)
      window.removeEventListener('touchcancel', onEnd)
    }
  }, [qc])

  if (pull === 0 && !refreshing) return null
  const ready = pull >= TRIGGER
  return (
    <div
      className="pointer-events-none fixed inset-x-0 top-[calc(env(safe-area-inset-top)+4.5rem)] z-50 flex justify-center"
      style={{ transform: `translateY(${refreshing ? 16 : pull / 4}px)` }}
      role="status"
      aria-label={refreshing ? '새로 불러오는 중' : ready ? '놓으면 새로 불러와요' : '당겨서 새로고침'}
    >
      <span className={`flex size-10 items-center justify-center rounded-full bg-surface shadow-lg ${ready || refreshing ? 'text-accent-ink' : 'text-ink-3'}`}>
        <RefreshCw
          className={`size-5 ${refreshing ? 'animate-spin' : ''}`}
          style={refreshing ? undefined : { transform: `rotate(${pull * 3}deg)` }}
          aria-hidden
        />
      </span>
    </div>
  )
}
