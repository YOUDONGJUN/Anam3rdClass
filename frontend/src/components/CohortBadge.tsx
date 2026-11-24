import { Globe2, Lock } from 'lucide-react'
import type { MeetupSummary } from '../api/types'

/**
 * 누구에게 연 3교시인지: "61기 전용"(우리 기수) · "연합"(다섯 학교). 우리 학교(기본)는 표시하지 않는다.
 */
export function AudienceBadge({
  meetup,
  tone = 'surface',
}: {
  meetup: Pick<MeetupSummary, 'audience' | 'onlyCohort'>
  tone?: 'surface' | 'board'
}) {
  if (meetup.audience === 'SCHOOL') return null
  const toneClass = tone === 'board' ? 'bg-board-line text-on-board' : 'bg-board text-on-board'
  const federated = meetup.audience === 'ALL'
  return (
    <span className={`inline-flex shrink-0 items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-bold ${toneClass}`}>
      {federated ? (
        <Globe2 className="size-3" strokeWidth={2.6} aria-hidden />
      ) : (
        <Lock className="size-3" strokeWidth={2.6} aria-hidden />
      )}
      {federated ? '연합' : `${meetup.onlyCohort}기 전용`}
    </span>
  )
}
