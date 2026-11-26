import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import {
  ArrowUpRight,
  Bell,
  BellOff,
  CalendarPlus,
  CheckCheck,
  ChevronLeft,
  Copy,
  Crown,
  EyeOff,
  Flag,
  Globe2,
  Hash,
  Info,
  Lock,
  MapPin,
  MessageCircle,
  PartyPopper,
  Pencil,
  Share2,
  Trash2,
  UserMinus,
  UserPlus,
  Users,
  Wallet,
} from 'lucide-react'
import { ApiError } from '../api/client'
import {
  type MeetupActionKind,
  useAddSeats,
  useDeleteMeetup,
  useHostTool,
  useMeetup,
  useMeetupAction,
  useMyMeetups,
} from '../api/queries'
import type { MeetupDetail } from '../api/types'
import { CategoryBadges } from '../components/CategoryBadge'
import { FeedbackCard } from '../components/FeedbackCard'
import { MeetupOptionTags } from '../components/MeetupOptionTags'
import { OverlapNote } from '../components/OverlapNote'
import { AudienceBadge } from '../components/CohortBadge'
import { ParticipantRoute } from '../components/LineGauge'
import { PersonName } from '../components/PersonName'
import { PurposeTags } from '../components/PurposeTags'
import { ReportDialog, type ReportTargetInfo } from '../components/ReportDialog'
import { SettlementCard } from '../components/SettlementCard'
import { StatusPill } from '../components/StatusPill'
import { Button, ErrorState, Spinner } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { downloadIcs, googleCalendarUrl, type CalendarEvent } from '../lib/calendar'
import { MAP_PROVIDERS } from '../lib/map'
import {
  CATEGORY,
  findOverlap,
  headcount,
  hiddenNamesNote,
  isLive,
  lateJoinConfirm,
  seatsCaption,
  seatsToAdd,
  showsLateJoin,
  sortCategories,
} from '../lib/meetup'
import { isApp, shareLink } from '../lib/native'
import { affiliation } from '../lib/person'
import { formatDate, formatDateTime, formatDayLabel, formatRemaining, formatTime } from '../lib/time'
import { DepartureBoard } from '../components/DepartureBoard'

/** 승차권 아래칸 왼쪽: 모집 마감까지 / 모임까지 남은 시간 */
function TicketCountdown({ m, now }: { m: MeetupDetail; now: Date }) {
  if (!isLive(m.status)) {
    return (
      <div>
        <p className="text-xs font-bold text-ink-3">모임</p>
        <p className="mt-1.5 text-[15px] font-bold text-ink-2">{formatDateTime(m.meetAt, now)}</p>
      </div>
    )
  }
  // 정원이 차서 더 못 들어가면 마감까지 남은 시간 대신 모임까지 남은 시간을 보여 준다
  const deadline = new Date(m.deadlineAt)
  const beforeDeadline = deadline > now && m.joinable
  return (
    <div>
      <p className="text-xs font-bold text-ink-3">{beforeDeadline ? '모집 마감까지' : '모임까지'}</p>
      <p className="tabular mt-1 text-2xl font-extrabold tracking-[-0.02em] text-ink">
        {formatRemaining(beforeDeadline ? deadline : m.meetAt, now)}
      </p>
    </div>
  )
}

/** 승차권에 찍히는 도장: 성사(확정) · 완료. 방금 참여해서 성사됐으면 쾅 찍힌다 */
function Stamp({ m, animate }: { m: MeetupDetail; animate: boolean }) {
  if (m.status !== 'CONFIRMED' && m.status !== 'DONE') return null
  const done = m.status === 'DONE'
  const word = done ? '완료' : m.minPeople === null ? '확정' : '성사'
  const [mm, dd] = formatDate(m.meetAt).match(/\d+/g) ?? []
  return (
    <span
      aria-hidden
      className={`absolute top-[-14px] right-4 flex size-20 rotate-[-12deg] flex-col items-center justify-center rounded-full border-[3px] ${
        done ? 'border-ink-3 text-ink-3' : 'border-ok text-ok'
      } ${animate && !done ? 'stamp-in' : ''}`}
      style={{ boxShadow: `inset 0 0 0 3px var(--surface), inset 0 0 0 4.5px ${done ? 'var(--ink-3)' : 'var(--ok)'}` }}
    >
      <span className="text-[21px] leading-none font-black tracking-[0.04em]">{word}</span>
      {mm && <span className="tabular mt-1 text-[9px] font-extrabold tracking-[0.08em]">{`${mm}.${dd}`}</span>}
    </span>
  )
}

/** 지금 상태에서 내가 알아야 할 것 한 가지 */
function StateNote({ m, now }: { m: MeetupDetail; now: Date }) {
  const left = (m.minPeople ?? 1) - m.participantCount
  let tone: 'accent' | 'ok' | 'muted' = 'accent'
  let title = ''
  let body = ''

  switch (m.status) {
    case 'RECRUITING':
      title = `${left}명만 더 모이면 성사돼요`
      body = m.canConfirm
        ? `더 기다리거나, 지금 ${m.participantCount}명으로 바로 확정할 수 있어요`
        : m.joined
          ? '성사되면 참여자끼리 실명·단톡방 공개'
          : '성사 전까지 내 이름은 가려져요'
      break
    case 'CONFIRMED':
      tone = 'ok'
      title = m.minPeople === null ? '인원 상관없이 무조건 진행해요' : '성사된 3교시예요'
      body = m.joined
        ? [
            m.participantCount < 2
              ? '함께할 원우를 기다리고 있어요'
              : m.openChatUrl
                ? '단톡방에서 만나요. 이제 서로 실명이 보여요'
                : m.hosting
                  ? '이제 서로 실명이 보여요. 아래에서 단톡방 링크를 넣어 주세요'
                  : '이제 서로 실명이 보여요. 단톡방 링크는 곧 올라와요',
            // 마감이 지나면 참여 취소 버튼이 사라진다 — 까닭을 알려 준다
            !m.hosting && !m.canLeave ? '마감이 지나 참여 취소는 안 돼요. 못 가게 되면 단톡방에 알려 주세요' : '',
          ]
            .filter(Boolean)
            .join('. ')
        : m.canJoin
          ? '지금 참여하면 바로 실명·단톡방이 열려요'
          : m.canWait
            ? m.waiting
              ? '자리가 나면 바로 알려 드릴게요. 먼저 참여하는 원우가 타요'
              : '정원이 다 찼어요. 자리 나면 알림을 받아 보세요'
            : '모집이 끝났어요.'
      break
    case 'FAILED':
      tone = 'muted'
      title = '이번엔 인원이 모이지 않았어요'
      body = m.canConfirm
        ? `모임 시간 전이라 지금 ${m.participantCount}명으로 다시 진행할 수 있어요`
        : m.hosting
          ? '나만 볼 수 있어요. 다음에 또 열어 봐요'
          : '참여한 원우만 볼 수 있어요. 연 사람이 다시 진행하면 알려 드려요'
      break
    case 'DONE':
      tone = 'muted'
      title = '모임 시간이 지났어요'
      // 안부 인사는 함께한 사람에게만, 모임 당일(모임 뒤 12시간 안)에만 (몇 명이 함께했는지는 아래 탑승 인원에 나온다)
      body =
        m.joined && now.getTime() - new Date(m.meetAt).getTime() < 12 * 3_600_000
          ? '즐거운 시간 보내셨나요? 귀가는 안전하게!'
          : ''
      break
    case 'CANCELED':
      tone = 'muted'
      title = '취소된 3교시예요'
      body = ''
      break
  }

  const toneClass = {
    accent: 'bg-accent-soft text-accent-ink',
    ok: 'bg-ok-soft text-ok-ink',
    muted: 'bg-surface-2 text-ink-2',
  }[tone]
  const iconClass = {
    accent: 'bg-accent text-on-accent',
    ok: 'bg-ok text-white',
    muted: 'bg-line-strong text-ink-2',
  }[tone]
  const Icon = m.status === 'CONFIRMED' ? PartyPopper : m.status === 'RECRUITING' ? UserPlus : Info

  return (
    <div className={`flex items-center gap-3.5 rounded-2xl px-4 py-4 sm:px-5 ${toneClass}`}>
      <span
        className={`flex size-10 shrink-0 items-center justify-center rounded-xl shadow-[inset_0_-2px_0_rgb(0_0_0/0.2)] ${iconClass}`}
      >
        <Icon className="size-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="text-base font-extrabold">{title}</p>
        {body && <p className="mt-0.5 text-[15px] leading-relaxed text-ink-2">{body}</p>}
      </div>
    </div>
  )
}

/** 캘린더에 넣기: 웹은 구글 캘린더 · 캘린더 파일(1시간 전 알림), 앱은 구글 캘린더 */
function CalendarCard({ m }: { m: MeetupDetail }) {
  const event: CalendarEvent = {
    id: m.id,
    title: m.title,
    place: m.place,
    meetAt: m.meetAt,
    url: `${window.location.origin}/meetups/${m.id}`,
  }
  return (
    <section className={`${cardClass} flex flex-wrap items-center gap-x-4 gap-y-3 px-5 py-4`}>
      <span className="flex min-w-[13rem] flex-1 items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink-2">
          <CalendarPlus className="size-5" aria-hidden />
        </span>
        <span className="min-w-0">
          <span className="block text-[15px] font-bold text-ink">캘린더에 넣어 두기</span>
          <span className="block text-[13px] text-ink-3">
            {isApp ? '구글 캘린더로 열려요' : '파일로 넣으면 1시간 전에 알려 줘요'}
          </span>
        </span>
      </span>
      {/* 좁은 화면에서는 버튼이 다음 줄로 내려가 한 줄을 나눠 쓴다 */}
      <span className="flex w-full gap-2 sm:w-auto">
        <a
          href={googleCalendarUrl(event)}
          target="_blank"
          rel="noreferrer"
          className={buttonClass('secondary', 'sm', 'flex-1 sm:flex-none')}
        >
          구글 캘린더
        </a>
        {!isApp && (
          <Button variant="secondary" size="sm" className="flex-1 sm:flex-none" onClick={() => downloadIcs(event)}>
            캘린더 파일
          </Button>
        )}
      </span>
    </section>
  )
}

/** 연 사람(·대학원장): 빈자리를 기다리는 사람 수와 정원 늘리기. 누구인지는 보이지 않고, 거절은 없다 */
function WaitlistCard({ m }: { m: MeetupDetail }) {
  const addSeats = useAddSeats()
  const toast = useToast()
  const count = m.waitCount ?? 0
  // 자리가 남아 있으면 보이지 않는다 (기다리던 원우에게 이미 알림이 갔다)
  if (count === 0 || m.maxPeople === null || !isLive(m.status) || m.participantCount < m.maxPeople) return null
  const add = m.canAddSeats ? seatsToAdd(m.maxPeople, count) : 0
  function expand() {
    if (m.maxPeople === null || add === 0) return
    const next = m.maxPeople + add
    if (!window.confirm(`정원을 ${m.maxPeople}명에서 ${next}명으로 늘릴까요?\n\n기다리는 ${count}명에게 바로 알림이 가요. 먼저 참여하는 원우가 타요.`))
      return
    addSeats.mutate(
      { id: m.id, maxPeople: next },
      {
        onSuccess: () => toast.show(`정원을 ${next}명으로 늘렸어요`, 'ok'),
        onError: (e) => toast.show(e.message, 'error'),
      },
    )
  }
  return (
    <section className={`${cardClass} flex flex-wrap items-center gap-x-4 gap-y-3 px-5 py-4`}>
      <span className="flex min-w-[13rem] flex-1 items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent-ink">
          <Bell className="size-5" aria-hidden />
        </span>
        <span className="min-w-0">
          <span className="block text-[15px] font-bold text-ink">
            빈자리 대기 <span className="tabular text-accent-ink">{count}명</span>
          </span>
          <span className="block text-[13px] text-ink-3">
            {add > 0 ? '정원을 늘리면 기다리던 원우에게 알림이 가요' : '정원은 30명까지예요'}
          </span>
        </span>
      </span>
      {add > 0 && (
        <Button size="sm" className="w-full shrink-0 sm:w-auto" loading={addSeats.isPending} onClick={expand}>
          <UserPlus className="size-4" aria-hidden />
          정원 +{add}
        </Button>
      )}
    </section>
  )
}

/** 단톡방에 공유: 앱·폰은 공유 창, PC 는 링크 복사. 링크를 누르면 앱이 있으면 앱으로 열린다 */
async function share(m: MeetupDetail, onCopied: () => void) {
  const url = `${window.location.origin}/meetups/${m.id}`
  // 단톡방에서 다음 날 봐도 틀리지 않게 '오늘·내일' 대신 날짜로
  const text = `[안암3교시] ${m.title} · ${formatDate(m.meetAt)} ${formatTime(m.meetAt)} · ${m.place} (${headcount(m)})`
  const result = await shareLink(m.title, text, url)
  if (result === 'copied') onCopied()
  else if (result === 'failed') window.prompt('아래 링크를 복사해 주세요', url)
}

export function MeetupDetailPage() {
  const id = Number(useParams().id)
  const { data: m, isPending, error, refetch } = useMeetup(id)
  const mine = useMyMeetups()
  const action = useMeetupAction()
  const removeMeetup = useDeleteMeetup()
  const hostTool = useHostTool()
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow()
  // 뒤로 버튼 이름: 온 곳(내 3교시 · 알림 …)을 알면 그 이름, 바로 열었으면 3교시 목록으로 간다
  const location = useLocation()
  const canGoBack = (window.history.state?.idx ?? 0) > 0
  const from = (location.state as { backLabel?: unknown } | null)?.backLabel
  const backLabel = canGoBack ? (typeof from === 'string' ? from : '뒤로') : '3교시 목록'
  const [arrived, setArrived] = useState<number | null>(null)
  /** 방금 "지금 인원으로 확정"을 눌렀으면 성사 도장이 쾅 찍힌다 */
  const [justConfirmed, setJustConfirmed] = useState(false)
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)

  if (isPending) return <Spinner />
  // 처음 불러오기가 실패했을 때만 (그 뒤 다시 불러오기가 실패해도 보던 내용·쓰던 신고는 그대로)
  // 보던 3교시가 그사이 지워졌으면(다시 불러오기가 404) 지워졌다고 바꿔 보여 준다
  const gone = error instanceof ApiError && error.status === 404
  if (!m || gone) {
    // 다른 학교·다른 기수 전용(403) · 지워진 3교시(404)는 다시 시도해도 같다
    const final = gone || (error instanceof ApiError && error.status === 403)
    return (
      <div>
        {/* 상세에는 하단 메뉴가 없어서, 막다른 화면이 되지 않게 뒤로 버튼을 둔다 */}
        <button
          type="button"
          onClick={() => (canGoBack ? navigate(-1) : navigate('/'))}
          className="-ml-2 inline-flex h-11 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
        >
          <ChevronLeft className="size-5" aria-hidden />
          {backLabel}
        </button>
        <ErrorState
          message={gone ? '지워진 3교시예요' : (error?.message ?? '불러오지 못했어요')}
          onRetry={() => refetch()}
          action={
            final ? (
              <Link to="/" className={buttonClass('secondary')}>
                열린 3교시 보기
              </Link>
            ) : undefined
          }
        />
      </div>
    )
  }

  const live = isLive(m.status)
  const hasActions =
    m.canJoin || m.canWait || Boolean(m.joined && m.revealed && m.openChatUrl) || live || m.canLeave || m.canConfirm
  /** 지난 3교시를 그대로 다시 열기 (연 사람·함께한 사람) */
  const canReopen = m.joined && !live && !m.canConfirm
  /** 무산된 3교시를 되살리는 확정인지 */
  const reviving = m.status === 'FAILED'
  /** 참여(·빈자리 대기)하려는데 내 다른 3교시와 시간이 겹친다 — 누르기 전에 까닭을 보여 준다 */
  const clash =
    m.canJoin || (m.canWait && !m.waiting) ? findOverlap(mine.data ?? [], new Date(m.meetAt), m.id) : null

  function remove() {
    const others = (m?.participantCount ?? 0) > 1 ? '\n• 참여자의 참여·정산·후기 기록도 함께 지워져요' : ''
    if (!window.confirm(`3교시를 지울까요?\n\n• 목록과 기록에서 아예 사라져요${others}\n• 되돌릴 수 없어요`)) return
    removeMeetup.mutate(id, {
      onSuccess: () => {
        toast.show('3교시를 지웠어요', 'ok')
        navigate('/', { replace: true })
      },
      onError: (e) => toast.show(e.message, 'error'),
    })
  }

  function run(kind: MeetupActionKind) {
    if (kind === 'cancel' && !window.confirm('3교시를 취소할까요? 참여자들에게는 "취소됨"으로 보여요.')) return
    if (
      kind === 'leave' &&
      !window.confirm(
        m?.status === 'CONFIRMED'
          ? '참여를 취소할까요?\n\n• 성사된 3교시에서 30일 동안 3번 나가면, 한동안 이미 성사된 3교시에는 참여할 수 없어요'
          : '참여를 취소할까요?',
      )
    )
      return
    // 마감 뒤(중간 합류) 참여는 되돌릴 수 없어서 한 번 더 묻는다
    const lateWarn = kind === 'join' && m ? lateJoinConfirm(m) : null
    if (lateWarn && !window.confirm(lateWarn)) return
    const count = m?.participantCount ?? 0
    if (
      kind === 'confirm' &&
      !window.confirm(
        reviving
          ? `무산된 3교시를 지금 ${count}명으로 다시 진행할까요?\n\n• 바로 성사되고 참여자에게 알림이 가요\n• 참여자끼리 실명과 단톡방이 열려요`
          : `지금 ${count}명으로 확정할까요?\n\n• 최소 인원이 ${count}명으로 바뀌고 바로 성사돼요\n• 참여자끼리 실명과 단톡방이 열려요\n• 정원까지는 계속 더 참여할 수 있어요`,
      )
    )
      return
    const wasConfirmed = m?.status === 'CONFIRMED'
    action.mutate(
      { id, action: kind },
      {
        onSuccess: (d) => {
          if (kind === 'confirm') {
            setJustConfirmed(true)
            toast.show(reviving ? '다시 진행해요! 이제 서로 이름이 보여요' : '성사! 이제 서로 이름이 보여요', 'ok')
          } else if (kind === 'join') {
            setArrived(d.participantCount - 1)
            toast.show(
              !wasConfirmed && d.status === 'CONFIRMED' ? '성사! 이제 서로 이름이 보여요' : '참여했어요',
              'ok',
            )
          } else if (kind === 'leave') {
            toast.show('참여를 취소했어요')
          } else if (kind === 'wait') {
            toast.show('자리가 나면 알려 드릴게요', 'ok')
          } else if (kind === 'unwait') {
            toast.show('빈자리 알림을 껐어요')
          } else {
            toast.show('3교시를 취소했어요')
          }
        },
        onError: (e) => toast.show(e.message, 'error'),
      },
    )
  }

  const actions = (
    <div className="flex flex-col gap-2.5">
      {clash && <OverlapNote meetup={clash} now={now} link />}
      {m.canJoin && (
        <Button size="lg" loading={action.isPending} disabled={Boolean(clash)} onClick={() => run('join')} className="w-full">
          나도 콜
        </Button>
      )}
      {m.canWait &&
        (m.waiting ? (
          <Button size="lg" variant="secondary" loading={action.isPending} onClick={() => run('unwait')} className="w-full">
            <BellOff className="size-5" aria-hidden />
            빈자리 알림 대기 중 · 끄기
          </Button>
        ) : (
          <Button size="lg" loading={action.isPending} disabled={Boolean(clash)} onClick={() => run('wait')} className="w-full">
            <Bell className="size-5" aria-hidden />
            자리 나면 알림 받기
          </Button>
        ))}
      {m.canConfirm && (
        <Button size="lg" loading={action.isPending} onClick={() => run('confirm')} className="w-full">
          <CheckCheck className="size-5" aria-hidden />
          {reviving ? `지금 ${m.participantCount}명으로 다시 진행` : `지금 ${m.participantCount}명으로 확정`}
        </Button>
      )}
      {m.joined && m.revealed && m.openChatUrl && (
        <a href={m.openChatUrl} target="_blank" rel="noreferrer" className={buttonClass('ok', 'lg', 'w-full')}>
          <MessageCircle className="size-5" aria-hidden />
          단톡방 들어가기
        </a>
      )}
      <div className="flex gap-2.5">
        {live && (
          <Button
            variant="secondary"
            className="flex-1"
            onClick={() => share(m, () => toast.show('링크를 복사했어요. 단톡방에 붙여 넣어 보세요', 'ok'))}
          >
            <Share2 className="size-4" aria-hidden />
            공유
          </Button>
        )}
        {/* 연 사람: 맨 아래까지 내리지 않아도 고칠 수 있게 */}
        {m.hosting && m.canEdit && (
          <Link to={`/meetups/${m.id}/edit`} className={`${buttonClass('secondary', 'md', 'flex-1')} lg:hidden`}>
            <Pencil className="size-4" aria-hidden />
            수정
          </Link>
        )}
        {m.canLeave && (
          <Button variant="secondary" className="flex-1" loading={action.isPending} onClick={() => run('leave')}>
            참여 취소
          </Button>
        )}
      </div>
    </div>
  )

  const day = formatDayLabel(m.meetAt, now)
  const date = formatDate(m.meetAt)
  const time = formatTime(m.meetAt)

  return (
    <div className={hasActions ? `${clash ? 'pb-56' : 'pb-40'} lg:pb-0` : 'lg:pb-0'}>
      {/* 모바일: 머리의 안내판이 이어지고, 승차권이 그 위로 겹쳐 올라온다 */}
      <div className="-mx-4 -mt-6 bg-board px-2 pt-2 pb-24 sm:-mx-6 sm:px-4 lg:mx-0 lg:mt-0 lg:bg-transparent lg:p-0 lg:pb-4">
        <button
          type="button"
          onClick={() => (canGoBack ? navigate(-1) : navigate('/'))}
          className="inline-flex h-11 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-on-board-2 hover:text-on-board lg:-ml-2 lg:text-ink-2 lg:hover:text-ink"
        >
          <ChevronLeft className="size-5" aria-hidden />
          {backLabel}
        </button>
      </div>

      {/* 승차권 */}
      <header className={`${cardClass} relative -mt-20 overflow-hidden lg:mt-0`}>
        {/* 고른 종류의 노선 색을 나란히 — 여러 노선이 지나는 환승역처럼 */}
        <div className="flex h-1.5" aria-hidden>
          {sortCategories(m.categories).map((c) => (
            <span key={c} className="flex-1" style={{ backgroundColor: CATEGORY[c].color }} />
          ))}
        </div>
        <div className="px-5 pt-4 pb-4 sm:px-7 sm:pt-6">
          <div className="flex items-center justify-between gap-3">
            <span className="min-w-0 text-ink-2">
              <CategoryBadges categories={m.categories} size="sm" withLabel />
            </span>
            <StatusPill meetup={m} />
          </div>
          <h1 className="mt-3.5 text-[23px] leading-snug font-extrabold tracking-[-0.03em] text-ink sm:text-[30px]">{m.title}</h1>
          <dl className="mt-3 flex flex-col gap-2 text-[15px] text-ink-2">
            <div className="flex items-start gap-2.5">
              <dt className="sr-only">장소</dt>
              <MapPin className="mt-1.5 size-4 shrink-0 text-ink-3" aria-hidden />
              <dd className="flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-1.5">
                <span className="min-w-0 font-semibold break-words text-ink">{m.place}</span>
                <span className="flex gap-1.5">
                  {MAP_PROVIDERS.map((p) => (
                    <a
                      key={p.label}
                      href={p.url(m.place)}
                      target="_blank"
                      rel="noreferrer"
                      className="hit inline-flex h-7 items-center gap-0.5 rounded-lg border border-line-strong bg-surface pr-1.5 pl-2 text-[12px] font-semibold text-ink-2 transition-colors hover:bg-surface-2 hover:text-ink"
                    >
                      {p.label}
                      <ArrowUpRight className="size-3.5 text-ink-3" aria-hidden />
                      <span className="sr-only">에서 장소 찾기 (새 창)</span>
                    </a>
                  ))}
                </span>
              </dd>
            </div>
            {m.purposes.length > 0 && (
              <div className="flex items-center gap-2.5">
                <dt className="sr-only">모임 목적</dt>
                <Hash className="size-4 shrink-0 text-ink-3" aria-hidden />
                <dd>
                  <PurposeTags purposes={m.purposes} />
                </dd>
              </div>
            )}
            {m.audience !== 'SCHOOL' && (
              <div className="flex items-center gap-2.5">
                <dt className="sr-only">모집 대상</dt>
                {m.audience === 'ALL' ? (
                  <Globe2 className="size-4 shrink-0 text-ink-3" aria-hidden />
                ) : (
                  <Lock className="size-4 shrink-0 text-ink-3" aria-hidden />
                )}
                <dd className="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <AudienceBadge meetup={m} />
                  <span className="text-sm text-ink-3">
                    {m.audience === 'ALL'
                      ? 'IT연합회 학교 누구나 참여해요'
                      : `${m.school.shortName} 다른 기수에게는 보이지 않아요`}
                  </span>
                </dd>
              </div>
            )}
            {(m.fee !== null || showsLateJoin(m)) && (
              <div className="flex items-center gap-2.5">
                <dt className="sr-only">참여 방식</dt>
                <Wallet className="size-4 shrink-0 text-ink-3" aria-hidden />
                <dd>
                  <MeetupOptionTags meetup={m} />
                </dd>
              </div>
            )}
            <div className="flex items-start gap-2.5">
              <Users className="mt-1 size-4 shrink-0 text-ink-3" aria-hidden />
              {/* '님이 열었어요'만 다음 줄에 홀로 남지 않게: 이름표를 앞에 두고, 이름 묶음 안에서 줄을 바꾼다 */}
              <dt className="shrink-0 pt-px text-ink-3">연 사람</dt>
              <dd className="min-w-0 pt-px">
                <PersonName person={m.host} showMe={false} showSchool={m.audience === 'ALL'} wrap />
              </dd>
            </div>
          </dl>

          <p className="mt-4 mb-1.5 text-xs font-bold text-ink-3">
            출발 · {day}
            {day !== date && ` ${date}`}
          </p>
          <DepartureBoard time={time} label={`${day} ${time} 출발`} />
        </div>

        {/* 절취선 */}
        <div className="relative h-5" aria-hidden>
          <span className="absolute top-0 -left-2.5 size-5 rounded-full bg-bg shadow-[inset_-1px_0_0_var(--line)]" />
          <span className="absolute inset-x-5 top-1/2 border-t-2 border-dashed border-line" />
          <span className="absolute top-0 -right-2.5 size-5 rounded-full bg-bg shadow-[inset_1px_0_0_var(--line)]" />
        </div>

        <div className="relative grid grid-cols-2 gap-3 px-5 pt-1 pb-5 sm:px-7">
          <TicketCountdown m={m} now={now} />
          <div>
            <p className="text-xs font-bold text-ink-3">탑승</p>
            <p className="tabular mt-1 text-2xl font-extrabold text-ink">
              {m.participantCount}
              <span className="font-medium text-ink-3">{m.maxPeople === null ? '명' : `/${m.maxPeople}`}</span>
            </p>
          </div>
          <Stamp m={m} animate={arrived !== null || justConfirmed} />
        </div>
      </header>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px] lg:items-start">
        <div className="flex flex-col gap-6">
          <StateNote m={m} now={now} />

          <WaitlistCard m={m} />

          {m.joined && live && <CalendarCard m={m} />}

          <SettlementCard m={m} />

          <FeedbackCard m={m} now={now} />

          {m.description && (
            <section className={`${cardClass} px-5 py-5`}>
              <h2 className="text-sm font-bold text-ink-3">한마디</h2>
              <p className="mt-2 text-base leading-relaxed whitespace-pre-wrap text-ink">{m.description}</p>
            </section>
          )}

          <section className={`${cardClass} px-4 py-5 sm:px-5`}>
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 px-1">
              <h2 className="text-[17px] font-extrabold whitespace-nowrap text-ink">
                탑승 인원 <span className="tabular text-accent-ink">{m.participantCount}</span>
                <span className="tabular font-semibold text-ink-3">{m.maxPeople === null ? '명' : `/${m.maxPeople}`}</span>
              </h2>
              <span className="text-sm text-ink-3">{seatsCaption(m)}</span>
            </div>
            {!m.revealed && (
              <p className="mx-1 mb-3 flex items-center gap-2 rounded-xl bg-surface-2 px-3 py-2.5 text-sm text-ink-2">
                <EyeOff className="size-4 shrink-0" aria-hidden />
                {hiddenNamesNote(m.status)}
              </p>
            )}
            <ParticipantRoute
              count={m.participantCount}
              min={m.minPeople}
              max={m.maxPeople}
              status={m.status}
              participants={m.participants}
              highlightIndex={arrived}
              showSchool={m.audience === 'ALL'}
              onPersonMenu={(p) =>
                p.ref !== null &&
                setReporting({
                  type: 'MEMBER',
                  id: p.ref,
                  name: p.name,
                  label: `${p.name} · ${affiliation(p.cohortLabel, p.departmentLabel)} · ${p.school.shortName}`,
                  // 후기를 받는 동안의 사람 신고: '아쉬웠어요'라고 답했으면 서버가 "후기에서" 표시를 붙인다
                  fromFeedback: m.feedback !== null,
                })
              }
            />
          </section>

          {m.hosting && m.status !== 'CANCELED' && !m.openChatUrl && m.canEdit && (
            <div className={`${cardClass} px-5 py-4 text-[15px] text-ink-2`}>
              단톡방 링크는 성사되면 참여자에게만 보여요.{' '}
              <Link to={`/meetups/${m.id}/edit`} className="font-semibold text-accent-ink underline underline-offset-4">
                링크 넣기
              </Link>
            </div>
          )}
        </div>

        <aside className="flex flex-col gap-4 lg:sticky lg:top-10">
          <div className="hidden lg:block">{actions}</div>
          {!m.hosting && (
            <button
              type="button"
              onClick={() => setReporting({ type: 'MEETUP', id: m.id, label: `3교시 ‘${m.title}’` })}
              className="inline-flex h-10 items-center justify-center gap-1.5 self-center rounded-lg px-3 text-sm font-semibold text-ink-3 hover:bg-surface-2 hover:text-ink lg:self-start"
            >
              <Flag className="size-4" aria-hidden />
              이 3교시 신고하기
            </button>
          )}
          {canReopen && (
            <Link to={`/meetups/new?from=${m.id}`} className={buttonClass('secondary', 'md')}>
              <Copy className="size-4" aria-hidden />이 3교시 다시 열기
            </Link>
          )}
          {/* 수정: 연 사람·대학원장 / 취소: 연 사람·그 학교 교수·대학원장 */}
          {(m.canEdit || m.canCancel) && (
            <div className="flex gap-2.5">
              {m.canEdit && (
                <Link to={`/meetups/${m.id}/edit`} className={buttonClass('ghost', 'md', 'flex-1')}>
                  <Pencil className="size-4" aria-hidden />
                  수정
                </Link>
              )}
              {m.canCancel && (
                <Button variant="danger" className="flex-1" loading={action.isPending} onClick={() => run('cancel')}>
                  3교시 취소
                </Button>
              )}
            </div>
          )}
          {/* 지우기: 연 사람·그 학교 교수·대학원장 — 취소와 달리 기록에서 사라진다 */}
          {m.canDelete && (
            <button
              type="button"
              onClick={remove}
              disabled={removeMeetup.isPending}
              className="inline-flex h-10 items-center justify-center gap-1.5 self-center rounded-lg px-3 text-sm font-semibold text-ink-3 hover:bg-danger-soft hover:text-danger-ink disabled:opacity-50 lg:self-start"
            >
              <Trash2 className="size-4" aria-hidden />
              3교시 지우기
            </button>
          )}
        </aside>
      </div>

      {reporting && (
        <ReportDialog
          key={`${reporting.type}-${reporting.id}`}
          target={reporting}
          onClose={() => setReporting(null)}
          hostTools={
            reporting.type === 'MEMBER' && (m.canCancel || (m.hosting && m.canEdit))
              ? (close) => (
                  <div className="flex gap-2">
                    {m.canCancel && (
                      <Button
                        variant="secondary"
                        className="flex-1"
                        loading={hostTool.isPending}
                        onClick={() => {
                          if (
                            !window.confirm(
                              `${reporting.name ?? '이 원우'} 님을 내보낼까요?\n\n• 이 3교시에 다시 참여할 수 없어요\n• 내보냈다고 알림이 가요`,
                            )
                          )
                            return
                          hostTool.mutate(
                            { id: m.id, ref: reporting.id, kind: 'kick' },
                            {
                              onSuccess: () => {
                                toast.show('내보냈어요', 'ok')
                                close()
                              },
                              onError: (e) => toast.show(e.message, 'error'),
                            },
                          )
                        }}
                      >
                        <UserMinus className="size-4" aria-hidden />
                        내보내기
                      </Button>
                    )}
                    {m.hosting && m.canEdit && (
                      <Button
                        variant="secondary"
                        className="flex-1"
                        loading={hostTool.isPending}
                        onClick={() => {
                          if (
                            !window.confirm(
                              `${reporting.name ?? '이 원우'} 님에게 연 사람을 넘길까요?\n\n• 넘겨받은 원우가 수정·취소·확정을 맡아요\n• 나는 참여자로 남아요`,
                            )
                          )
                            return
                          hostTool.mutate(
                            { id: m.id, ref: reporting.id, kind: 'transfer' },
                            {
                              onSuccess: () => {
                                toast.show('연 사람을 넘겼어요', 'ok')
                                close()
                              },
                              onError: (e) => toast.show(e.message, 'error'),
                            },
                          )
                        }}
                      >
                        <Crown className="size-4" aria-hidden />
                        연 사람 넘기기
                      </Button>
                    )}
                  </div>
                )
              : undefined
          }
        />
      )}

      {/* 모바일: 하단 고정 행동 바 (상세에서는 하단 메뉴 대신 이것만) */}
      {hasActions && (
        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface px-4 pt-3 pb-[calc(16px+env(safe-area-inset-bottom))] shadow-[0_-8px_20px_-16px_rgb(0_0_0/0.4)] lg:hidden">
          {actions}
        </div>
      )}
    </div>
  )
}
