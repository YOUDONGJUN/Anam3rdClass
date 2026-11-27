// 톡방 한 곳의 메시지 상태. 처음 50개를 읽고, 실시간 연결(lib/live)이 알려 주면 새 메시지만 더 읽는다.
// 실시간 연결이 끊겼으면 15초마다, 화면으로 돌아오면 바로 다시 읽는다.
// 보낸 메시지는 서버 답을 기다리지 않고 바로 "보내는 중"으로 보이고, 실패하면 다시 보낼 수 있다 (카톡처럼).
import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { markDeleted, withReaction } from '../lib/chat'
import { isLiveConnected, subscribeLive } from '../lib/live'
import { api, ApiError } from './client'
import { keys } from './queries'
import type { ChatMessage, ChatPage, ChatQuote, ChatUpdates, ReactionKind } from './types'
import { uploadPhotos } from './uploads'

/** 같은 번호는 새것으로 바꾸고 번호 순으로 */
export function mergeMessages(prev: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  if (incoming.length === 0) return prev
  const byId = new Map(prev.map((m) => [m.id, m]))
  incoming.forEach((m) => byId.set(m.id, m))
  return [...byId.values()].sort((a, b) => a.id - b.id)
}

function samePin(a: ChatQuote | null, b: ChatQuote | null) {
  return a === b || (a !== null && b !== null && a.id === b.id && a.preview === b.preview && a.sender === b.sender)
}

/** 보낼 것: 글·사진 또는 이모티콘 하나 (+ 답장할 메시지) */
export interface ChatDraft {
  body?: string
  photos?: Blob[]
  sticker?: string | null
  reply?: ChatQuote | null
}

/** 아직 서버에 닿지 않은 내 메시지 (보내는 중 · 실패) */
export interface PendingMessage {
  key: string
  body: string
  photos: Blob[]
  /** 사진 미리 보기 주소 (이 기기 안에서만) */
  previews: string[]
  sticker: string | null
  reply: ChatQuote | null
  status: 'sending' | 'failed'
  createdAt: string
}

const POLL_MS = 15_000
let pendingSeq = 0

export function useChatRoom(meetupId: number) {
  const qc = useQueryClient()
  const base = `/api/meetups/${meetupId}/chat`
  const [page, setPage] = useState<Omit<ChatPage, 'messages' | 'reads'> | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  /** 나 말고 함께하는 원우들이 각자 마지막으로 읽은 메시지 번호 (메시지 옆 "안 읽은 사람 수") */
  const [reads, setReads] = useState<number[]>([])
  const [pending, setPending] = useState<PendingMessage[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const lastId = useRef(0)
  const fetching = useRef(false)
  const again = useRef(false)
  /**
   * 처음 50개를 받았는지. 그 전에 실시간 연결이 먼저 붙으면 "0번 다음부터"를 읽게 되어 지난 메시지를 통째로(최대 200개) 받는다 —
   * 받기 전에 온 알림은 기억해 두었다가 받은 뒤에 한 번 읽는다
   */
  const loaded = useRef(false)
  const missed = useRef(false)
  /** 보낼 것을 차례대로 (먼저 보낸 것이 먼저 닿게) */
  const queue = useRef<Promise<unknown>>(Promise.resolve())

  const take = useCallback((incoming: ChatMessage[]) => {
    if (incoming.length === 0) return
    lastId.current = Math.max(lastId.current, ...incoming.map((m) => m.id))
    setMessages((prev) => mergeMessages(prev, incoming))
  }, [])

  const setPinned = useCallback((pinned: ChatQuote | null) => {
    setPage((p) => (p && !samePin(p.pinned, pinned) ? { ...p, pinned } : p))
  }, [])

  const apply = useCallback(
    (data: ChatPage) => {
      const { messages: list, reads: readIds, ...rest } = data
      setPage({ ...rest, pinned: rest.pinned ?? null, canPin: rest.canPin ?? false })
      setReads(readIds ?? [])
      lastId.current = 0
      setMessages([])
      take(list)
      setError(null)
      loaded.current = true
    },
    [take],
  )
  const fail = useCallback((e: unknown) => {
    setError(e instanceof ApiError ? e : new ApiError(0, 'ERROR', '불러오지 못했어요'))
  }, [])

  /** 다시 시도 (처음 불러오기가 실패했을 때) */
  const load = useCallback(() => {
    api<ChatPage>(base).then(apply, fail)
  }, [base, apply, fail])

  /** 새로 온 메시지 · 지금 읽은 자리 · 톡방 공지. 읽는 중에 또 부르면 끝난 뒤 한 번 더 */
  const fetchNew = useCallback(async () => {
    if (!loaded.current) {
      missed.current = true
      return
    }
    if (fetching.current) {
      again.current = true
      return
    }
    fetching.current = true
    try {
      do {
        again.current = false
        const data = await api<ChatUpdates>(`${base}/updates?after=${lastId.current}`)
        take(data.messages)
        if (data.reads) setReads(data.reads)
        if (data.pinned !== undefined) setPinned(data.pinned)
      } while (again.current)
    } catch {
      // 다음 알림·다시 읽기 때 받는다
    } finally {
      fetching.current = false
    }
  }, [base, take, setPinned])

  /** 이미 받은 메시지 하나를 다시 (공감이 바뀌었을 때) */
  const refreshMessage = useCallback(
    async (messageId: number) => {
      try {
        const fresh = await api<ChatMessage>(`${base}/${messageId}`)
        setMessages((prev) => prev.map((m) => (m.id === messageId ? fresh : m)))
      } catch {
        // 다음에 다시 읽을 때 맞춰진다
      }
    },
    [base],
  )

  // 이전 메시지 불러오기는 지금 가진 가장 오래된 메시지 앞에서 (여러 번 이어 불러도 같은 쪽을 다시 받지 않게)
  const oldest = useRef<number | null>(null)
  const hasMore = useRef(false)
  const loadingRef = useRef<Promise<boolean> | null>(null)
  useEffect(() => {
    oldest.current = messages[0]?.id ?? null
  }, [messages])
  useEffect(() => {
    hasMore.current = page?.hasMore ?? false
  }, [page?.hasMore])

  /** 이전 메시지 50개 더. 더 받았으면 true (더 없거나 못 받으면 false) */
  const loadOlder = useCallback((): Promise<boolean> => {
    if (loadingRef.current) return loadingRef.current
    const first = oldest.current
    if (first === null || !hasMore.current) return Promise.resolve(false)
    setLoadingOlder(true)
    const run = api<ChatPage>(`${base}?before=${first}`)
      .then((data) => {
        oldest.current = Math.min(first, ...data.messages.map((m) => m.id))
        hasMore.current = data.hasMore
        setMessages((prev) => mergeMessages(prev, data.messages))
        setPage((p) => (p ? { ...p, hasMore: data.hasMore } : p))
        return data.messages.length > 0
      })
      .catch(() => false)
      .finally(() => {
        loadingRef.current = null
        setLoadingOlder(false)
      })
    loadingRef.current = run
    return run
  }, [base])

  /** 지금 가진 가장 오래된 메시지 번호 (없으면 null) */
  const oldestId = useCallback(() => oldest.current, [])

  /** 서버로 보낸다: 사진은 먼저 저장소에 올린다 */
  const deliver = useCallback(
    async (p: PendingMessage) => {
      const replyToId = p.reply?.id ?? null
      let message: ChatMessage
      if (p.sticker) {
        message = await api<ChatMessage>(base, { method: 'POST', body: { sticker: p.sticker, replyToId } })
      } else {
        const photoKeys = await uploadPhotos(`${base}/uploads`, p.photos)
        message = await api<ChatMessage>(base, { method: 'POST', body: { body: p.body, photoKeys, replyToId } })
      }
      take([message])
      if (p.photos.length > 0) {
        setPage((x) => (x ? { ...x, photosLeftToday: Math.max(0, x.photosLeftToday - p.photos.length) } : x))
      }
      void qc.invalidateQueries({ queryKey: keys.chats })
    },
    [base, take, qc],
  )

  /** 차례가 오면 보낸다. 닿으면 "보내는 중"을 지우고, 실패하면 "실패"로 두고 에러를 던진다 */
  const run = useCallback(
    (p: PendingMessage): Promise<void> => {
      const done = queue.current.then(() => deliver(p))
      queue.current = done.catch(() => {})
      return done.then(
        () => {
          setPending((list) => list.filter((x) => x.key !== p.key))
          p.previews.forEach((u) => URL.revokeObjectURL(u))
        },
        (e: unknown) => {
          setPending((list) => list.map((x) => (x.key === p.key ? { ...x, status: 'failed' } : x)))
          throw e
        },
      )
    },
    [deliver],
  )

  /** 보내기: 바로 "보내는 중"으로 보이고 차례대로 보낸다. 실패하면 에러를 던진다 (말풍선은 "실패"로 남는다) */
  const send = useCallback(
    (draft: ChatDraft) => {
      const photos = draft.photos ?? []
      const p: PendingMessage = {
        key: `p${++pendingSeq}`,
        body: draft.body ?? '',
        photos,
        previews: photos.map((b) => URL.createObjectURL(b)),
        sticker: draft.sticker ?? null,
        reply: draft.reply ?? null,
        status: 'sending',
        createdAt: new Date().toISOString(),
      }
      setPending((list) => [...list, p])
      return run(p)
    },
    [run],
  )

  /** 실패한 메시지 다시 보내기 */
  const retry = useCallback(
    (key: string) => {
      const p = pending.find((x) => x.key === key)
      if (!p || p.status !== 'failed') return Promise.resolve()
      setPending((list) => list.map((x) => (x.key === key ? { ...x, status: 'sending' } : x)))
      return run(p)
    },
    [pending, run],
  )

  /** 실패한 메시지 보내지 않기 */
  const discard = useCallback((key: string) => {
    setPending((list) => {
      list.find((x) => x.key === key)?.previews.forEach((u) => URL.revokeObjectURL(u))
      return list.filter((x) => x.key !== key)
    })
  }, [])

  const remove = useCallback(
    async (messageId: number) => {
      await api<void>(`${base}/${messageId}`, { method: 'DELETE' })
      setMessages((prev) => markDeleted(prev, messageId))
      setPage((p) => (p?.pinned?.id === messageId ? { ...p, pinned: null } : p))
    },
    [base],
  )

  /** 공감: 화면에 먼저 바꾸고 서버 답으로 맞춘다. 실패하면 되돌리고 에러를 던진다 */
  const react = useCallback(
    async (messageId: number, reaction: ReactionKind | null) => {
      setMessages((prev) => prev.map((m) => (m.id === messageId ? withReaction(m, reaction) : m)))
      try {
        const fresh = await api<ChatMessage>(`${base}/${messageId}/reaction`, { method: 'PUT', body: { reaction } })
        setMessages((prev) => prev.map((m) => (m.id === messageId ? fresh : m)))
      } catch (e) {
        void refreshMessage(messageId)
        throw e
      }
    },
    [base, refreshMessage],
  )

  /** 톡방 공지 올리기 · 내리기(null) — 연 사람만 */
  const pin = useCallback(
    async (messageId: number | null) => {
      const { pinned } = await api<{ pinned: ChatQuote | null }>(`${base}/pin`, { method: 'PUT', body: { messageId } })
      setPinned(pinned)
    },
    [base, setPinned],
  )

  const lastRead = useRef(0)
  /** 여기까지 읽음 (같은 번호는 다시 보내지 않는다. keepAlive: 보고 있다고 다시 알림) */
  const markRead = useCallback(
    (id: number, keepAlive = false) => {
      if (id <= lastRead.current && !keepAlive) return
      lastRead.current = Math.max(lastRead.current, id)
      api<void>(`${base}/read`, { method: 'PUT', body: { lastId: lastRead.current } })
        .then(() => qc.invalidateQueries({ queryKey: keys.chats }))
        .catch(() => {})
    },
    [base, qc],
  )

  const setMuted = useCallback(
    async (muted: boolean) => {
      await api<void>(`${base}/mute`, { method: 'PUT', body: { muted } })
      setPage((p) => (p ? { ...p, muted } : p))
      void qc.invalidateQueries({ queryKey: keys.chats })
    },
    [base, qc],
  )

  useEffect(() => {
    let alive = true
    api<ChatPage>(base).then(
      (data) => alive && apply(data),
      (e) => alive && fail(e),
    )
    return () => {
      alive = false
    }
  }, [base, apply, fail])

  // 처음 50개를 받기 전에 온 소식이 있으면 받은 뒤에 읽는다
  useEffect(() => {
    if (page && missed.current) {
      missed.current = false
      void fetchNew()
    }
  }, [page, fetchNew])

  // 실시간: 이 톡방 소식이면 새 메시지를 읽는다. 다시 연결되면 놓친 것을 읽는다.
  // 다른 원우가 더 읽었으면 읽은 자리만, 공감이 바뀌었으면 그 메시지만, 공지가 바뀌었으면 공지만 다시 받는다
  useEffect(() => {
    return subscribeLive((e) => {
      if (e.type === 'connected') void fetchNew()
      if (e.type === 'chat-reads' && e.meetupId === meetupId) void fetchNew()
      if (e.type === 'chat-change' && e.meetupId === meetupId) {
        if (e.messageId === null) void fetchNew()
        else void refreshMessage(e.messageId)
      }
      if (e.type !== 'chat' || e.meetupId !== meetupId) return
      if (e.deleted) {
        setMessages((prev) => markDeleted(prev, e.messageId))
        void fetchNew() // 지운 메시지가 공지였으면 공지도 내려간다
      } else {
        void fetchNew()
      }
    })
  }, [meetupId, fetchNew, refreshMessage])

  // 실시간 연결이 없을 때는 가끔, 화면으로 돌아오면 바로
  useEffect(() => {
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible' && !isLiveConnected()) void fetchNew()
    }, POLL_MS)
    const onVisible = () => {
      if (document.visibilityState === 'visible') void fetchNew()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [fetchNew])

  // 톡방을 닫으면 이제 새 메시지는 폰 알림으로
  useEffect(() => {
    return () => {
      api<void>(`${base}/leave`, { method: 'POST' }).catch(() => {})
    }
  }, [base])

  return {
    page,
    messages,
    reads,
    pending,
    error,
    reload: load,
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
  }
}
