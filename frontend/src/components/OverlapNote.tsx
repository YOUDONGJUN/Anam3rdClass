import { Link } from 'react-router'
import { CalendarX2 } from 'lucide-react'
import type { MeetupSummary } from '../api/types'
import { OVERLAP_HOURS } from '../lib/meetup'
import { formatDateTime } from '../lib/time'

/**
 * 시간이 겹치는 내 3교시 안내. 까닭 한 줄 + 그 3교시(제목 · 때) 한 줄로 나눠, 제목 가운데서 줄이 바뀌지 않게 한다.
 * link: 그 3교시로 가는 링크 (쓰던 글이 없는 상세 화면에서만)
 */
export function OverlapNote({
  meetup,
  now,
  link = false,
}: {
  meetup: Pick<MeetupSummary, 'id' | 'title' | 'meetAt' | 'hosting'>
  now?: Date
  link?: boolean
}) {
  const what = `'${meetup.title}' · ${formatDateTime(meetup.meetAt, now)}`
  return (
    <div className="flex items-start gap-2 text-sm leading-relaxed">
      <CalendarX2 className="mt-0.5 size-4 shrink-0 text-danger-ink" aria-hidden />
      <p className="min-w-0">
        <span className="block font-semibold text-danger-ink">
          앞뒤 {OVERLAP_HOURS}시간 안에 이미 {meetup.hosting ? '연' : '참여한'} 3교시가 있어요
        </span>
        {link ? (
          <Link
            to={`/meetups/${meetup.id}`}
            className="font-semibold text-ink-2 underline decoration-line-strong underline-offset-2 hover:text-ink"
          >
            {what}
          </Link>
        ) : (
          <span className="text-ink-2">{what}</span>
        )}
      </p>
    </div>
  )
}
