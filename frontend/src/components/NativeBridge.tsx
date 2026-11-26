import { useEffect, useRef } from 'react'
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
  // navigate 는 화면을 옮길 때마다 새로 만들어진다. 앱 껍데기는 한 번만 켜고 최신 navigate 를 쓴다
  const navigateRef = useRef(navigate)
  useEffect(() => {
    navigateRef.current = navigate
  }, [navigate])

  useEffect(
    () =>
      startAppShell({
        navigate: (path) => navigateRef.current(path),
        // 앱을 보고 있을 때 알림이 오면 (누가 참여 등) 보고 있는 화면을 새로 불러온다
        onPushReceived: () => void qc.invalidateQueries(),
      }),
    [qc],
  )

  // 로그인하면(앱을 열 때마다) 이 폰의 알림 토큰을 서버에 알린다. 처음이면 알림 권한을 묻는다
  // 약관 동의 전에는 서버가 등록을 받지 않는다(403) — 동의하면 그때 등록한다
  const termsAgreed = member?.termsAgreed ?? false
  useEffect(() => {
    if (!isApp || status !== 'authed' || memberId === null || !termsAgreed) return
    // 이미 허락했으면 등록만 한다. 처음 묻는 것은 첫 참여·열기 뒤 (askPushOnce)
    void enablePush(sendPushToken, { ask: false }).catch(() => {})
  }, [status, memberId, termsAgreed])

  return null
}
