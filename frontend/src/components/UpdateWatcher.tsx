import { useCallback, useEffect, useRef } from 'react'
import { useLocation } from 'react-router'
import { isEditingPath, loadedMainScript, mainScriptOf } from '../lib/appVersion'

/** 너무 자주 묻지 않게: 한 번 보면 1분은 쉰다 (처음 열 때는 이미 새 버전이라 1분 뒤부터) */
const MIN_GAP_MS = 60_000

/**
 * 탭을 오래 열어 둬도 예전 화면에 머물지 않게: 다시 볼 때(앱 전환 뒤 돌아옴)와 화면을 옮길 때
 * 새 버전이 나왔는지 보고, 나왔으면 새로고침한다. 글을 쓰는 화면에서는 다른 화면으로 옮길 때까지 미룬다.
 */
export function UpdateWatcher() {
  const { pathname } = useLocation()
  // stale: 새 버전이 나왔는데 글 쓰는 화면이라 새로고침을 미뤄 둔 상태
  const watch = useRef({ loaded: null as string | null, lastCheck: 0, path: pathname, stale: false })

  useEffect(() => {
    watch.current.path = pathname
  }, [pathname])

  const check = useCallback(async () => {
    const w = watch.current
    if (!w.loaded || document.visibilityState !== 'visible' || Date.now() - w.lastCheck < MIN_GAP_MS) return
    w.lastCheck = Date.now()
    try {
      const res = await fetch('/', { cache: 'no-store' })
      const latest = res.ok ? mainScriptOf(await res.text()) : null
      if (!latest || latest === w.loaded) return
      if (isEditingPath(w.path)) w.stale = true
      else window.location.reload()
    } catch {
      // 오프라인 등은 다음에 다시 본다
    }
  }, [])

  useEffect(() => {
    watch.current.loaded = loadedMainScript() // 개발 서버에서는 null → 아무것도 안 한다
    watch.current.lastCheck = Date.now()
    const onShow = () => void check()
    document.addEventListener('visibilitychange', onShow)
    window.addEventListener('focus', onShow)
    return () => {
      document.removeEventListener('visibilitychange', onShow)
      window.removeEventListener('focus', onShow)
    }
  }, [check])

  // 화면을 옮길 때: 기다리던 새 버전이 있으면 새로고침, 아니면 한 번 물어본다
  useEffect(() => {
    if (watch.current.stale && !isEditingPath(pathname)) window.location.reload()
    else void check()
  }, [pathname, check])

  return null
}
