import { Link } from 'react-router'
import { MeetupOptionTags } from './MeetupOptionTags'
import { Check, MapPin } from 'lucide-react'
import type { MeetupSummary } from '../api/types'
import { useMeetupAction } from '../api/queries'
import { formatDayLabel, formatRemaining, formatTime } from '../lib/time'
import { isLive, joinedLabel, lateJoinConfirm, seatsCaption } from '../lib/meetup'
import { CategoryBadges } from './CategoryBadge'
import { AudienceBadge } from './CohortBadge'
import { LineGauge } from './LineGauge'
import { PersonName } from './PersonName'
import { PurposeTags } from './PurposeTags'
import { StatusPill } from './StatusPill'
import { Button } from './ui'
import { useToast } from './toastContext'

/** 목록에서 바로 참여. block 이면 "다음 출발" 카드처럼 큰 버튼 */
export function QuickJoin({ meetup, block = false }: { meetup: MeetupSummary; block?: boolean }) {
  const action = useMeetupAction()
  const toast = useToast()
  return (
    <Button
      size={block ? 'lg' : 'sm'}
      className={`relative z-10 ${block ? 'w-full' : ''}`}
      loading={action.isPending}
      onClick={() => {
        // 마감 뒤(중간 합류) 참여는 되돌릴 수 없어서 한 번 더 묻는다
        const warn = lateJoinConfirm(meetup)
        if (warn && !window.confirm(warn)) return
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
      }}
    >
      나도 콜
    </Button>
  )
}

/** 내 3교시 · 참여 중 · 바로 참여 — 줄 오른쪽 끝 */
export function MeetupRole({ meetup, block = false }: { meetup: MeetupSummary; block?: boolean }) {
  if (meetup.hosting)
    return <span className="rounded-lg bg-board px-2 py-1 text-xs font-bold text-on-board">내 3교시</span>
  if (meetup.joined) {
    // 지난 3교시는 "참여함"(회색), 무산·취소된 3교시는 붙이지 않는다
    const label = joinedLabel(meetup.status)
    if (!label) return null
    const tone = isLive(meetup.status) ? 'bg-ok-soft text-ok-ink' : 'bg-surface-2 text-ink-2'
    return (
      <span className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-bold ${tone}`}>
        <Check className="size-3.5" strokeWidth={3} aria-hidden />
        {label}
      </span>
    )
  }
  if (meetup.joinable) return <QuickJoin meetup={meetup} block={block} />
  return null
}

/**
 * 3교시 목록의 한 줄 — 왼쪽 시간 칸(안내판 숫자판), 오른쪽 정보 + 노선도 게이지.
 * hideTime: 위에 시간을 따로 적는 곳(내 3교시 노선)에서는 시간 칸을 뺀다
 */
export function MeetupRow({
  meetup,
  showDate = false,
  hideTime = false,
  backLabel,
  now,
}: {
  meetup: MeetupSummary
  showDate?: boolean
  hideTime?: boolean
  /** 상세의 뒤로 버튼 이름 (이 목록 이름) */
  backLabel?: string
  now: Date
}) {
  const live = isLive(meetup.status)
  // 정원이 차서 못 들어가면 마감까지 남은 시간은 보이지 않는다 ('만석'과 부딪히지 않게)
  const recruiting = live && meetup.joinable && new Date(meetup.deadlineAt) > now
  return (
    <article className="group relative flex gap-3.5 px-4 py-4 transition-colors focus-within:bg-surface-2 hover:bg-surface-2/70 sm:gap-5 sm:px-5">
      {!hideTime && (
        <div className="w-[58px] shrink-0 self-start rounded-xl bg-surface-2 py-2 text-center shadow-[inset_0_0_0_1px_var(--line)] sm:w-16">
          <p
            className={`tabular text-[17px] leading-none font-extrabold tracking-[-0.02em] whitespace-nowrap sm:text-xl ${live ? 'text-ink' : 'text-ink-3'}`}
          >
            {formatTime(meetup.meetAt)}
          </p>
          {showDate && <p className="mt-1.5 text-[11px] font-semibold text-ink-3">{formatDayLabel(meetup.meetAt, now)}</p>}
        </div>
      )}

      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <CategoryBadges categories={meetup.categories} size="sm" />
          <h3 className="min-w-0 flex-1 pt-0.5 text-base leading-snug font-bold text-ink">
            <Link
              to={`/meetups/${meetup.id}`}
              state={backLabel ? { backLabel } : undefined}
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
          <MeetupOptionTags meetup={meetup} />
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
            <MeetupRole meetup={meetup} />
          </div>
        </div>

        {/* 한 줄로 흐르는 글: '·' 앞은 붙는 공백이라 줄이 바뀌어도 '·'가 줄 첫머리에 오지 않는다 */}
        <p className="mt-2 text-[13px] text-ink-3">
          <span className={meetup.status === 'RECRUITING' ? 'font-semibold text-accent-ink' : ''}>
            {seatsCaption(meetup)}
          </span>
          {recruiting && (
            <>
              {'\u00a0· '}
              <span className="whitespace-nowrap">마감 {formatRemaining(meetup.deadlineAt, now)} 전</span>
            </>
          )}
          {meetup.audience !== 'ALL' && (
            <span className="hidden sm:inline">
              {'\u00a0· '}
              <PersonName person={meetup.host} showMe={false} />
            </span>
          )}
        </p>
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
      <div className="h-11 w-[58px] animate-pulse rounded-xl bg-surface-2" />
      <div className="flex-1 space-y-3">
        <div className="h-4 w-2/3 animate-pulse rounded bg-surface-2" />
        <div className="h-3 w-1/3 animate-pulse rounded bg-surface-2" />
        <div className="h-2 w-1/2 animate-pulse rounded bg-surface-2" />
      </div>
    </div>
  )
}
