import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  ChevronLeft,
  Clock,
  EyeOff,
  Flag,
  Globe2,
  Hash,
  Lock,
  MapPin,
  MessageCircle,
  PartyPopper,
  Pencil,
  Share2,
  Trash2,
  Users,
  Wallet,
} from 'lucide-react'
import { ApiError } from '../api/client'
import { useDeleteMeetup, useMeetup, useMeetupAction } from '../api/queries'
import type { MeetupDetail } from '../api/types'
import { CategoryBadges } from '../components/CategoryBadge'
import { MeetupOptionTags } from '../components/MeetupOptionTags'
import { AudienceBadge } from '../components/CohortBadge'
import { ParticipantRoute } from '../components/LineGauge'
import { PersonName } from '../components/PersonName'
import { PurposeTags } from '../components/PurposeTags'
import { ReportDialog, type ReportTargetInfo } from '../components/ReportDialog'
import { StatusPill } from '../components/StatusPill'
import { Button, ErrorState, Spinner } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { CATEGORY, headcount, isLive, seatsCaption, sortCategories } from '../lib/meetup'
import { affiliation } from '../lib/person'
import { formatDateTime, formatRemaining } from '../lib/time'

function Countdown({ m, now }: { m: MeetupDetail; now: Date }) {
  if (!isLive(m.status)) return null
  const deadline = new Date(m.deadlineAt)
  const text =
    deadline > now ? `모집 마감까지 ${formatRemaining(deadline, now)}` : `모임까지 ${formatRemaining(m.meetAt, now)}`
  return (
    <span className="tabular inline-flex items-center gap-1.5 rounded-md bg-exit px-2.5 py-1 text-sm font-bold text-on-exit">
      <Clock className="size-4" aria-hidden />
      {text}
    </span>
  )
}

/** 지금 상태에서 내가 알아야 할 것 한 가지 */
function StateNote({ m }: { m: MeetupDetail }) {
  const left = (m.minPeople ?? 1) - m.participantCount
  let tone: 'accent' | 'ok' | 'muted' = 'accent'
  let title = ''
  let body = ''

  switch (m.status) {
    case 'RECRUITING':
      title = `${left}명만 더 모이면 성사돼요`
      body = m.joined
        ? '성사되면 참여자끼리 실명·단톡방 공개'
        : '성사 전까지 내 이름은 가려져요'
      break
    case 'CONFIRMED':
      tone = 'ok'
      title = m.minPeople === null ? '인원 상관없이 무조건 진행해요' : '성사된 3교시예요'
      body = m.joined
        ? m.openChatUrl
          ? '단톡방에서 만나요. 이제 서로 실명이 보여요'
          : '이제 서로 실명이 보여요. 단톡방 링크는 곧 올라와요'
        : m.canJoin
          ? '지금 참여하면 바로 실명·단톡방이 열려요'
          : '모집이 끝났어요.'
      break
    case 'FAILED':
      tone = 'muted'
      title = '이번엔 인원이 모이지 않았어요'
      body = '나만 볼 수 있어요. 다음에 또 열어 봐요'
      break
    case 'DONE':
      tone = 'muted'
      title = '모임 시간이 지났어요'
      body = '즐거운 시간 보내셨나요? 귀가는 안전하게!'
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

  return (
    <div className={`rounded-2xl px-5 py-4 ${toneClass}`}>
      <p className="flex items-center gap-2 text-base font-bold">
        {m.status === 'CONFIRMED' && <PartyPopper className="size-5" aria-hidden />}
        {title}
      </p>
      {body && <p className="mt-1 text-[15px] leading-relaxed opacity-90">{body}</p>}
    </div>
  )
}

async function share(m: MeetupDetail, onCopied: () => void) {
  const url = `${window.location.origin}/meetups/${m.id}`
  const text = `[안암3교시] ${m.title} · ${formatDateTime(m.meetAt)} · ${m.place} (${headcount(m)})`
  if (navigator.share) {
    try {
      await navigator.share({ title: m.title, text, url })
      return
    } catch {
      // 공유 창을 닫은 경우 등 → 복사로 대체하지 않고 끝
      return
    }
  }
  try {
    await navigator.clipboard.writeText(`${text}\n${url}`)
    onCopied()
  } catch {
    window.prompt('아래 링크를 복사해 주세요', url)
  }
}

export function MeetupDetailPage() {
  const id = Number(useParams().id)
  const { data: m, isPending, isError, error, refetch } = useMeetup(id)
  const action = useMeetupAction()
  const removeMeetup = useDeleteMeetup()
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow()
  const [arrived, setArrived] = useState<number | null>(null)
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)

  if (isPending) return <Spinner />
  if (isError) {
    // 다른 학교·다른 기수 전용 3교시(403)는 다시 시도해도 같다
    const forbidden = error instanceof ApiError && error.status === 403
    return (
      <div>
        <ErrorState message={error.message} onRetry={forbidden ? undefined : () => refetch()} />
        {forbidden && (
          <div className="flex justify-center">
            <Link to="/" className={buttonClass('secondary')}>
              열린 3교시 보기
            </Link>
          </div>
        )}
      </div>
    )
  }

  const live = isLive(m.status)
  const hasActions = m.canJoin || Boolean(m.joined && m.revealed && m.openChatUrl) || live || m.canLeave

  function remove() {
    if (!window.confirm('3교시를 지울까요?\n\n• 목록과 기록에서 아예 사라져요\n• 참여자 기록도 함께 지워져요\n• 되돌릴 수 없어요')) return
    removeMeetup.mutate(id, {
      onSuccess: () => {
        toast.show('3교시를 지웠어요', 'ok')
        navigate('/', { replace: true })
      },
      onError: (e) => toast.show(e.message, 'error'),
    })
  }

  function run(kind: 'join' | 'leave' | 'cancel') {
    if (kind === 'cancel' && !window.confirm('3교시를 취소할까요? 참여자들에게는 "취소됨"으로 보여요.')) return
    if (kind === 'leave' && !window.confirm('참여를 취소할까요?')) return
    const wasConfirmed = m?.status === 'CONFIRMED'
    action.mutate(
      { id, action: kind },
      {
        onSuccess: (d) => {
          if (kind === 'join') {
            setArrived(d.participantCount - 1)
            toast.show(
              !wasConfirmed && d.status === 'CONFIRMED' ? '성사! 이제 서로 이름이 보여요' : '참여했어요',
              'ok',
            )
          } else if (kind === 'leave') {
            toast.show('참여를 취소했어요')
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
      {m.canJoin && (
        <Button size="lg" loading={action.isPending} onClick={() => run('join')} className="w-full">
          나도 콜
        </Button>
      )}
      {m.joined && m.revealed && m.openChatUrl && (
        <a href={m.openChatUrl} target="_blank" rel="noreferrer" className={buttonClass('ok', 'lg', 'w-full')}>
          <MessageCircle className="size-5" aria-hidden />
          오픈채팅 들어가기
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
        {m.canLeave && (
          <Button variant="secondary" className="flex-1" loading={action.isPending} onClick={() => run('leave')}>
            참여 취소
          </Button>
        )}
      </div>
    </div>
  )

  return (
    <div className="pb-24 lg:pb-0">
      <button
        type="button"
        onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}
        className="mb-4 -ml-2 inline-flex h-10 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
      >
        <ChevronLeft className="size-5" aria-hidden />
        3교시 목록
      </button>

      {/* 안내판 머리 */}
      <header className="overflow-hidden rounded-3xl bg-board text-on-board">
        <div className="px-5 pt-6 pb-6 sm:px-8 sm:pt-8">
          <div className="flex items-center justify-between gap-3">
            <span className="min-w-0 text-on-board-2">
              <CategoryBadges categories={m.categories} size="sm" withLabel />
            </span>
            <StatusPill meetup={m} />
          </div>
          <h1 className="mt-4 text-[26px] leading-tight font-extrabold tracking-[-0.03em] sm:text-[32px]">{m.title}</h1>
          <dl className="mt-5 flex flex-col gap-2.5 text-[15px]">
            <div className="flex items-center gap-2.5">
              <dt className="sr-only">모임 시간</dt>
              <Clock className="size-4 shrink-0 text-on-board-2" aria-hidden />
              <dd className="tabular font-semibold">{formatDateTime(m.meetAt, now)}</dd>
            </div>
            <div className="flex items-center gap-2.5">
              <dt className="sr-only">장소</dt>
              <MapPin className="size-4 shrink-0 text-on-board-2" aria-hidden />
              <dd className="font-semibold">{m.place}</dd>
            </div>
            {m.purposes.length > 0 && (
              <div className="flex items-center gap-2.5">
                <dt className="sr-only">모임 목적</dt>
                <Hash className="size-4 shrink-0 text-on-board-2" aria-hidden />
                <dd>
                  <PurposeTags purposes={m.purposes} tone="board" />
                </dd>
              </div>
            )}
            {m.audience !== 'SCHOOL' && (
              <div className="flex items-center gap-2.5">
                <dt className="sr-only">모집 대상</dt>
                {m.audience === 'ALL' ? (
                  <Globe2 className="size-4 shrink-0 text-on-board-2" aria-hidden />
                ) : (
                  <Lock className="size-4 shrink-0 text-on-board-2" aria-hidden />
                )}
                <dd className="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <AudienceBadge meetup={m} tone="board" />
                  <span className="text-sm text-on-board-2">
                    {m.audience === 'ALL'
                      ? 'IT연합회 다섯 학교 누구나 참여해요'
                      : `${m.school.shortName} 다른 기수에게는 보이지 않아요`}
                  </span>
                </dd>
              </div>
            )}
            {(m.fee !== null || m.lateJoin) && (
              <div className="flex items-center gap-2.5">
                <dt className="sr-only">참여 방식</dt>
                <Wallet className="size-4 shrink-0 text-on-board-2" aria-hidden />
                <dd>
                  <MeetupOptionTags meetup={m} tone="board" />
                </dd>
              </div>
            )}
            <div className="flex items-center gap-2.5">
              <dt className="sr-only">연 사람</dt>
              <Users className="size-4 shrink-0 text-on-board-2" aria-hidden />
              <dd className="flex min-w-0 flex-wrap items-center gap-x-1 text-on-board-2">
                <PersonName person={m.host} showMe={false} showSchool={m.audience === 'ALL'} /> 님이 열었어요
              </dd>
            </div>
          </dl>
          <div className="mt-5">
            <Countdown m={m} now={now} />
          </div>
        </div>
        {/* 고른 종류의 노선 색을 나란히 — 여러 노선이 지나는 환승역처럼 */}
        <div className="flex h-2" aria-hidden>
          {sortCategories(m.categories).map((c) => (
            <span key={c} className="flex-1" style={{ backgroundColor: CATEGORY[c].color }} />
          ))}
        </div>
      </header>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px] lg:items-start">
        <div className="flex flex-col gap-6">
          <StateNote m={m} />

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
                {m.status === 'FAILED' || m.status === 'CANCELED'
                  ? '성사되지 않은 3교시라 이름은 계속 가려져요.'
                  : '성사되면 함께하는 사람끼리만 실명이 보여요.'}
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
                  label: `${p.name} · ${affiliation(p.cohortLabel, p.departmentLabel)} · ${p.school.shortName}`,
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

      {reporting && <ReportDialog key={`${reporting.type}-${reporting.id}`} target={reporting} onClose={() => setReporting(null)} />}

      {/* 모바일: 하단 고정 행동 바 (탭 바 바로 위) */}
      {hasActions && (
        <div className="fixed inset-x-0 bottom-[calc(64px+env(safe-area-inset-bottom))] z-30 border-t border-line bg-surface px-4 py-3 shadow-[0_-8px_24px_-12px_rgb(0_0_0/0.18)] lg:hidden">
          {actions}
        </div>
      )}
    </div>
  )
}
