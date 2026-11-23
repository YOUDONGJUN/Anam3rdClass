import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ChevronLeft, Clock, EyeOff, MapPin, MessageCircle, PartyPopper, Pencil, Share2, Users } from 'lucide-react'
import { useMeetup, useMeetupAction } from '../api/queries'
import type { MeetupDetail } from '../api/types'
import { CategoryBadge } from '../components/CategoryBadge'
import { ParticipantRoute } from '../components/LineGauge'
import { PersonName } from '../components/PersonName'
import { StatusPill } from '../components/StatusPill'
import { Button, ErrorState, Spinner } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { CATEGORY, isLive, seatsCaption } from '../lib/meetup'
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
  const left = m.minPeople - m.participantCount
  let tone: 'accent' | 'ok' | 'muted' = 'accent'
  let title = ''
  let body = ''

  switch (m.status) {
    case 'RECRUITING':
      title = `${left}명만 더 모이면 성사돼요`
      body = m.joined
        ? '성사되면 함께하는 사람끼리 실명과 오픈채팅 링크가 공개돼요. 안 모이면 조용히 사라져요.'
        : '참여해도 성사 전까지는 내 이름이 가려져요. 부담 없이 눌러 보세요.'
      break
    case 'CONFIRMED':
      tone = 'ok'
      title = '성사된 번개예요'
      body = m.joined
        ? m.openChatUrl
          ? '오픈채팅방에서 만나요. 이제 서로 실명이 보여요.'
          : '이제 서로 실명이 보여요. 개설자가 오픈채팅 링크를 곧 넣을 거예요.'
        : m.canJoin
          ? '지금 참여하면 바로 함께하는 사람들의 이름과 오픈채팅이 열려요.'
          : '모집이 끝났어요.'
      break
    case 'FAILED':
      tone = 'muted'
      title = '이번엔 인원이 모이지 않았어요'
      body = '다른 사람들에겐 보이지 않아요. 다음에 또 열어 봐요.'
      break
    case 'DONE':
      tone = 'muted'
      title = '모임 시간이 지났어요'
      body = '즐거운 시간 보내셨나요? 귀가는 안전하게!'
      break
    case 'CANCELED':
      tone = 'muted'
      title = '취소된 번개예요'
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
  const text = `[안암3교시] ${m.title} · ${formatDateTime(m.meetAt)} · ${m.place} (${m.participantCount}/${m.maxPeople}명)`
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
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow()
  const [arrived, setArrived] = useState<number | null>(null)

  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />

  const category = CATEGORY[m.category]
  const live = isLive(m.status)
  const hasActions = m.canJoin || Boolean(m.joined && m.revealed && m.openChatUrl) || live || m.canLeave

  function run(kind: 'join' | 'leave' | 'cancel') {
    if (kind === 'cancel' && !window.confirm('번개를 취소할까요? 참여자들에게는 "취소됨"으로 보여요.')) return
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
            toast.show('번개를 취소했어요')
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
        번개 목록
      </button>

      {/* 안내판 머리 */}
      <header className="overflow-hidden rounded-3xl bg-board text-on-board">
        <div className="px-5 pt-6 pb-6 sm:px-8 sm:pt-8">
          <div className="flex items-center justify-between gap-3">
            <span className="inline-flex items-center gap-2 text-sm font-semibold text-on-board-2">
              <CategoryBadge category={m.category} size="sm" />
              {category.label}
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
            <div className="flex items-center gap-2.5">
              <dt className="sr-only">연 사람</dt>
              <Users className="size-4 shrink-0 text-on-board-2" aria-hidden />
              <dd className="text-on-board-2">
                <PersonName person={m.host} showMe={false} /> 님이 열었어요
              </dd>
            </div>
          </dl>
          <div className="mt-5">
            <Countdown m={m} now={now} />
          </div>
        </div>
        <div className="h-2" style={{ backgroundColor: category.color }} aria-hidden />
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
            <div className="mb-3 flex items-baseline justify-between gap-3 px-1">
              <h2 className="text-[17px] font-extrabold text-ink">
                탑승 인원 <span className="tabular text-accent-ink">{m.participantCount}</span>
                <span className="tabular font-semibold text-ink-3">/{m.maxPeople}</span>
              </h2>
              <span className="text-sm text-ink-3">{seatsCaption(m)}</span>
            </div>
            {!m.revealed && (
              <p className="mx-1 mb-3 flex items-center gap-2 rounded-xl bg-surface-2 px-3 py-2.5 text-sm text-ink-2">
                <EyeOff className="size-4 shrink-0" aria-hidden />
                {m.status === 'FAILED' || m.status === 'CANCELED'
                  ? '성사되지 않은 번개라 이름은 계속 가려져요.'
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
            />
          </section>

          {m.hosting && m.status !== 'CANCELED' && !m.openChatUrl && m.canEdit && (
            <div className={`${cardClass} px-5 py-4 text-[15px] text-ink-2`}>
              오픈채팅 링크를 넣어 두면 성사됐을 때 참여자에게만 보여요.{' '}
              <Link to={`/meetups/${m.id}/edit`} className="font-semibold text-accent-ink underline underline-offset-4">
                링크 넣기
              </Link>
            </div>
          )}
        </div>

        <aside className="flex flex-col gap-4 lg:sticky lg:top-10">
          <div className="hidden lg:block">{actions}</div>
          {m.canEdit && (
            <div className="flex gap-2.5">
              <Link to={`/meetups/${m.id}/edit`} className={buttonClass('ghost', 'md', 'flex-1')}>
                <Pencil className="size-4" aria-hidden />
                수정
              </Link>
              {m.canCancel && (
                <Button variant="danger" className="flex-1" loading={action.isPending} onClick={() => run('cancel')}>
                  번개 취소
                </Button>
              )}
            </div>
          )}
        </aside>
      </div>

      {/* 모바일: 하단 고정 행동 바 (탭 바 바로 위) */}
      {hasActions && (
        <div className="fixed inset-x-0 bottom-[calc(64px+env(safe-area-inset-bottom))] z-30 border-t border-line bg-surface px-4 py-3 shadow-[0_-8px_24px_-12px_rgb(0_0_0/0.18)] lg:hidden">
          {actions}
        </div>
      )}
    </div>
  )
}
