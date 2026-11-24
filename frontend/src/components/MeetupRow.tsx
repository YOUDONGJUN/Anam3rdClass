import { Link } from 'react-router'
import { MapPin } from 'lucide-react'
import type { MeetupSummary } from '../api/types'
import { useMeetupAction } from '../api/queries'
import { formatDayLabel, formatRemaining, formatTime } from '../lib/time'
import { isLive, seatsCaption } from '../lib/meetup'
import { CategoryBadges } from './CategoryBadge'
import { AudienceBadge } from './CohortBadge'
import { LineGauge } from './LineGauge'
import { PersonName } from './PersonName'
import { PurposeTags } from './PurposeTags'
import { StatusPill } from './StatusPill'
import { Button } from './ui'
import { useToast } from './toastContext'

function QuickJoin({ meetup }: { meetup: MeetupSummary }) {
  const action = useMeetupAction()
  const toast = useToast()
  return (
    <Button
      size="sm"
      className="relative z-10"
      loading={action.isPending}
      onClick={() =>
        action.mutate(
          { id: meetup.id, action: 'join' },
          {
            onSuccess: (d) =>
              toast.show(
                d.status !== 'CONFIRMED'
                  ? '참여했어요. 성사되면 이름이 공개돼요'
                  : meetup.status === 'RECRUITING'
                    ? '성사! 이제 서로 이름이 보여요'
                    : '참여했어요. 이제 함께하는 사람들 이름이 보여요',
                'ok',
              ),
            onError: (e) => toast.show(e.message, 'error'),
          },
        )
      }
    >
      나도 콜
    </Button>
  )
}

/** 3교시 목록의 한 줄 — 왼쪽 시간 칸, 오른쪽 정보 + 노선도 게이지 */
export function MeetupRow({ meetup, showDate = false, now }: { meetup: MeetupSummary; showDate?: boolean; now: Date }) {
  const live = isLive(meetup.status)
  const recruiting = live && new Date(meetup.deadlineAt) > now
  return (
    <article className="group relative flex gap-3.5 px-4 py-4 transition-colors focus-within:bg-surface-2 hover:bg-surface-2/70 sm:gap-5 sm:px-5">
      <div className="w-[54px] shrink-0 pt-0.5 sm:w-16">
        <p className={`tabular text-lg leading-none font-extrabold tracking-[-0.02em] whitespace-nowrap sm:text-[22px] ${live ? 'text-ink' : 'text-ink-3'}`}>
          {formatTime(meetup.meetAt)}
        </p>
        {showDate && <p className="mt-1.5 text-xs font-medium text-ink-3">{formatDayLabel(meetup.meetAt, now)}</p>}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <CategoryBadges categories={meetup.categories} size="sm" />
          <h3 className="min-w-0 flex-1 pt-0.5 text-base leading-snug font-bold text-ink">
            <Link
              to={`/meetups/${meetup.id}`}
              className="outline-none after:absolute after:inset-0 after:content-[''] focus-visible:underline"
            >
              {meetup.title}
            </Link>
          </h3>
          <StatusPill meetup={meetup} />
        </div>

        <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ink-2">
          <AudienceBadge meetup={meetup} />
          <p className="flex max-w-full min-w-0 items-center gap-1">
            <MapPin className="size-3.5 shrink-0 text-ink-3" aria-hidden />
            <span className="truncate">{meetup.place}</span>
          </p>
          {meetup.purposes.length > 0 && <PurposeTags purposes={meetup.purposes} />}
        </div>

        <div className="mt-3 flex items-center gap-3">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <LineGauge
              count={meetup.participantCount}
              min={meetup.minPeople}
              max={meetup.maxPeople}
              status={meetup.status}
            />
            <span className="tabular shrink-0 text-sm font-bold text-ink">
              {meetup.participantCount}
              <span className="font-medium text-ink-3">{meetup.maxPeople === null ? '명' : `/${meetup.maxPeople}`}</span>
            </span>
          </div>
          <div className="shrink-0">
            {meetup.hosting ? (
              <span className="rounded-md bg-board px-2 py-1 text-xs font-bold text-on-board">내 3교시</span>
            ) : meetup.joined ? (
              <span className="rounded-md bg-ok-soft px-2 py-1 text-xs font-bold text-ok-ink">참여 중</span>
            ) : meetup.joinable ? (
              <QuickJoin meetup={meetup} />
            ) : null}
          </div>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] text-ink-3">
          <span className={meetup.status === 'RECRUITING' ? 'font-semibold text-accent-ink' : ''}>
            {seatsCaption(meetup)}
          </span>
          {recruiting && <span>· 마감 {formatRemaining(meetup.deadlineAt, now)} 전</span>}
          {meetup.audience !== 'ALL' && (
            <span className="hidden min-w-0 items-center gap-1 sm:flex">
              · <PersonName person={meetup.host} showMe={false} />
            </span>
          )}
        </div>
        {meetup.audience === 'ALL' && (
          // 연합: 어느 학교 사람이 열었는지 늘 보여준다 (모바일에서도)
          <p className="mt-1 flex min-w-0 items-center gap-1 text-[13px] text-ink-3">
            <PersonName person={meetup.host} showMe={false} showSchool />
          </p>
        )}
      </div>
    </article>
  )
}

export function MeetupRowSkeleton() {
  return (
    <div className="flex gap-4 px-5 py-5" aria-hidden>
      <div className="h-5 w-12 animate-pulse rounded bg-surface-2" />
      <div className="flex-1 space-y-3">
        <div className="h-4 w-2/3 animate-pulse rounded bg-surface-2" />
        <div className="h-3 w-1/3 animate-pulse rounded bg-surface-2" />
        <div className="h-2 w-1/2 animate-pulse rounded bg-surface-2" />
      </div>
    </div>
  )
}
