import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { keys } from '../api/queries'
import { startLive, stopLive, subscribeLive } from '../lib/live'

/** 로그인한 동안 실시간 연결을 켜 두고, 톡방 소식이 오면 안 읽은 수를 다시 센다 */
export function LiveBridge() {
  const qc = useQueryClient()
  useEffect(() => {
    const off = subscribeLive((e) => {
      // 다른 원우가 읽었다 · 공감 · 공지는 열린 톡방만 바꾼다 (내 안 읽은 수는 그대로)
      if (e.type === 'chat-reads' || e.type === 'chat-change') return
      void qc.invalidateQueries({ queryKey: keys.chats })
      if (e.type === 'connected') void qc.invalidateQueries({ queryKey: keys.unreadNotifications })
    })
    startLive()
    return () => {
      off()
      stopLive()
    }
  }, [qc])
  return null
}
