import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { enablePush, isApp, sendPushToken, startAppShell } from '../lib/native'

/**
 * 안드로이드 앱에서만 하는 일 (웹에서는 아무것도 안 한다):
 * 시작 화면 닫기, 상단바 색, 뒤로가기, 공유 링크·알림으로 화면 열기, 로그인하면 이 폰으로 알림 받기
 */
export function NativeBridge() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { status, member } = useAuth()
  const memberId = member?.id ?? null

  useEffect(
    () =>
      startAppShell({
        navigate: (path) => navigate(path),
        // 앱을 보고 있을 때 알림이 오면 (누가 참여 등) 보고 있는 화면을 새로 불러온다
        onPushReceived: () => void qc.invalidateQueries(),
      }),
    [navigate, qc],
  )

  // 로그인하면(앱을 열 때마다) 이 폰의 알림 토큰을 서버에 알린다. 처음이면 알림 권한을 묻는다
  useEffect(() => {
    if (!isApp || status !== 'authed' || memberId === null) return
    void enablePush(sendPushToken).catch(() => {})
  }, [status, memberId])

  return null
}
