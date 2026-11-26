import { Clock3, Wallet } from 'lucide-react'
import type { MeetupSummary } from '../api/types'
import { feeLabel, showsLateJoin } from '../lib/meetup'

/** 참여비 · 중간 합류 OK 표시 (없으면 아무것도 안 그린다). 중간 합류는 모이는 중일 때만 */
export function MeetupOptionTags({
  meetup,
  tone = 'surface',
}: {
  meetup: Pick<MeetupSummary, 'fee' | 'feePrepaid' | 'lateJoin' | 'status'>
  tone?: 'surface' | 'board'
}) {
  const lateJoin = showsLateJoin(meetup)
  if (meetup.fee === null && !lateJoin) return null
  const tag =
    tone === 'board' ? 'bg-board-line text-on-board' : 'bg-surface-2 text-ink-2'
  return (
    <ul className="flex flex-wrap gap-1" aria-label="참여 방식">
      {meetup.fee !== null && (
        <li className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap ${tag}`}>
          <Wallet className="size-3" aria-hidden />
          {feeLabel(meetup.fee, meetup.feePrepaid)}
        </li>
      )}
      {lateJoin && (
        <li className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap ${tag}`}>
          <Clock3 className="size-3" aria-hidden />
          중간 합류 OK
        </li>
      )}
    </ul>
  )
}
