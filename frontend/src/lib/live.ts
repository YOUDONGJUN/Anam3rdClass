// 서버가 바로 알려 주는 실시간 연결 (Server-Sent Events, /api/live).
// 로그인 토큰을 머리글에 실어야 해서 EventSource 대신 fetch 로 읽는다.
// 서버는 "무엇이 바뀌었다"만 보낸다 — 내용은 화면이 API 로 다시 읽는다.
import { absoluteUrl, getToken } from '../api/client'

export type LiveEvent =
  /** 톡방에 새 메시지 (deleted 면 그 메시지를 지웠다) */
  | { type: 'chat'; meetupId: number; messageId: number; deleted?: boolean }
  /** 내 다른 기기에서 톡방을 읽었다 */
  | { type: 'chat-read'; meetupId: number }
  /** 다른 원우가 톡방을 더 읽었다 — 메시지 옆 "안 읽은 사람 수"를 다시 센다 */
  | { type: 'chat-reads'; meetupId: number }
  /** 공감(messageId 의 메시지) · 톡방 공지(messageId 없음)가 바뀌었다 */
  | { type: 'chat-change'; meetupId: number; messageId: number | null }
  /** 연결이 (다시) 붙었다 — 끊긴 사이 놓친 것을 다시 읽는다 */
  | { type: 'connected' }

type Listener = (e: LiveEvent) => void

const listeners = new Set<Listener>()
let controller: AbortController | null = null
let running = false
let connected = false
let retry = 0
let retryTimer: ReturnType<typeof setTimeout> | null = null

export function subscribeLive(listener: Listener) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** 지금 실시간으로 받고 있는지 (아니면 화면이 가끔 다시 읽는다) */
export function isLiveConnected() {
  return connected
}

function emit(e: LiveEvent) {
  listeners.forEach((fn) => {
    try {
      fn(e)
    } catch {
      // 한 화면의 실수가 다른 화면에 번지지 않게
    }
  })
}

/** "event: chat\ndata: {...}" 묶음 하나를 읽는다 (이름 없는 줄·주석은 건너뛴다) */
export function parseSseBlock(block: string): LiveEvent | null {
  let name = ''
  const data: string[] = []
  for (const raw of block.split('\n')) {
    const line = raw.replace(/\r$/, '')
    if (!line || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '')
    if (field === 'event') name = value
    else if (field === 'data') data.push(value)
  }
  if (name !== 'chat' && name !== 'chat-read' && name !== 'chat-reads' && name !== 'chat-change') return null
  try {
    const body = JSON.parse(data.join('\n') || '{}') as Record<string, unknown>
    const meetupId = Number(body.meetupId)
    if (!Number.isFinite(meetupId)) return null
    if (name === 'chat-read' || name === 'chat-reads') return { type: name, meetupId }
    if (name === 'chat-change') {
      const messageId = Number(body.messageId)
      return { type: name, meetupId, messageId: body.messageId == null || !Number.isFinite(messageId) ? null : messageId }
    }
    return { type: 'chat', meetupId, messageId: Number(body.messageId), deleted: body.deleted === true }
  } catch {
    return null
  }
}

/** 받은 글자를 빈 줄로 나눠 묶음마다 처리하고, 아직 덜 온 뒷부분은 돌려준다 */
export function splitSse(buffer: string): { blocks: string[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const parts = normalized.split('\n\n')
  const rest = parts.pop() ?? ''
  return { blocks: parts, rest }
}

async function connectOnce() {
  const token = getToken()
  if (!token) return
  controller = new AbortController()
  const res = await fetch(absoluteUrl('/api/live'), {
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    signal: controller.signal,
    cache: 'no-store',
  })
  if (!res.ok || !res.body) throw new Error(`live ${res.status}`)
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  connected = true
  retry = 0
  emit({ type: 'connected' })
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const { blocks, rest } = splitSse(buffer)
    buffer = rest
    for (const block of blocks) {
      const e = parseSseBlock(block)
      if (e) emit(e)
    }
  }
}

function scheduleRetry() {
  if (!running || retryTimer) return
  // 1초 · 2초 · 5초 · 10초 · 그 뒤로는 30초마다
  const delays = [1000, 2000, 5000, 10000, 30000]
  const delay = delays[Math.min(retry, delays.length - 1)]
  retry += 1
  retryTimer = setTimeout(() => {
    retryTimer = null
    void loop()
  }, delay)
}

async function loop() {
  if (!running || document.visibilityState === 'hidden') return
  try {
    await connectOnce()
  } catch {
    // 끊김 · 서버 재시작 · 네트워크 — 아래에서 다시 붙는다
  } finally {
    connected = false
    controller = null
  }
  scheduleRetry()
}

function onVisibility() {
  if (!running) return
  if (document.visibilityState === 'hidden') {
    // 화면을 안 보는 동안은 연결을 닫는다 (배터리·서버 메모리). 그동안 새 소식은 폰 알림으로
    controller?.abort()
  } else if (!controller) {
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    retry = 0
    void loop()
  }
}

/** 로그인하면 켠다 */
export function startLive() {
  if (running) return
  running = true
  document.addEventListener('visibilitychange', onVisibility)
  void loop()
}

/** 로그아웃하면 끈다 */
export function stopLive() {
  running = false
  document.removeEventListener('visibilitychange', onVisibility)
  if (retryTimer) clearTimeout(retryTimer)
  retryTimer = null
  controller?.abort()
  controller = null
  connected = false
}
