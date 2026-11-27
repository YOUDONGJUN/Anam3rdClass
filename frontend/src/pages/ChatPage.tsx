import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type CSSProperties, type KeyboardEvent, type MouseEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  ArrowDown,
  ArrowUp,
  Bell,
  BellOff,
  ChevronLeft,
  ChevronUp,
  ImagePlus,
  Info,
  Keyboard,
  LoaderCircle,
  Megaphone,
  Smile,
  X,
} from 'lucide-react'
import { ApiError } from '../api/client'
import { useChatRoom } from '../api/chat'
import { useMeetupMemories } from '../api/queries'
import type { ChatMessage, ChatQuote, ReactionKind } from '../api/types'
import { ChatBubble, PendingBubble } from '../components/chat/ChatBubble'
import { MessageMenu } from '../components/chat/MessageMenu'
import { StickerPanel } from '../components/chat/StickerPanel'
import { MemoryComposer } from '../components/MemoryComposer'
import { PhotoPicks } from '../components/PhotoPicks'
import { PhotoViewer } from '../components/PhotoViewer'
import { ReportDialog, type ReportTargetInfo } from '../components/ReportDialog'
import { ErrorState, Spinner } from '../components/ui'
import { buttonClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { coarsePointer, groupMessages, isNotice, nextReaction, noticeText, previewOf, quoteOf, replyLabel, unreadCount } from '../lib/chat'
import { copyText } from '../lib/clipboard'
import { setKeyboardBar } from '../lib/native'
import { affiliation } from '../lib/person'
import { CHAT_SIDE, fetchPhoto, PER_SEND, shrinkPhoto } from '../lib/photo'
import { readRecentStickers, rememberSticker, stickerOf } from '../lib/stickers'
import { formatDateTime } from '../lib/time'

/** 맨 아래에서 이만큼 안이면 "맨 아래에 있다"로 본다 */
const NEAR_BOTTOM = 120
/** 맨 아래에서 이만큼 넘게 올라가 있으면 "맨 아래로" 단추 */
const AWAY = 300
/** 답장·공지의 원래 메시지를 찾으러 이전 메시지를 몇 번까지 더 불러오나 (50개씩) */
const JUMP_PAGES = 20

/** 폰 키보드가 올라오면 보이는 높이가 줄어든다 — 입력창이 키보드에 가리지 않게 그 높이에 맞춘다 */
function useVisualViewport() {
  const [box, setBox] = useState<{ top: number; height: number } | null>(() => {
    const vv = typeof window !== 'undefined' ? window.visualViewport : null
    return vv ? { top: vv.offsetTop, height: vv.height } : null
  })
  useEffect(() => {
    const vv = window.visualViewport
    if (!vv) return
    const update = () => setBox({ top: vv.offsetTop, height: vv.height })
    vv.addEventListener('resize', update)
    vv.addEventListener('scroll', update)
    return () => {
      vv.removeEventListener('resize', update)
      vv.removeEventListener('scroll', update)
    }
  }, [])
  return box
}

/** 접어 둔 톡방 공지 (이 기기에만. 새 공지가 올라오면 다시 펴진다) */
const FOLD_KEY = 'anam3.pinFolded.'
function readFolded(meetupId: number): number | null {
  try {
    const v = Number(localStorage.getItem(FOLD_KEY + meetupId))
    return Number.isFinite(v) && v > 0 ? v : null
  } catch {
    return null
  }
}
function writeFolded(meetupId: number, pinId: number | null) {
  try {
    if (pinId === null) localStorage.removeItem(FOLD_KEY + meetupId)
    else localStorage.setItem(FOLD_KEY + meetupId, String(pinId))
  } catch {
    // 저장이 막힌 브라우저 — 이번 화면에서만
  }
}

/** 아이폰 사파리는 누를 수 있는 요소에서만 click 을 보낸다 — 톡방 빈 곳을 눌러도 오게 */
const NOOP = () => {}

/**
 * 3교시 톡방: 지금 함께하는 원우끼리. 글 · 사진(30일 보관) · 이모티콘을 주고받고, 답장 · 공감 · 톡방 공지가 있다.
 * 새 메시지는 실시간으로 오고, 보고 있는 동안은 폰 알림이 울리지 않는다.
 */
export function ChatPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const toast = useToast()
  const room = useChatRoom(id)
  const {
    page,
    messages,
    reads,
    pending,
    error,
    loadOlder,
    loadingOlder,
    oldestId,
    send,
    retry,
    discard,
    remove,
    react,
    pin,
    markRead,
    setMuted,
  } = room
  const memories = useMeetupMemories(id, Boolean(page))
  const viewport = useVisualViewport()
  const canGoBack = (window.history.state?.idx ?? 0) > 0

  const listRef = useRef<HTMLDivElement>(null)
  const topRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  /**
   * 맨 아래까지 남은 거리. 키보드 · 이모티콘함 · 답장 줄로 목록 높이가 바뀌면 이 거리를 지킨다
   * (맨 아래를 보고 있었으면 최신 메시지가 그대로, 위를 보고 있었으면 보던 줄이 그대로 — 카톡처럼)
   */
  const gap = useRef(0)
  const [text, setText] = useState('')
  const [photos, setPhotos] = useState<Blob[]>([])
  const [preparing, setPreparing] = useState(false)
  const [menuFor, setMenuFor] = useState<ChatMessage | null>(null)
  /** 위로 올려 보는 동안 온 새 메시지 수와 그중 마지막 (아래 띠에 "보낸 사람: 첫 줄") */
  const [newCount, setNewCount] = useState(0)
  const [newest, setNewest] = useState<ChatMessage | null>(null)
  /** 맨 아래에서 멀리 올라가 있다 → "맨 아래로" 단추 */
  const [away, setAway] = useState(false)
  /** 공지·답장에서 이 메시지로 왔다 (잠깐 테두리) */
  const [flash, setFlash] = useState<number | null>(null)
  /** 지금 원래 메시지를 찾으러 가는 중 (그 사이 또 누르면 새것만) */
  const jumpSeq = useRef(0)
  const [viewer, setViewer] = useState<{ urls: string[]; start: number } | null>(null)
  const [memoryFrom, setMemoryFrom] = useState<Blob[] | null>(null)
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)
  /** 이모티콘함 (키보드 자리에 열린다) */
  const [panel, setPanel] = useState(false)
  /** 고른 이모티콘 — 입력창 위에 미리 보이고, 보내기(또는 한 번 더 누르기)로 보낸다 (카톡처럼) */
  const [sticker, setSticker] = useState<string | null>(null)
  const [recent, setRecent] = useState<string[]>(readRecentStickers)
  /** 답장할 메시지 (입력창 위에 보인다) */
  const [replyTo, setReplyTo] = useState<ChatQuote | null>(null)
  /** 입력창을 잡고 있다 (폰이면 키보드가 올라와 있다) */
  const [typing, setTyping] = useState(false)
  const [folded, setFolded] = useState<number | null>(() => readFolded(id))

  const atBottom = useCallback(() => {
    const el = listRef.current
    return !el || el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM
  }, [])

  const scrollToBottom = useCallback((smooth = false) => {
    const el = listRef.current
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
    gap.current = 0
  }, [])

  const lastId = messages.length ? messages[messages.length - 1].id : 0

  // 메시지가 바뀔 때 스크롤: 처음엔 맨 아래로, 오래된 것을 불러오면 보던 자리 그대로,
  // 새 메시지는 맨 아래에 있었거나 내가 보냈으면 따라 내려가고, 아니면 아래 띠로 알린다.
  // 공감·지우기로 높이가 바뀌어도 맨 아래에 있었으면 맨 아래 그대로
  const shape = useRef<{ first: number; last: number; height: number; top: number } | null>(null)
  useLayoutEffect(() => {
    const el = listRef.current
    if (!el || messages.length === 0) return
    const first = messages[0].id
    const last = messages[messages.length - 1].id
    const before = shape.current
    let toBottom = false
    if (!before) {
      toBottom = true
      scrollToBottom()
    } else if (first < before.first && last === before.last) {
      el.scrollTop = el.scrollHeight - before.height + before.top
    } else if (last > before.last) {
      const mine = messages[messages.length - 1].mine
      if (gap.current < NEAR_BOTTOM || mine) {
        toBottom = true
        scrollToBottom(!mine)
      } else {
        const incoming = messages.filter((m) => m.id > before.last && !m.mine && !isNotice(m))
        if (incoming.length > 0) {
          setNewCount((n) => n + incoming.length)
          setNewest(incoming[incoming.length - 1])
        }
      }
    } else if (gap.current < NEAR_BOTTOM) {
      toBottom = true
      scrollToBottom()
    }
    shape.current = { first, last, height: el.scrollHeight, top: el.scrollTop }
    // 부드럽게 내려가는 중이면 아직 맨 아래가 아니다 — 가려는 곳(0)을 그대로 둔다
    if (!toBottom) gap.current = el.scrollHeight - el.scrollTop - el.clientHeight
  }, [messages, scrollToBottom])

  // 보낸 메시지("보내는 중")가 생기면 맨 아래로. 실패 단추가 생기고 없어져도 맨 아래였으면 그대로
  const pendingCount = useRef(0)
  useLayoutEffect(() => {
    if (pending.length > pendingCount.current || gap.current < NEAR_BOTTOM) scrollToBottom()
    pendingCount.current = pending.length
  }, [pending, scrollToBottom])

  // 목록 높이가 바뀌면(키보드 · 이모티콘함 · 답장 줄 · 사진 고르기) 맨 아래까지 남은 거리를 지킨다
  const ready = !error
  useEffect(() => {
    const el = listRef.current
    if (!el || !ready || typeof ResizeObserver === 'undefined') return
    let height = el.clientHeight
    const ro = new ResizeObserver(() => {
      if (el.clientHeight === height) return
      height = el.clientHeight
      el.scrollTop = Math.max(0, el.scrollHeight - el.clientHeight - gap.current)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [ready])

  const onScroll = useCallback(() => {
    const el = listRef.current
    if (!el) return
    gap.current = el.scrollHeight - el.scrollTop - el.clientHeight
    if (shape.current) shape.current = { ...shape.current, height: el.scrollHeight, top: el.scrollTop }
    setAway(gap.current > AWAY)
    if (gap.current < NEAR_BOTTOM) {
      setNewCount(0)
      setNewest(null)
      if (lastId && document.visibilityState === 'visible') markRead(lastId)
    }
  }, [lastId, markRead])

  // 맨 아래에서 보고 있으면 읽음. 열어 둔 동안은 1분마다 "보고 있어요" (그동안 폰 알림이 안 울린다)
  useEffect(() => {
    if (lastId && atBottom() && document.visibilityState === 'visible') markRead(lastId)
  }, [lastId, atBottom, markRead])
  useEffect(() => {
    const timer = setInterval(() => {
      if (lastId && document.visibilityState === 'visible') markRead(atBottom() ? lastId : 0, true)
    }, 60_000)
    return () => clearInterval(timer)
  }, [lastId, atBottom, markRead])

  // 맨 위에 닿으면 오래된 메시지
  useEffect(() => {
    const el = topRef.current
    if (!el || !page?.hasMore) return
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadOlder()
    }, { root: listRef.current })
    io.observe(el)
    return () => io.disconnect()
  }, [page?.hasMore, loadOlder])

  // 입력창 높이: 쓴 만큼 (5줄까지)
  useLayoutEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 132)}px`
  }, [text])

  // 아이폰 앱: 톡방에서는 키보드 위 ∧ ∨ 완료 막대를 숨긴다 (톡방을 누르면 키보드가 내려간다). 나가면 다시 보인다
  useEffect(() => {
    setKeyboardBar(false)
    return () => setKeyboardBar(true)
  }, [])

  useEffect(() => {
    if (flash === null) return
    const timer = setTimeout(() => setFlash(null), 1600)
    return () => clearTimeout(timer)
  }, [flash])

  if (error) {
    return (
      <div className="px-4 pt-6">
        <button
          type="button"
          onClick={() => (canGoBack ? navigate(-1) : navigate(`/meetups/${id}`))}
          className="-ml-2 inline-flex h-11 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
        >
          <ChevronLeft className="size-5" aria-hidden />
          뒤로
        </button>
        <ErrorState
          message={error.status === 403 ? '함께하는 원우만 톡방에 들어갈 수 있어요' : error.message}
          onRetry={error.status === 403 ? undefined : () => room.reload()}
          action={
            <Link to={`/meetups/${id}`} className={buttonClass('secondary')}>
              3교시 보기
            </Link>
          }
        />
      </div>
    )
  }

  const left = page ? Math.min(PER_SEND, page.photosLeftToday) : 0
  const canSend = (text.trim().length > 0 || photos.length > 0 || sticker !== null) && !preparing
  const picked = stickerOf(sticker)
  const canWrite = page?.canWrite ?? false
  const pinned = page?.pinned ?? null
  const showPin = pinned !== null && folded !== pinned.id
  // 폰 키보드가 올라와 있으면 아래 여백(홈 표시줄 자리)이 필요 없다 — 입력창이 키보드에 바로 붙게
  const keyboardUp = coarsePointer && typing

  /**
   * 못 보낸 메시지는 말풍선 옆 ! 와 "다시 보내기"로 알린다 (카톡처럼).
   * 서버가 이유를 알려 준 것(너무 빨리 보냄 · 오늘 사진을 다 보냄 …)만 알림으로도 띄운다 — 다시 보내도 안 되는 것이라
   */
  const failToast = (err: unknown) => {
    if (err instanceof ApiError && err.status > 0 && err.message) toast.show(err.message, 'error')
  }

  async function pick(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length === 0) return
    const room = left - photos.length
    if (room <= 0) {
      toast.show(page?.photosLeftToday === 0 ? '오늘 보낼 수 있는 사진을 다 보냈어요' : '사진은 한 번에 5장까지예요')
      return
    }
    if (files.length > room) toast.show(`${room}장까지 더 고를 수 있어요`)
    setPreparing(true)
    try {
      const shrunk = await Promise.all(files.slice(0, room).map((f) => shrinkPhoto(f, CHAT_SIDE)))
      setPhotos((prev) => [...prev, ...shrunk])
    } catch (err) {
      toast.show(err instanceof Error ? err.message : '사진을 열지 못했어요', 'error')
    } finally {
      setPreparing(false)
    }
  }

  /**
   * 보내기: 바로 "보내는 중"으로 보이고 입력창은 비운다 (키보드는 그대로).
   * 고른 이모티콘이 있으면 먼저, 그다음 글·사진 (이모티콘은 글과 한 메시지에 담지 않는다). 답장은 처음 것에 붙는다
   */
  function submit() {
    if (!canSend) return
    const reply = replyTo
    if (sticker) {
      send({ sticker, reply }).catch(failToast)
      setRecent(rememberSticker(sticker))
      setSticker(null)
    }
    const body = text.trim()
    if (body || photos.length > 0) {
      send({ body, photos, reply: sticker ? null : reply }).catch(failToast)
      setText('')
      setPhotos([])
    }
    setReplyTo(null)
    // 이모티콘함을 보고 있으면 그대로 둔다 (입력창을 잡으면 이모티콘함이 닫히고 키보드가 올라온다)
    if (!panel) inputRef.current?.focus()
  }

  /** 이모티콘함에서 누르기: 처음엔 고르기(미리 보기), 고른 것을 한 번 더 누르면 바로 보내기 */
  function pickSticker(key: string) {
    if (sticker === key) submit()
    else setSticker(key)
  }

  /** 😊 ↔ ⌨ : 이모티콘함을 열면 키보드를 내리고, 닫으면 키보드를 올린다 */
  function togglePanel() {
    if (panel) {
      setPanel(false)
      inputRef.current?.focus()
    } else {
      setPanel(true)
      inputRef.current?.blur()
    }
  }

  /** 폰: 키보드나 이모티콘함이 열려 있으면 톡방을 눌렀을 때 내리기만 한다 (카톡처럼) */
  function dismissFirst(e: MouseEvent) {
    if (!coarsePointer) return
    const keyboardOpen = document.activeElement === inputRef.current
    if (!panel && !keyboardOpen) return
    e.preventDefault()
    e.stopPropagation()
    setPanel(false)
    inputRef.current?.blur()
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Escape' && replyTo) {
      e.preventDefault()
      setReplyTo(null)
      return
    }
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing && !coarsePointer) {
      e.preventDefault()
      submit()
    }
  }

  async function toggleMute() {
    if (!page) return
    try {
      await setMuted(!page.muted)
      toast.show(page.muted ? '이 톡방 알림을 켰어요' : '이 톡방 알림을 껐어요')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : '바꾸지 못했어요', 'error')
    }
  }

  async function addToMemories(url: string) {
    try {
      const blob = await fetchPhoto(url)
      setViewer(null)
      setMemoryFrom([blob])
    } catch {
      toast.show('사진을 가져오지 못했어요', 'error')
    }
  }

  function openMenu(m: ChatMessage) {
    setPanel(false)
    inputRef.current?.blur()
    setMenuFor(m)
  }

  /** 답장·공지의 원래 메시지로: 화면에 없으면 이전 메시지를 더 불러와서 찾는다 (50개씩, 20번까지) */
  async function jumpTo(messageId: number) {
    const seq = ++jumpSeq.current
    for (let i = 0; ; i++) {
      const list = listRef.current
      const el = list?.querySelector<HTMLElement>(`[data-mid="${messageId}"]`)
      if (list && el) {
        // 가까우면 부드럽게, 멀면(두 화면 넘게) 바로 간다 — 길게 미끄러지는 동안 기다리지 않게
        const far = Math.abs(el.getBoundingClientRect().top - list.getBoundingClientRect().top) > list.clientHeight * 2
        el.scrollIntoView({ block: 'center', behavior: i === 0 && !far ? 'smooth' : 'auto' })
        setFlash(messageId)
        return
      }
      const oldestLoaded = oldestId()
      // 가진 메시지 안에 있어야 하는데 없다 (지워진 줄) · 너무 오래됐다 · 더 없다
      if (i >= JUMP_PAGES || oldestLoaded === null || oldestLoaded < messageId || !(await loadOlder())) break
      // 불러온 메시지가 화면에 그려질 때까지
      await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      if (seq !== jumpSeq.current) return
    }
    if (seq === jumpSeq.current) toast.show('원래 메시지를 찾지 못했어요')
  }

  function startReply(m: ChatMessage) {
    setReplyTo(quoteOf(m))
    setPanel(false)
    inputRef.current?.focus()
  }

  async function copyMessage(m: ChatMessage) {
    if (m.body && (await copyText(m.body))) toast.show('복사했어요')
    else toast.show('복사하지 못했어요', 'error')
  }

  function reactTo(m: ChatMessage, reaction: ReactionKind) {
    react(m.id, nextReaction(m, reaction)).catch((err: unknown) =>
      toast.show(err instanceof Error ? err.message : '공감하지 못했어요', 'error'),
    )
  }

  async function setPin(messageId: number | null) {
    try {
      await pin(messageId)
      toast.show(messageId === null ? '톡방 공지를 내렸어요' : '톡방 공지로 올렸어요')
      if (messageId !== null) unfold()
    } catch (err) {
      toast.show(err instanceof Error ? err.message : '바꾸지 못했어요', 'error')
    }
  }

  function unpinFromBar() {
    if (window.confirm('톡방 공지를 내릴까요?')) void setPin(null)
  }

  function fold() {
    if (!pinned) return
    setFolded(pinned.id)
    writeFolded(id, pinned.id)
  }

  function unfold() {
    setFolded(null)
    writeFolded(id, null)
  }

  async function deleteMessage(m: ChatMessage) {
    if (!window.confirm('이 메시지를 지울까요?\n\n• 모두에게 "지운 메시지"로 보여요')) return
    try {
      await remove(m.id)
      if (replyTo?.id === m.id) setReplyTo(null)
    } catch (err) {
      toast.show(err instanceof Error ? err.message : '지우지 못했어요', 'error')
    }
  }

  function reportMessage(m: ChatMessage) {
    const s = m.sender
    const label = `${s.name} · ${affiliation(s.cohortLabel, s.departmentLabel)} · ${s.school.shortName}`
    // 메시지 번호로 신고한다: 보낸 원우가 그 뒤에 나갔어도 그 사람이 대상이고, 메시지 내용이 신고 기록에 남는다
    setReporting({ type: 'MEMBER', id: m.id, chat: true, name: s.name, label })
  }

  const canAddMemory = Boolean(memories.data?.canAdd && memories.data.photosLeft > 0)
  const rows = groupMessages(messages)
  // 들어왔을 때 어디까지 읽었었는지 (page 는 처음 불러올 때만 바뀐다) — "여기부터 안 읽은 메시지" 줄
  const unreadFrom = page?.lastReadId ?? 0
  const firstUnread = unreadFrom ? messages.find((m) => m.id > unreadFrom && !m.mine && !isNotice(m))?.id : undefined

  return (
    <div
      className="fixed inset-x-0 top-(--vv-top) z-40 flex h-(--vv-height) touch-manipulation flex-col bg-bg lg:static lg:z-auto lg:h-dvh"
      style={
        {
          '--vv-top': `${viewport?.top ?? 0}px`,
          '--vv-height': viewport ? `${viewport.height}px` : '100dvh',
        } as CSSProperties
      }
    >
      {/* 머리: 뒤로 · 3교시 이름 · (접어 둔 공지) · 알림 끄기 · 3교시 보기 */}
      <header className="flex shrink-0 items-center gap-1 border-b border-line bg-surface px-2 pt-[env(safe-area-inset-top)] lg:pt-0">
        <button
          type="button"
          onClick={() => (canGoBack ? navigate(-1) : navigate(`/meetups/${id}`))}
          aria-label="뒤로"
          className="flex size-11 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 hover:text-ink"
        >
          <ChevronLeft className="size-6" aria-hidden />
        </button>
        <div className="min-w-0 flex-1 py-2">
          <h1 className="truncate text-[16px] font-extrabold text-ink">{page?.title ?? '톡방'}</h1>
          {page && (
            <p className="truncate text-[12px] text-ink-3">
              {page.people}명 · {formatDateTime(page.meetAt)}
              {page.muted && ' · 알림 꺼짐'}
            </p>
          )}
        </div>
        {pinned && !showPin && (
          <button
            type="button"
            onClick={unfold}
            aria-label="톡방 공지 펴기"
            className="flex size-11 shrink-0 items-center justify-center rounded-lg text-accent-ink hover:bg-surface-2"
          >
            <Megaphone className="size-5" aria-hidden />
          </button>
        )}
        {page && (
          <button
            type="button"
            onClick={toggleMute}
            aria-label={page.muted ? '이 톡방 알림 켜기' : '이 톡방 알림 끄기'}
            aria-pressed={page.muted}
            className="flex size-11 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 hover:text-ink"
          >
            {page.muted ? <BellOff className="size-5" aria-hidden /> : <Bell className="size-5" aria-hidden />}
          </button>
        )}
        <Link
          to={`/meetups/${id}`}
          aria-label="3교시 보기"
          className="flex size-11 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 hover:text-ink"
        >
          <Info className="size-5" aria-hidden />
        </Link>
      </header>

      {/* 톡방 공지: 누르면 그 메시지로. 연 사람은 내리기, 누구나 접기 */}
      {pinned && showPin && (
        <div className="flex shrink-0 items-center gap-1 border-b border-line bg-surface pr-1 pl-3">
          <Megaphone className="size-4 shrink-0 text-accent-ink" aria-hidden />
          <button type="button" onClick={() => jumpTo(pinned.id)} className="min-w-0 flex-1 py-2.5 pl-1 text-left">
            <span className="sr-only">톡방 공지: </span>
            <span className="block truncate text-[14px] font-semibold text-ink">{pinned.preview}</span>
          </button>
          {page?.canPin && (
            <button
              type="button"
              onClick={unpinFromBar}
              className="h-11 shrink-0 rounded-lg px-2 text-[13px] font-semibold text-ink-3 hover:bg-surface-2 hover:text-ink"
            >
              내리기
            </button>
          )}
          <button
            type="button"
            onClick={fold}
            aria-label="톡방 공지 접기"
            className="flex size-11 shrink-0 items-center justify-center rounded-lg text-ink-3 hover:bg-surface-2 hover:text-ink"
          >
            <ChevronUp className="size-5" aria-hidden />
          </button>
        </div>
      )}

      {/* 메시지 */}
      <div
        ref={listRef}
        onScroll={onScroll}
        onClickCapture={dismissFirst}
        onClick={NOOP}
        className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 pb-3 sm:px-5"
      >
        {!page ? (
          <Spinner label="톡방 들어가는 중" />
        ) : (
          <>
            <div ref={topRef} className="h-px" />
            {page.hasMore ? (
              <div className="flex justify-center py-3">
                {loadingOlder ? (
                  <LoaderCircle className="size-5 animate-spin text-ink-3" aria-label="이전 메시지 불러오는 중" />
                ) : (
                  <button type="button" onClick={() => loadOlder()} className="text-sm font-semibold text-ink-3 hover:text-ink">
                    이전 메시지 보기
                  </button>
                )}
              </div>
            ) : (
              <p className="mx-auto mt-4 mb-2 max-w-sm rounded-2xl bg-surface-2 px-4 py-3 text-center text-[13px] leading-relaxed text-ink-3">
                지금 함께하는 원우만 보는 톡방이에요.
                <br />
                사진은 올린 날부터 {page.photoKeepDays}일 동안 보관돼요. 남기고 싶은 사진은 추억에 올려 주세요.
              </p>
            )}
            {messages.length === 0 && pending.length === 0 && (
              <p className="mt-10 text-center text-[15px] text-ink-3">첫 인사를 건네 보세요 👋</p>
            )}
            {rows.map(({ message: m, dayBreak, first, last }) => (
              <div key={m.id}>
                {dayBreak && (
                  <div className="my-4 flex justify-center">
                    <span className="rounded-full bg-surface-2 px-3 py-1 text-[12px] font-semibold text-ink-3">{dayBreak}</span>
                  </div>
                )}
                {m.id === firstUnread && (
                  <div className="my-3 flex items-center gap-2 text-[12px] font-semibold text-accent-ink" role="separator">
                    <span className="h-px flex-1 bg-accent/40" />
                    여기부터 안 읽은 메시지
                    <span className="h-px flex-1 bg-accent/40" />
                  </div>
                )}
                {isNotice(m) ? (
                  <p className="my-3 flex justify-center" data-mid={m.id}>
                    <span className="max-w-[85%] rounded-full bg-surface-2 px-3 py-1 text-center text-[12px] text-ink-3 [word-break:keep-all]">
                      {noticeText(m)}
                    </span>
                  </p>
                ) : (
                  <ChatBubble
                    m={m}
                    federated={page.federated}
                    first={first || m.id === firstUnread}
                    last={last}
                    unread={unreadCount(m, reads)}
                    flash={flash === m.id}
                    canReact={page.canWrite}
                    onMenu={() => openMenu(m)}
                    onPhoto={(i) =>
                      setViewer({ urls: m.photos.filter((p) => !p.expired && p.url).map((p) => p.url as string), start: i })
                    }
                    onQuote={jumpTo}
                    onReact={(reaction) => reactTo(m, reaction)}
                  />
                )}
              </div>
            ))}
            {pending.map((p) => (
              <PendingBubble
                key={p.key}
                p={p}
                onRetry={() => void retry(p.key).catch(failToast)}
                onDiscard={() => discard(p.key)}
              />
            ))}
          </>
        )}
      </div>

      {/* 위로 올려 보는 동안: 새 메시지가 오면 "보낸 사람: 첫 줄" 띠, 아니면 맨 아래로 단추 */}
      {(newest !== null || away) && (
        <div className="pointer-events-none relative">
          {newest !== null && newCount > 0 ? (
            <button
              type="button"
              onClick={() => {
                scrollToBottom(true)
                setNewCount(0)
                setNewest(null)
              }}
              aria-label={`새 메시지 ${newCount}개 · ${newest.sender.name}: ${previewOf(newest)} — 맨 아래로`}
              className={`pointer-events-auto absolute bottom-3 left-3 flex h-11 items-center gap-2 rounded-full bg-board pr-3 pl-4 text-on-board shadow-lg ${
                // 이모티콘 미리 보기(오른쪽)와 겹치지 않게
                picked ? 'right-[calc(min(7rem,var(--vv-height)*0.16)+2.5rem)]' : 'right-3'
              } sm:right-auto sm:max-w-md`}
            >
              <span className="min-w-0 flex-1 truncate text-left text-[13px]">
                <b className="font-bold">{newest.sender.name}</b>
                <span className="opacity-85"> {previewOf(newest)}</span>
              </span>
              {newCount > 1 && (
                <span className="tabular shrink-0 rounded-full bg-accent px-1.5 text-[11px] leading-5 font-bold text-on-accent">
                  {newCount}
                </span>
              )}
              <ArrowDown className="size-4 shrink-0" aria-hidden />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => scrollToBottom(true)}
              aria-label="맨 아래로"
              className={`pointer-events-auto absolute bottom-3 flex size-10 items-center justify-center rounded-full border border-line bg-surface text-ink-2 shadow-lg hover:text-ink ${
                picked ? 'left-3' : 'right-3 sm:right-5'
              }`}
            >
              <ArrowDown className="size-5" aria-hidden />
            </button>
          )}
        </div>
      )}

      {/* 입력 */}
      {page && !page.canWrite ? (
        <p className="shrink-0 border-t border-line bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] text-center text-sm text-ink-3">
          취소된 3교시라 읽기만 할 수 있어요
        </p>
      ) : (
        <>
          {/* 고른 이모티콘 미리 보기 — 누르거나 보내기를 누르면 보낸다. ✕ 로 뺀다 */}
          {picked && (
            <div className="pointer-events-none relative">
              <div className="pointer-events-auto absolute right-3 bottom-2 rounded-2xl border border-line bg-surface/95 p-1.5 shadow-lg sm:right-5">
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={submit}
                  aria-label={`이모티콘 보내기: ${picked.label}`}
                  className="block touch-manipulation"
                >
                  <img
                    src={picked.src}
                    alt=""
                    width={112}
                    height={112}
                    draggable={false}
                    // 화면이 낮으면(키보드 · 가로 화면) 작게 — 머리를 가리지 않게
                    className="size-[min(7rem,calc(var(--vv-height)*0.16))] select-none"
                  />
                </button>
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => setSticker(null)}
                  aria-label="고른 이모티콘 빼기"
                  className="absolute -top-2 -right-2 flex size-8 items-center justify-center rounded-full bg-board text-on-board shadow-md"
                >
                  <X className="size-4" strokeWidth={2.6} aria-hidden />
                </button>
              </div>
            </div>
          )}
          <div
            className={`shrink-0 border-t border-line bg-surface px-3 pt-2.5 sm:px-5 ${
              panel || keyboardUp ? 'pb-2.5' : 'pb-[calc(10px+env(safe-area-inset-bottom))]'
            }`}
          >
            {replyTo && (
              <div className="mb-2 flex items-center gap-2 rounded-xl bg-surface-2 py-1.5 pr-1 pl-3">
                <div className="min-w-0 flex-1 border-l-[3px] border-accent pl-2">
                  <p className="truncate text-[12px] font-bold text-ink-2">{replyLabel(replyTo)}</p>
                  <p className="truncate text-[13px] text-ink-3">{replyTo.preview}</p>
                </div>
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => setReplyTo(null)}
                  aria-label="답장 그만두기"
                  className="flex size-9 shrink-0 items-center justify-center rounded-full text-ink-3 hover:bg-surface hover:text-ink"
                >
                  <X className="size-4" aria-hidden />
                </button>
              </div>
            )}
            {photos.length > 0 && (
              <div className="mb-2">
                <PhotoPicks size="sm" photos={photos} onRemove={(i) => setPhotos((p) => p.filter((_, j) => j !== i))} />
              </div>
            )}
            <div className="flex items-end gap-2">
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                disabled={!page || preparing}
                aria-label={page ? `사진 보내기 (오늘 ${page.photosLeftToday}장 남음)` : '사진 보내기'}
                className="flex size-11 shrink-0 items-center justify-center rounded-full text-ink-2 hover:bg-surface-2 hover:text-ink disabled:opacity-40"
              >
                {preparing ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <ImagePlus className="size-[22px]" aria-hidden />}
              </button>
              <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={pick} />
              {/* 입력창 안 오른쪽에 이모티콘 단추 (카톡처럼) */}
              <div className="flex min-h-11 min-w-0 flex-1 items-end rounded-[22px] border border-line-strong bg-surface-2 focus-within:border-accent">
                <textarea
                  ref={inputRef}
                  value={text}
                  onChange={(e) => setText(e.target.value.slice(0, 1000))}
                  onKeyDown={onKeyDown}
                  onFocus={() => {
                    setPanel(false)
                    setTyping(true)
                  }}
                  onBlur={() => setTyping(false)}
                  rows={1}
                  placeholder={replyTo ? '답장 보내기' : '메시지 보내기'}
                  aria-label="메시지"
                  enterKeyHint={coarsePointer ? 'enter' : 'send'}
                  className="max-h-33 min-h-[42px] min-w-0 flex-1 resize-none bg-transparent py-2.5 pr-1 pl-4 text-base leading-snug text-ink focus:outline-none"
                />
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={togglePanel}
                  aria-label={panel ? '키보드로 돌아가기' : '이모티콘'}
                  aria-expanded={panel}
                  className={`flex size-[42px] shrink-0 items-center justify-center rounded-full hover:text-ink ${
                    panel ? 'text-accent-ink' : 'text-ink-2'
                  }`}
                >
                  {panel ? <Keyboard className="size-[22px]" aria-hidden /> : <Smile className="size-[22px]" aria-hidden />}
                </button>
              </div>
              <button
                type="button"
                // 눌러도 입력창에서 손이 떠나지 않게 — 안 그러면 폰 키보드가 내려갔다가 다시 올라온다
                onMouseDown={(e) => e.preventDefault()}
                onClick={submit}
                disabled={!canSend}
                aria-label="보내기"
                className="flex size-11 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent transition-opacity disabled:opacity-35"
              >
                <ArrowUp className="size-5" strokeWidth={2.6} aria-hidden />
              </button>
            </div>
          </div>
          {panel && <StickerPanel selected={sticker} recent={recent} onPick={pickSticker} />}
        </>
      )}

      {menuFor && page && (
        <MessageMenu
          m={menuFor}
          canWrite={canWrite}
          canPin={page.canPin}
          pinnedHere={pinned?.id === menuFor.id}
          onClose={() => setMenuFor(null)}
          onReact={(reaction) => reactTo(menuFor, reaction)}
          onReply={() => startReply(menuFor)}
          onCopy={() => void copyMessage(menuFor)}
          onPin={() => void setPin(pinned?.id === menuFor.id ? null : menuFor.id)}
          onDelete={() => void deleteMessage(menuFor)}
          onReport={() => reportMessage(menuFor)}
        />
      )}
      {viewer && (
        <PhotoViewer
          urls={viewer.urls}
          start={viewer.start}
          onClose={() => setViewer(null)}
          onAddToMemories={canAddMemory ? addToMemories : undefined}
        />
      )}
      {memoryFrom && memories.data && (
        <MemoryComposer
          meetupId={id}
          photosLeft={memories.data.photosLeft}
          initialPhotos={memoryFrom}
          onClose={() => setMemoryFrom(null)}
          onDone={() => void memories.refetch()}
        />
      )}
      {reporting && (
        <ReportDialog
          key={`${reporting.type}-${reporting.id}`}
          target={reporting}
          onClose={() => setReporting(null)}
          // 차단했으면 그 원우의 메시지를 바로 가린다
          onReported={() => room.reload()}
        />
      )}
    </div>
  )
}
