import { Link } from 'react-router'
import { Clock, MapPin } from 'lucide-react'
import type { MeetupSummary } from '../api/types'
import { isLive, seatsCaption } from '../lib/meetup'
import { formatDate, formatDayLabel, formatRemaining, formatTime } from '../lib/time'
import { CategoryBadges } from './CategoryBadge'
import { AudienceBadge } from './CohortBadge'
import { DepartureBoard } from './DepartureBoard'
import { LineGauge } from './LineGauge'
import { MeetupOptionTags } from './MeetupOptionTags'
import { MeetupRole } from './MeetupRow'
import { PersonName } from './PersonName'
import { PurposeTags } from './PurposeTags'
import { StatusPill } from './StatusPill'
import { cardClass } from './styles'

/** 목록 맨 위 "다음 출발" — 가장 먼저 모이는 3교시를 역 출발 안내판처럼 크게 */
export function NextDeparture({ meetup: m, now }: { meetup: MeetupSummary; now: Date }) {
  // 더 탈 수 있을 때만 마감까지 남은 시간 (만석이면 빼다 — 목록 한 줄과 같은 조건)
  const recruiting = isLive(m.status) && m.joinable && new Date(m.deadlineAt) > now
  const day = formatDayLabel(m.meetAt, now)
  const date = formatDate(m.meetAt)
  const time = formatTime(m.meetAt)
  const captionTone =
    m.status === 'RECRUITING' ? 'text-accent-ink' : m.status === 'CONFIRMED' ? 'text-ok-ink' : 'text-ink-3'

  return (
    <article className={`${cardClass} relative overflow-hidden`}>
      <div className="border-b border-line bg-accent-soft px-4.5 py-4">
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs font-extrabold text-accent-ink">
            {day} 출발{day !== date && <span className="font-semibold"> · {date}</span>}
          </span>
          <StatusPill meetup={m} />
        </div>
        <div className="mt-3 flex flex-wrap items-end justify-between gap-x-3 gap-y-2">
          <DepartureBoard time={time} label={`${day} ${time} 출발`} />
          {recruiting && (
            <span className="tabular inline-flex items-center gap-1.5 text-[13px] font-semibold text-ink-2">
              <Clock className="size-3.5" aria-hidden />
              마감 {formatRemaining(m.deadlineAt, now)} 전
            </span>
          )}
        </div>
      </div>

      <div className="px-4.5 pt-4 pb-5">
        <div className="flex items-start gap-2.5">
          <CategoryBadges categories={m.categories} />
          <h3 className="min-w-0 flex-1 pt-0.5 text-lg leading-snug font-extrabold tracking-[-0.02em] text-ink">
            <Link
              to={`/meetups/${m.id}`}
              state={{ backLabel: '3교시 목록' }}
              className="outline-none after:absolute after:inset-0 after:content-[''] focus-visible:underline"
            >
              {m.title}
            </Link>
          </h3>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-sm text-ink-2">
          <AudienceBadge meetup={m} />
          <p className="flex max-w-full min-w-0 items-center gap-1">
            <MapPin className="size-3.5 shrink-0 text-ink-3" aria-hidden />
            <span className="truncate">{m.place}</span>
          </p>
          {m.purposes.length > 0 && <PurposeTags purposes={m.purposes} />}
          <MeetupOptionTags meetup={m} />
        </div>

        <div className="mt-4 rounded-xl bg-surface-2 px-3.5 pt-3.5 pb-3 shadow-[inset_0_1px_2px_rgb(0_0_0/0.08)]">
          <LineGauge size="lg" count={m.participantCount} min={m.minPeople} max={m.maxPeople} status={m.status} />
          <div className="mt-2.5 flex items-baseline justify-between gap-3">
            <span className={`text-sm font-bold ${captionTone}`}>{seatsCaption(m)}</span>
            <span className="tabular shrink-0 text-[15px] font-extrabold text-ink">
              {m.participantCount}
              <span className="font-medium text-ink-3">{m.maxPeople === null ? '명' : `/${m.maxPeople}`}</span>
            </span>
          </div>
        </div>

        {m.audience === 'ALL' && (
          <p className="mt-3 flex min-w-0 items-center gap-1 text-[13px] text-ink-3">
            <PersonName person={m.host} showMe={false} showSchool />
          </p>
        )}

        {m.joinable && !m.joined && !m.hosting ? (
          <div className="mt-4 pb-1">
            <MeetupRole meetup={m} block />
          </div>
        ) : (
          (m.joined || m.hosting) && (
            <div className="mt-4">
              <MeetupRole meetup={m} />
            </div>
          )
        )}
      </div>
    </article>
  )
}
