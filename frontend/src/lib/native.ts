// 안드로이드 앱(Capacitor)에서만 쓰는 기능. 웹에서는 isApp=false 라 아무것도 하지 않는다.
// 앱은 이 웹 화면을 그대로 불러오고(capacitor.config.ts server.url), 아래 플러그인으로 폰 기능을 쓴다.
// ⚠️ 플러그인(@capacitor/*) 버전을 올리면 앱도 다시 빌드해서 올려야 한다 (웹과 앱의 플러그인 버전이 맞아야 한다).
import { Capacitor, registerPlugin, SystemBars, SystemBarsStyle, SystemBarType } from '@capacitor/core'
import { App } from '@capacitor/app'
import { PushNotifications } from '@capacitor/push-notifications'
import { Share } from '@capacitor/share'
import { SplashScreen } from '@capacitor/splash-screen'
import { api } from '../api/client'

export const isApp = Capacitor.isNativePlatform()

/** 알림 채널 (android strings.xml push_channel_id, 서버 FcmPushSender.CHANNEL_ID 와 같은 값) */
export const PUSH_CHANNEL = 'anam3'

export interface AppInfo {
  version: string
  build: number
  /** google-services.json 을 넣고 빌드한 앱인지 (없으면 푸시를 등록하면 앱이 꺼진다) */
  pushAvailable: boolean
  kakaoAvailable: boolean
}

/** 앱 전용 네이티브 기능 (android/app/src/main/java/kr/anam3/app/AnamNativePlugin.kt) */
interface AnamNativePlugin {
  info(): Promise<AppInfo>
  kakaoLogin(): Promise<{ accessToken: string }>
}

const AnamNative = registerPlugin<AnamNativePlugin>('AnamNative')

let infoRequest: Promise<AppInfo | null> | null = null

export function appInfo(): Promise<AppInfo | null> {
  if (!isApp) return Promise.resolve(null)
  infoRequest ??= AnamNative.info().catch(() => null)
  return infoRequest
}

function errorCode(e: unknown): string | null {
  return typeof e === 'object' && e !== null && 'code' in e ? String((e as { code?: unknown }).code) : null
}

/** 카카오톡 앱(없으면 카카오 계정 창)으로 로그인 → 카카오 액세스 토큰. 취소하면 null */
export async function kakaoAppToken(): Promise<string | null> {
  try {
    const { accessToken } = await AnamNative.kakaoLogin()
    return accessToken
  } catch (e) {
    if (errorCode(e) === 'CANCELLED') return null
    throw new Error(e instanceof Error && e.message ? e.message : '카카오 로그인에 실패했어요. 다시 시도해 주세요', {
      cause: e,
    })
  }
}

/** 이 앱(서버) 주소의 링크면 화면 경로(/meetups/12?x=1), 아니면 null */
export function appPathFromUrl(url: string, origin: string = window.location.origin): string | null {
  try {
    const u = new URL(url)
    if (u.origin !== origin) return null
    return u.pathname + u.search + u.hash
  } catch {
    return null
  }
}

export type ShareResult = 'shared' | 'copied' | 'cancelled' | 'failed'

/** 공유: 앱은 안드로이드 공유 창, 폰 브라우저는 공유 창, PC 는 링크 복사 */
export async function shareLink(title: string, text: string, url: string): Promise<ShareResult> {
  if (isApp) {
    try {
      await Share.share({ title, text, url, dialogTitle: '공유하기' })
      return 'shared'
    } catch {
      return 'cancelled'
    }
  }
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url })
      return 'shared'
    } catch {
      return 'cancelled' // 공유 창을 닫은 경우 등
    }
  }
  try {
    await navigator.clipboard.writeText(`${text}\n${url}`)
    return 'copied'
  } catch {
    return 'failed'
  }
}

// ---------------- 푸시 ----------------

let pushToken: string | null = null
let onPushToken: ((token: string) => void) | null = null
let registrationListening = false

/** 이 폰의 푸시 토큰 (등록한 뒤에만) — 로그아웃할 때 서버에서 지운다 */
export function currentPushToken(): string | null {
  return pushToken
}

/** 이 폰의 알림 토큰을 서버에 알린다 (로그인한 원우 앞으로) */
export function sendPushToken(token: string) {
  void api('/api/me/push/devices', { method: 'POST', body: { token, platform: 'android' } }).catch(() => {})
}

export type PushState = 'granted' | 'denied' | 'unavailable'

/** 지금 알림 권한 (앱이 아니거나 푸시를 못 쓰는 빌드면 unavailable) */
export async function pushPermission(): Promise<PushState> {
  const info = await appInfo()
  if (!info?.pushAvailable) return 'unavailable'
  const perm = await PushNotifications.checkPermissions()
  return perm.receive === 'granted' ? 'granted' : 'denied'
}

/**
 * 이 폰으로 알림 받기. 처음이면 알림 권한을 묻는다 (안드로이드 13+).
 * 토큰이 나오면 onToken 으로 알려 준다 → 서버에 등록
 */
export async function enablePush(onToken: (token: string) => void): Promise<PushState> {
  const info = await appInfo()
  if (!info?.pushAvailable) return 'unavailable'
  let perm = await PushNotifications.checkPermissions()
  if (perm.receive === 'prompt' || perm.receive === 'prompt-with-rationale') {
    perm = await PushNotifications.requestPermissions()
  }
  if (perm.receive !== 'granted') return 'denied'
  await PushNotifications.createChannel({
    id: PUSH_CHANNEL,
    name: '3교시·공지 알림',
    description: '참여·성사·시간 변경·마감·공지 알림',
    importance: 4,
    visibility: 1,
  })
  onPushToken = onToken
  if (!registrationListening) {
    registrationListening = true
    await PushNotifications.addListener('registration', (t) => {
      pushToken = t.value
      onPushToken?.(t.value)
    })
  }
  await PushNotifications.register()
  return 'granted'
}

// ---------------- 앱 껍데기: 시작 화면 · 상단바 · 뒤로가기 · 링크 · 알림 누르기 ----------------

interface ShellHandlers {
  /** 앱 안에서 화면 이동 (react-router navigate) */
  navigate: (path: string) => void
  /** 앱이 켜져 있을 때 알림이 옴 → 화면 데이터를 새로 */
  onPushReceived: () => void
}

/** 앱을 연 공유 링크는 한 번만 연다 (화면이 다시 그려져도 그 링크로 되돌아가지 않게) */
let launchUrlHandled = false

/** 앱이 켜질 때 한 번. 정리 함수를 돌려준다 */
export function startAppShell({ navigate, onPushReceived }: ShellHandlers): () => void {
  if (!isApp) return () => {}
  const cleanups: Array<() => void> = []
  const listen = (p: Promise<{ remove: () => Promise<void> }>) => {
    p.then((h) => cleanups.push(() => void h.remove())).catch(() => {})
  }

  void SplashScreen.hide().catch(() => {})

  // 위 상태바: 늘 어두운 안내판 위라 흰 아이콘. 아래 내비게이션 바: 화면 밝기에 맞춘다
  void SystemBars.setStyle({ style: SystemBarsStyle.Dark, bar: SystemBarType.StatusBar }).catch(() => {})
  const syncNavigationBar = () => {
    const dark = document.documentElement.dataset.mode === 'dark'
    void SystemBars.setStyle({
      style: dark ? SystemBarsStyle.Dark : SystemBarsStyle.Light,
      bar: SystemBarType.NavigationBar,
    }).catch(() => {})
  }
  syncNavigationBar()
  const observer = new MutationObserver(syncNavigationBar)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-mode'] })
  cleanups.push(() => observer.disconnect())

  // 안드로이드 뒤로가기: 열린 창 닫기 → 이전 화면 → 첫 화면이면 앱을 내린다
  listen(
    App.addListener('backButton', ({ canGoBack }) => {
      const dialog = document.querySelector<HTMLDialogElement>('dialog[open]')
      if (dialog) {
        dialog.close()
        return
      }
      const path = window.location.pathname
      if (path === '/' || path === '/welcome') {
        void App.minimizeApp()
      } else if (canGoBack) {
        window.history.back()
      } else {
        navigate('/')
      }
    }),
  )

  // 공유 링크(https://…/meetups/12)로 앱이 열림
  const openUrl = (url: string | undefined) => {
    const path = url ? appPathFromUrl(url) : null
    if (path && path !== window.location.pathname + window.location.search) navigate(path)
  }
  if (!launchUrlHandled) {
    launchUrlHandled = true
    void App.getLaunchUrl()
      .then((launch) => openUrl(launch?.url))
      .catch(() => {})
  }
  listen(App.addListener('appUrlOpen', ({ url }) => openUrl(url)))

  // 알림을 누르면 그 화면으로
  listen(
    PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
      const path = (notification.data as { path?: unknown } | undefined)?.path
      if (typeof path === 'string' && path.startsWith('/')) navigate(path)
    }),
  )
  listen(PushNotifications.addListener('pushNotificationReceived', () => onPushReceived()))

  return () => cleanups.forEach((fn) => fn())
}
