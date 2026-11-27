// 안드로이드 앱(Capacitor)에서만 쓰는 기능. 웹에서는 isApp=false 라 아무것도 하지 않는다.
// 앱은 이 웹 화면을 그대로 불러오고(capacitor.config.ts server.url), 아래 플러그인으로 폰 기능을 쓴다.
// ⚠️ 플러그인(@capacitor/*) 버전을 올리면 앱도 다시 빌드해서 올려야 한다 (웹과 앱의 플러그인 버전이 맞아야 한다).
import { Capacitor, registerPlugin, SystemBars, SystemBarsStyle, SystemBarType } from '@capacitor/core'
import { App } from '@capacitor/app'
import { Directory, Filesystem } from '@capacitor/filesystem'
import { Keyboard } from '@capacitor/keyboard'
import { PushNotifications } from '@capacitor/push-notifications'
import { Share } from '@capacitor/share'
import { SplashScreen } from '@capacitor/splash-screen'
import { api } from '../api/client'
import { confirmLeave } from './leaveGuard'

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
  /** 갤러리(사진/안암3교시)에 저장. data 는 base64 */
  savePhoto(options: { data: string; mimeType: string; fileName: string }): Promise<void>
}

const AnamNative = registerPlugin<AnamNativePlugin>('AnamNative')

let infoRequest: Promise<AppInfo | null> | null = null

/**
 * 아이폰 앱 키보드 위 ∧ ∨ 완료 막대. 톡방에서만 숨긴다 — 다른 화면(가입 · 3교시 만들기 …)에서는 "완료"가 키보드를 내리는 단추라 그대로 둔다.
 * 예전 앱(v1.2.0 이하)은 이 기능이 없어 그대로 보인다
 */
export function setKeyboardBar(visible: boolean) {
  if (Capacitor.getPlatform() !== 'ios') return
  void Keyboard.setAccessoryBarVisible({ isVisible: visible }).catch(() => {})
}

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

// ---------------- 사진 저장 ----------------

/** saved: 갤러리에 저장함 · shared: 공유 창을 띄움(거기서 저장) · downloaded: 파일로 내려받음 */
export type SaveResult = 'saved' | 'shared' | 'downloaded' | 'cancelled'

function base64Of(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(',')[1] ?? '')
    reader.onerror = () => reject(reader.error ?? new Error('사진을 읽지 못했어요'))
    reader.readAsDataURL(blob)
  })
}

/**
 * 사진 저장: 안드로이드 앱은 갤러리에 바로 · 아이폰 앱은 공유 창("이미지 저장") ·
 * 폰 브라우저는 공유 창 · PC 는 내려받기.
 * 예전 앱(이 기능이 없는 빌드)이면 업데이트하라고 알린다 (앱 기능은 앱을 새로 깔아야 생긴다)
 */
export async function savePhotoFile(blob: Blob, fileName: string): Promise<SaveResult> {
  const mimeType = blob.type || 'image/jpeg'
  const platform = Capacitor.getPlatform()
  if (platform === 'android') {
    try {
      await AnamNative.savePhoto({ data: await base64Of(blob), mimeType, fileName })
      return 'saved'
    } catch (e) {
      if (errorCode(e) === 'UNIMPLEMENTED') throw new Error('앱을 업데이트하면 사진을 저장할 수 있어요', { cause: e })
      throw new Error(e instanceof Error && e.message ? e.message : '사진을 저장하지 못했어요', { cause: e })
    }
  }
  if (platform === 'ios') {
    let uri: string | null = null
    try {
      uri = (await Filesystem.writeFile({ path: fileName, data: await base64Of(blob), directory: Directory.Cache })).uri
    } catch (e) {
      if (errorCode(e) !== 'UNIMPLEMENTED') throw e
      // 예전 앱: 아래 웹 방식(공유 창)으로
    }
    if (uri) {
      try {
        await Share.share({ files: [uri], dialogTitle: '사진 저장' })
        return 'shared'
      } catch {
        return 'cancelled'
      }
    }
  }
  const file = new File([blob], fileName, { type: mimeType })
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file] })
      return 'shared'
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return 'cancelled'
      throw e
    }
  }
  if (isApp) throw new Error('앱을 업데이트하면 사진을 저장할 수 있어요')
  const link = document.createElement('a')
  link.href = URL.createObjectURL(file)
  link.download = file.name
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 10_000)
  return 'downloaded'
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
export async function enablePush(onToken: (token: string) => void, { ask = true }: { ask?: boolean } = {}): Promise<PushState> {
  const info = await appInfo()
  if (!info?.pushAvailable) return 'unavailable'
  let perm = await PushNotifications.checkPermissions()
  if (ask && (perm.receive === 'prompt' || perm.receive === 'prompt-with-rationale')) {
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
      // 창 안에서 연 창(후기 창 → 신고 창)이면 맨 위 창만 닫는다
      const dialogs = document.querySelectorAll<HTMLDialogElement>('dialog[open]')
      const dialog = dialogs[dialogs.length - 1]
      if (dialog) {
        dialog.close()
        return
      }
      if (!confirmLeave()) return // 쓰던 내용이 있으면 묻는다
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

const PUSH_ASKED_KEY = 'anam3.pushAsked'

/**
 * 처음 3교시에 참여하거나 열었을 때 한 번만 알림 권한을 묻는다 — 왜 필요한지 알 때 묻는다.
 * 로그인하자마자 설명 없이 묻지 않는다. 내 정보의 [알림 켜기]로 언제든 켤 수 있다
 */
export function askPushOnce() {
  if (!isApp) return
  try {
    if (localStorage.getItem(PUSH_ASKED_KEY)) return
    localStorage.setItem(PUSH_ASKED_KEY, '1')
  } catch {
    return
  }
  void enablePush(sendPushToken).catch(() => {})
}
