import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type CSSProperties, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowDown, ArrowUp, Bell, BellOff, ChevronLeft, EyeOff, ImageOff, ImagePlus, Info, LoaderCircle } from 'lucide-react'
import { absoluteUrl, ApiError } from '../api/client'
import { useChatRoom } from '../api/chat'
import { useMeetupMemories } from '../api/queries'
import type { ChatMessage } from '../api/types'
import { MemoryComposer } from '../components/MemoryComposer'
import { RoleTag } from '../components/PersonName'
import { SchoolBadge } from '../components/SchoolBadge'
import { PhotoPicks } from '../components/PhotoPicks'
import { PhotoViewer } from '../components/PhotoViewer'
import { ReportDialog, type ReportTargetInfo } from '../components/ReportDialog'
import { ErrorState, Spinner } from '../components/ui'
import { buttonClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { groupMessages } from '../lib/chat'
import { affiliation } from '../lib/person'
import { CHAT_SIDE, fetchPhoto, PER_SEND, shrinkPhoto } from '../lib/photo'
import { formatDateTime, formatTime } from '../lib/time'

/** 맨 아래에서 이만큼 안이면 "맨 아래에 있다"로 본다 */
const NEAR_BOTTOM = 120

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

/** 손가락이 주로 쓰이는 기기인지 (폰에서는 Enter 가 줄바꿈, PC 에서는 보내기) */
const coarse = typeof window !== 'undefined' && window.matchMedia?.('(pointer: coarse)').matches

function Bubble({
  m,
  federated,
  first,
  last,
  selected,
  onSelect,
  onPhoto,
}: {
  m: ChatMessage
  /** 연합 3교시: 학교 배지 + 학과 · 같은 학교 3교시: 기수 · 학과 */
  federated: boolean
  first: boolean
  last: boolean
  selected: boolean
  onSelect: () => void
  onPhoto: (index: number) => void
}) {
  const visible = m.photos.filter((p) => !p.expired)
  const bubble = m.mine
    ? 'bg-accent text-on-accent rounded-2xl rounded-br-md'
    : 'bg-surface text-ink border border-line rounded-2xl rounded-bl-md'
  const muted = m.deleted || m.hidden
  return (
    <div className={`flex flex-col ${m.mine ? 'items-end' : 'items-start'} ${first ? 'mt-3' : 'mt-1'}`}>
      {first && !m.mine && (
        <p className="mb-1 flex max-w-full items-center gap-1.5 px-1 text-[13px] text-ink-2">
          <span className="truncate font-bold text-ink">{m.sender.name}</span>
          {m.sender.masked && <EyeOff className="size-3 shrink-0 opacity-60" aria-label="이름 가려짐" />}
          {m.sender.owner && <RoleTag />}
          {m.sender.host && <span className="shrink-0 rounded bg-surface-2 px-1 text-[11px] font-bold text-ink-2">연 사람</span>}
          {federated && <SchoolBadge school={m.sender.school} />}
          <span className="shrink-0 text-ink-3">{affiliation(m.sender.cohortLabel, m.sender.departmentLabel)}</span>
        </p>
      )}
      <div className={`flex max-w-[85%] items-end gap-1.5 sm:max-w-[70%] ${m.mine ? 'flex-row-reverse' : ''}`}>
        <div className="flex min-w-0 flex-col gap-1">
          {!muted && m.photos.length > 0 && (
            <div className={`grid gap-1 overflow-hidden rounded-2xl ${m.photos.length > 1 ? 'w-60 grid-cols-2' : 'w-60'}`}>
              {m.photos.map((p, i) =>
                p.expired || !p.url ? (
                  <div
                    key={p.key}
                    className="flex aspect-square flex-col items-center justify-center gap-1 bg-surface-2 text-center text-[11px] text-ink-3"
                  >
                    <ImageOff className="size-5" aria-hidden />
                    보관 기간이 지난 사진
                  </div>
                ) : (
                  <button
                    key={p.key}
                    type="button"
                    onClick={() => onPhoto(visible.indexOf(p))}
                    aria-label={`사진 ${i + 1} 크게 보기`}
                    className={m.photos.length > 1 ? 'aspect-square' : 'aspect-[4/5]'}
                  >
                    <img
                      src={absoluteUrl(p.url)}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      className="size-full bg-surface-2 object-cover"
                    />
                  </button>
                ),
              )}
            </div>
          )}
          {(m.body || muted) && (
            <button
              type="button"
              onClick={onSelect}
              aria-expanded={selected}
              className={`px-3.5 py-2 text-left text-[15px] leading-relaxed break-words whitespace-pre-wrap ${bubble} ${
                muted ? 'italic opacity-60' : ''
              }`}
            >
              {m.deleted ? '지운 메시지예요' : m.hidden ? '차단한 원우의 메시지예요' : m.body}
            </button>
          )}
          {!m.body && !muted && (
            <button type="button" onClick={onSelect} className="sr-only">
              메시지 메뉴
            </button>
          )}
        </div>
        {last && (
          <time dateTime={m.createdAt} className="tabular shrink-0 pb-0.5 text-[11px] text-ink-3">
            {formatTime(m.createdAt)}
          </time>
        )}
      </div>
    </div>
  )
}

/**
 * 3교시 톡방: 지금 함께하는 원우끼리. 글과 사진(30일 보관)을 주고받는다.
 * 새 메시지는 실시간으로 오고, 보고 있는 동안은 폰 알림이 울리지 않는다.
 */
export function ChatPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const toast = useToast()
  const room = useChatRoom(id)
  const { page, messages, error, loadOlder, loadingOlder, send, remove, markRead, setMuted } = room
  const memories = useMeetupMemories(id, Boolean(page))
  const viewport = useVisualViewport()
  const canGoBack = (window.history.state?.idx ?? 0) > 0

  const listRef = useRef<HTMLDivElement>(null)
  const topRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState('')
  const [photos, setPhotos] = useState<Blob[]>([])
  const [preparing, setPreparing] = useState(false)
  const [sending, setSending] = useState(false)
  const [selected, setSelected] = useState<number | null>(null)
  const [newCount, setNewCount] = useState(0)
  const [viewer, setViewer] = useState<{ urls: string[]; start: number } | null>(null)
  const [memoryFrom, setMemoryFrom] = useState<Blob[] | null>(null)
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)

  const atBottom = useCallback(() => {
    const el = listRef.current
    return !el || el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM
  }, [])

  const scrollToBottom = useCallback((smooth = false) => {
    const el = listRef.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }, [])

  const lastId = messages.length ? messages[messages.length - 1].id : 0

  // 메시지가 바뀔 때 스크롤: 처음엔 맨 아래로, 오래된 것을 불러오면 보던 자리 그대로,
  // 새 메시지는 맨 아래에 있었거나 내가 보냈으면 따라 내려가고, 아니면 "새 메시지" 단추
  const shape = useRef<{ first: number; last: number; height: number; top: number } | null>(null)
  useLayoutEffect(() => {
    const el = listRef.current
    if (!el || messages.length === 0) return
    const first = messages[0].id
    const last = messages[messages.length - 1].id
    const before = shape.current
    if (!before) {
      scrollToBottom()
    } else if (first < before.first && last === before.last) {
      el.scrollTop = el.scrollHeight - before.height + before.top
    } else if (last > before.last) {
      const wasAtBottom = before.height - before.top - el.clientHeight < NEAR_BOTTOM
      const mine = messages[messages.length - 1].mine
      if (wasAtBottom || mine) scrollToBottom(!mine)
      else setNewCount((n) => n + messages.filter((m) => m.id > before.last && !m.mine).length)
    }
    shape.current = { first, last, height: el.scrollHeight, top: el.scrollTop }
  }, [messages, scrollToBottom])

  const onScroll = useCallback(() => {
    const el = listRef.current
    if (el && shape.current) shape.current = { ...shape.current, height: el.scrollHeight, top: el.scrollTop }
    if (atBottom()) {
      setNewCount(0)
      if (lastId && document.visibilityState === 'visible') markRead(lastId)
    }
  }, [atBottom, lastId, markRead])

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
  const canSend = (text.trim().length > 0 || photos.length > 0) && !sending && !preparing

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

  async function submit() {
    if (!canSend) return
    setSending(true)
    try {
      await send(text.trim(), photos)
      setText('')
      setPhotos([])
      inputRef.current?.focus()
    } catch (err) {
      toast.show(err instanceof ApiError ? err.message : '보내지 못했어요. 다시 보내 주세요', 'error')
    } finally {
      setSending(false)
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing && !coarse) {
      e.preventDefault()
      void submit()
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

  async function deleteMessage(m: ChatMessage) {
    if (!window.confirm('이 메시지를 지울까요?\n\n• 모두에게 "지운 메시지"로 보여요')) return
    try {
      await remove(m.id)
      setSelected(null)
    } catch (err) {
      toast.show(err instanceof Error ? err.message : '지우지 못했어요', 'error')
    }
  }

  function reportMessage(m: ChatMessage) {
    const s = m.sender
    const label = `${s.name} · ${affiliation(s.cohortLabel, s.departmentLabel)} · ${s.school.shortName}`
    setReporting(s.ref !== null ? { type: 'MEMBER', id: s.ref, name: s.name, label } : { type: 'MEETUP', id, label: `톡방 ‘${page?.title ?? ''}’` })
    setSelected(null)
  }

  const canAddMemory = Boolean(memories.data?.canAdd && memories.data.photosLeft > 0)
  const rows = groupMessages(messages)
  // 들어왔을 때 어디까지 읽었었는지 (page 는 처음 불러올 때만 바뀐다) — "여기부터 안 읽은 메시지" 줄
  const unreadFrom = page?.lastReadId ?? 0
  const firstUnread = unreadFrom ? messages.find((m) => m.id > unreadFrom && !m.mine)?.id : undefined

  return (
    <div
      className="fixed inset-x-0 top-(--vv-top) z-40 flex h-(--vv-height) flex-col bg-bg lg:static lg:z-auto lg:h-dvh"
      style={
        {
          '--vv-top': `${viewport?.top ?? 0}px`,
          '--vv-height': viewport ? `${viewport.height}px` : '100dvh',
        } as CSSProperties
      }
    >
      {/* 머리: 뒤로 · 3교시 이름 · 알림 끄기 · 3교시 보기 */}
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

      {/* 메시지 */}
      <div ref={listRef} onScroll={onScroll} className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 pb-3 sm:px-5">
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
            {messages.length === 0 && (
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
                <Bubble
                  m={m}
                  federated={page.federated}
                  first={first || m.id === firstUnread}
                  last={last}
                  selected={selected === m.id}
                  onSelect={() => setSelected(selected === m.id ? null : m.id)}
                  onPhoto={(i) =>
                    setViewer({ urls: m.photos.filter((p) => !p.expired && p.url).map((p) => p.url as string), start: i })
                  }
                />
                {selected === m.id && !m.deleted && (
                  <div className={`mt-1 flex gap-1 ${m.mine ? 'justify-end' : 'justify-start'}`}>
                    <span className="self-center px-1 text-[11px] text-ink-3">{formatDateTime(m.createdAt)}</span>
                    {m.mine ? (
                      <button
                        type="button"
                        onClick={() => deleteMessage(m)}
                        className="h-8 rounded-lg px-2.5 text-[13px] font-semibold text-danger-ink hover:bg-danger-soft"
                      >
                        지우기
                      </button>
                    ) : (
                      !m.hidden && (
                        <button
                          type="button"
                          onClick={() => reportMessage(m)}
                          className="h-8 rounded-lg px-2.5 text-[13px] font-semibold text-ink-2 hover:bg-surface-2"
                        >
                          신고·차단
                        </button>
                      )
                    )}
                  </div>
                )}
              </div>
            ))}
          </>
        )}
      </div>

      {newCount > 0 && (
        <div className="pointer-events-none relative">
          <button
            type="button"
            onClick={() => {
              scrollToBottom(true)
              setNewCount(0)
            }}
            className="pointer-events-auto absolute bottom-3 left-1/2 inline-flex h-9 -translate-x-1/2 items-center gap-1.5 rounded-full bg-board px-4 text-[13px] font-bold text-on-board shadow-lg"
          >
            <ArrowDown className="size-4" aria-hidden />새 메시지 {newCount}
          </button>
        </div>
      )}

      {/* 입력 */}
      {page && !page.canWrite ? (
        <p className="shrink-0 border-t border-line bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] text-center text-sm text-ink-3">
          취소된 3교시라 읽기만 할 수 있어요
        </p>
      ) : (
        <div className="shrink-0 border-t border-line bg-surface px-3 pt-2.5 pb-[calc(10px+env(safe-area-inset-bottom))] sm:px-5">
          {photos.length > 0 && (
            <div className="mb-2">
              <PhotoPicks size="sm" photos={photos} onRemove={(i) => setPhotos((p) => p.filter((_, j) => j !== i))} />
            </div>
          )}
          <div className="flex items-end gap-2">
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              disabled={!page || preparing || sending}
              aria-label={page ? `사진 보내기 (오늘 ${page.photosLeftToday}장 남음)` : '사진 보내기'}
              className="flex size-11 shrink-0 items-center justify-center rounded-full text-ink-2 hover:bg-surface-2 hover:text-ink disabled:opacity-40"
            >
              {preparing ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <ImagePlus className="size-[22px]" aria-hidden />}
            </button>
            <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={pick} />
            <textarea
              ref={inputRef}
              value={text}
              onChange={(e) => setText(e.target.value.slice(0, 1000))}
              onKeyDown={onKeyDown}
              rows={1}
              placeholder="메시지 보내기"
              aria-label="메시지"
              enterKeyHint={coarse ? 'enter' : 'send'}
              className="max-h-33 min-h-11 flex-1 resize-none rounded-[22px] border border-line-strong bg-surface-2 px-4 py-2.5 text-[15px] leading-snug text-ink focus:border-accent focus:outline-none"
            />
            <button
              type="button"
              onClick={submit}
              disabled={!canSend}
              aria-label="보내기"
              className="flex size-11 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent transition-opacity disabled:opacity-35"
            >
              {sending ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <ArrowUp className="size-5" strokeWidth={2.6} aria-hidden />}
            </button>
          </div>
        </div>
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
      {reporting && <ReportDialog key={`${reporting.type}-${reporting.id}`} target={reporting} onClose={() => setReporting(null)} />}
    </div>
  )
}
