import type { ChatMessage, ChatQuote, ReactionKind } from '../api/types'
import { stickerOf } from './stickers'
import { formatDate, kstDayKey } from './time'

/** 같은 사람이 이 시간 안에 이어 보낸 메시지는 한 덩어리로 (이름·시각을 한 번만) */
const GROUP_GAP_MS = 3 * 60_000

/** 손가락이 주로 쓰이는 기기인지 (폰: 길게 눌러 메뉴 · Enter 는 줄바꿈 / PC: 눌러 메뉴 · Enter 는 보내기) */
export const coarsePointer = typeof window !== 'undefined' && Boolean(window.matchMedia?.('(pointer: coarse)').matches)

export interface ChatRow {
  message: ChatMessage
  /** 날이 바뀌면 그 날짜 ("3월 2일 (월)") */
  dayBreak: string | null
  /** 덩어리의 첫 메시지 — 이름을 보인다 */
  first: boolean
  /** 덩어리의 마지막 메시지 — 시각을 보인다 */
  last: boolean
}

/** "○○님이 들어왔어요" 같은 안내 줄 */
export function isNotice(m: ChatMessage): boolean {
  return m.kind !== 'TEXT'
}

function sameGroup(a: ChatMessage, b: ChatMessage) {
  return (
    !isNotice(a) &&
    !isNotice(b) &&
    a.mine === b.mine &&
    a.sender.name === b.sender.name &&
    a.sender.ref === b.sender.ref &&
    kstDayKey(a.createdAt) === kstDayKey(b.createdAt) &&
    new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() <= GROUP_GAP_MS
  )
}

export function groupMessages(messages: ChatMessage[]): ChatRow[] {
  return messages.map((message, i) => {
    const prev = messages[i - 1]
    const next = messages[i + 1]
    const dayBreak =
      !prev || kstDayKey(prev.createdAt) !== kstDayKey(message.createdAt) ? formatDate(message.createdAt) : null
    return {
      message,
      dayBreak,
      first: !prev || dayBreak !== null || !sameGroup(prev, message),
      last: !next || !sameGroup(message, next),
    }
  })
}

export function noticeText(m: ChatMessage): string {
  return `${m.sender.name}님이 ${m.kind === 'JOIN' ? '들어왔어요' : '나갔어요'}`
}

/** 톡방 목록·알림에 보일 한 줄 */
export function previewOf(message: ChatMessage): string {
  if (isNotice(message)) return noticeText(message)
  if (message.deleted) return '지운 메시지예요'
  if (message.hidden) return '차단한 원우의 메시지예요'
  if (message.sticker) return `(이모티콘) ${stickerOf(message.sticker)?.label ?? ''}`.trim()
  if (message.body) return message.body
  return `사진 ${message.photos.length}장`
}

/**
 * 이 메시지를 아직 안 읽은 사람 수 (카톡의 숫자 1). reads: 나 말고 함께하는 원우들이 각자 마지막으로 읽은 메시지 번호.
 * 보낸 사람은 보낸 순간 읽은 것으로, 나중에 들어온 사람은 들어온 순간까지 읽은 것으로 서버가 쳐 둔다.
 * 안내 줄 · 지운 메시지는 세지 않는다
 */
export function unreadCount(message: ChatMessage, reads: number[]): number {
  if (isNotice(message) || message.deleted) return 0
  return reads.reduce((n, r) => (r < message.id ? n + 1 : n), 0)
}

/** 공감 여섯 가지 (서버 ChatReactionKind 와 같은 순서) */
export const REACTIONS: { reaction: ReactionKind; emoji: string; label: string }[] = [
  { reaction: 'HEART', emoji: '❤️', label: '하트' },
  { reaction: 'LIKE', emoji: '👍', label: '좋아요' },
  { reaction: 'CHECK', emoji: '✅', label: '확인' },
  { reaction: 'LOL', emoji: '😂', label: '웃겨요' },
  { reaction: 'WOW', emoji: '😮', label: '놀라워요' },
  { reaction: 'SAD', emoji: '😢', label: '슬퍼요' },
]

export function reactionEmoji(reaction: ReactionKind): string {
  return REACTIONS.find((r) => r.reaction === reaction)?.emoji ?? '❤️'
}

/** 내 공감 (없으면 null) */
export function myReaction(m: ChatMessage): ReactionKind | null {
  return m.reactions.find((r) => r.mine)?.reaction ?? null
}

/** 같은 공감을 다시 누르면 빼고, 다른 것을 누르면 바꾼다 */
export function nextReaction(m: ChatMessage, pressed: ReactionKind): ReactionKind | null {
  return myReaction(m) === pressed ? null : pressed
}

/** 서버 답을 기다리지 않고 화면에 먼저: 내 공감을 next 로 바꾼 메시지 */
export function withReaction(m: ChatMessage, next: ReactionKind | null): ChatMessage {
  const mine = myReaction(m)
  const counts = new Map(m.reactions.map((r) => [r.reaction, r.count]))
  if (mine) counts.set(mine, (counts.get(mine) ?? 1) - 1)
  if (next) counts.set(next, (counts.get(next) ?? 0) + 1)
  const reactions = REACTIONS.filter((r) => (counts.get(r.reaction) ?? 0) > 0).map((r) => ({
    reaction: r.reaction,
    count: counts.get(r.reaction) as number,
    mine: r.reaction === next,
  }))
  return { ...m, reactions }
}

/** 답장할 때 입력창 위에 보일 원래 메시지 */
export function quoteOf(m: ChatMessage): ChatQuote {
  return { id: m.id, sender: m.sender.name, mine: m.mine, preview: previewOf(m) }
}

/** "김*대에게 답장" · "나에게 답장" */
export function replyLabel(q: ChatQuote): string {
  return q.mine ? '나에게 답장' : `${q.sender}에게 답장`
}

/** 메시지를 지웠다: 그 메시지와, 그것을 인용한 답장들의 한 줄을 바꾼다 */
export function markDeleted(messages: ChatMessage[], id: number): ChatMessage[] {
  return messages.map((m) =>
    m.id === id
      ? { ...m, deleted: true, body: null, sticker: null, photos: [], reactions: [], reply: null }
      : m.reply?.id === id
        ? { ...m, reply: { ...m.reply, preview: '지운 메시지예요' } }
        : m,
  )
}
