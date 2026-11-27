// 톡방 한 곳의 메시지 상태. 처음 50개를 읽고, 실시간 연결(lib/live)이 알려 주면 새 메시지만 더 읽는다.
// 실시간 연결이 끊겼으면 15초마다, 화면으로 돌아오면 바로 다시 읽는다.
import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { isLiveConnected, subscribeLive } from '../lib/live'
import { api, ApiError } from './client'
import { keys } from './queries'
import type { ChatMessage, ChatPage, ChatUpdates } from './types'
import { uploadPhotos } from './uploads'

/** 같은 번호는 새것으로 바꾸고 번호 순으로 */
export function mergeMessages(prev: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  if (incoming.length === 0) return prev
  const byId = new Map(prev.map((m) => [m.id, m]))
  incoming.forEach((m) => byId.set(m.id, m))
  return [...byId.values()].sort((a, b) => a.id - b.id)
}

const POLL_MS = 15_000

export function useChatRoom(meetupId: number) {
  const qc = useQueryClient()
  const base = `/api/meetups/${meetupId}/chat`
  const [page, setPage] = useState<Omit<ChatPage, 'messages' | 'reads'> | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  /** 나 말고 함께하는 원우들이 각자 마지막으로 읽은 메시지 번호 (메시지 옆 "안 읽은 사람 수") */
  const [reads, setReads] = useState<number[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const lastId = useRef(0)
  const fetching = useRef(false)
  const again = useRef(false)

  const take = useCallback((incoming: ChatMessage[]) => {
    if (incoming.length === 0) return
    lastId.current = Math.max(lastId.current, ...incoming.map((m) => m.id))
    setMessages((prev) => mergeMessages(prev, incoming))
  }, [])

  const apply = useCallback(
    (data: ChatPage) => {
      const { messages: list, reads: readIds, ...rest } = data
      setPage(rest)
      setReads(readIds ?? [])
      lastId.current = 0
      setMessages([])
      take(list)
      setError(null)
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

  /** 새로 온 메시지와 지금 읽은 자리. 읽는 중에 또 부르면 끝난 뒤 한 번 더 */
  const fetchNew = useCallback(async () => {
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
      } while (again.current)
    } catch {
      // 다음 알림·다시 읽기 때 받는다
    } finally {
      fetching.current = false
    }
  }, [base, take])

  const loadOlder = useCallback(async () => {
    const first = messages[0]
    if (!first || loadingOlder) return
    setLoadingOlder(true)
    try {
      const data = await api<ChatPage>(`${base}?before=${first.id}`)
      setMessages((prev) => mergeMessages(prev, data.messages))
      setPage((p) => (p ? { ...p, hasMore: data.hasMore } : p))
    } finally {
      setLoadingOlder(false)
    }
  }, [base, messages, loadingOlder])

  /** 보내기: 사진은 먼저 저장소에 올린다. 실패하면 에러를 던진다 (쓴 글은 화면이 그대로 둔다) */
  const send = useCallback(
    async (body: string, photos: Blob[]) => {
      const photoKeys = await uploadPhotos(`${base}/uploads`, photos)
      const message = await api<ChatMessage>(base, { method: 'POST', body: { body, photoKeys } })
      take([message])
      if (photos.length > 0) {
        setPage((p) => (p ? { ...p, photosLeftToday: Math.max(0, p.photosLeftToday - photos.length) } : p))
      }
      void qc.invalidateQueries({ queryKey: keys.chats })
      return message
    },
    [base, take, qc],
  )

  /** 이모티콘 보내기 (글·사진과 따로) */
  const sendSticker = useCallback(
    async (sticker: string) => {
      const message = await api<ChatMessage>(base, { method: 'POST', body: { sticker } })
      take([message])
      void qc.invalidateQueries({ queryKey: keys.chats })
      return message
    },
    [base, take, qc],
  )

  const remove = useCallback(
    async (messageId: number) => {
      await api<void>(`${base}/${messageId}`, { method: 'DELETE' })
      setMessages((prev) =>
        prev.map((m) => (m.id === messageId ? { ...m, deleted: true, body: null, photos: [] } : m)),
      )
    },
    [base],
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

  // 실시간: 이 톡방 소식이면 새 메시지를 읽는다. 다시 연결되면 놓친 것을 읽는다.
  // 다른 원우가 더 읽었으면 읽은 자리만 다시 받는다 (새 메시지가 있으면 함께)
  useEffect(() => {
    return subscribeLive((e) => {
      if (e.type === 'connected') void fetchNew()
      if (e.type === 'chat-reads' && e.meetupId === meetupId) void fetchNew()
      if (e.type !== 'chat' || e.meetupId !== meetupId) return
      if (e.deleted) {
        setMessages((prev) =>
          prev.map((m) => (m.id === e.messageId ? { ...m, deleted: true, body: null, photos: [] } : m)),
        )
      } else {
        void fetchNew()
      }
    })
  }, [meetupId, fetchNew])

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
    error,
    reload: load,
    loadOlder,
    loadingOlder,
    send,
    sendSticker,
    remove,
    markRead,
    setMuted,
  }
}
