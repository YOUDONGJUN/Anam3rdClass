import type { ChatMessage } from '../api/types'
import { formatDate, kstDayKey } from './time'

/** 같은 사람이 이 시간 안에 이어 보낸 메시지는 한 덩어리로 (이름·시각을 한 번만) */
const GROUP_GAP_MS = 3 * 60_000

export interface ChatRow {
  message: ChatMessage
  /** 날이 바뀌면 그 날짜 ("3월 2일 (월)") */
  dayBreak: string | null
  /** 덩어리의 첫 메시지 — 이름을 보인다 */
  first: boolean
  /** 덩어리의 마지막 메시지 — 시각을 보인다 */
  last: boolean
}

function sameGroup(a: ChatMessage, b: ChatMessage) {
  return (
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

/** 톡방 목록·알림에 보일 한 줄 */
export function previewOf(message: ChatMessage): string {
  if (message.deleted) return '지운 메시지예요'
  if (message.hidden) return '차단한 원우의 메시지예요'
  if (message.body) return message.body
  return `사진 ${message.photos.length}장`
}
