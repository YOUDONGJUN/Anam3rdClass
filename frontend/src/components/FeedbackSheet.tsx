import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { Flag, X } from 'lucide-react'
import { useMeetup, usePendingFeedback, useSubmitFeedback } from '../api/queries'
import type { FeedbackInput, FeedbackPending } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { affiliation } from '../lib/person'
import { formatDayLabel, formatTime, kstParts } from '../lib/time'
import { RATING_CHOICES } from './ratingChoices'
import { ReportDialog, type ReportTargetInfo } from './ReportDialog'
import { buttonClass } from './styles'
import { useToast } from './toastContext'

/** 후기 창은 아래 메뉴가 있는 첫 화면들에서만 띄운다 (3교시를 열거나 보는 중에는 끼어들지 않는다) */
const MAIN_PATHS = ['/', '/my-meetups', '/notices', '/me']

/** 한 번 띄웠으면 이번에 앱을 쓰는 동안은 다시 띄우지 않는다 (밀린 건 다음에 열 때 하나씩) */
let finishedFor: number | null = null

/** 앱을 열면, 어제(최근) 성사된 3교시 후기를 한 번 묻는다. 알림은 보내지 않는다 */
export function FeedbackPrompter() {
  const { member } = useAuth()
  const { pathname } = useLocation()
  const pending = usePendingFeedback(!!member)
  const [item, setItem] = useState<FeedbackPending | null>(null)

  const first = pending.data?.[0]
  // 처음 한 번만 고정한다: 답하는 동안 목록이 새로 와도 창 내용이 바뀌지 않게
  if (!item && first && member && finishedFor !== member.id && MAIN_PATHS.includes(pathname)) {
    setItem(first)
  }
  if (!item || !member) return null
  return (
    <FeedbackSheet
      item={item}
      onDone={() => {
        finishedFor = member.id
        setItem(null)
      }}
    />
  )
}

type Step = 'met' | 'rate' | 'sorry'

function FeedbackSheet({ item, onDone }: { item: FeedbackPending; onDone: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  /** 창이 열리면 닫기 버튼 대신 본문에 포커스 (화면 읽기도 제목부터) */
  const body = useRef<HTMLDivElement>(null)
  /** 닫힐 때 띄울 말. null 이면 답하지 않고 닫은 것 */
  const outcome = useRef<string | null>(null)
  const [step, setStep] = useState<Step>('met')
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)
  const submit = useSubmitFeedback()
  const toast = useToast()
  // 아쉬웠어요 다음 단계에서 함께한 사람 목록을 보여준다
  const detail = useMeetup(item.meetupId)
  const others = (detail.data?.participants ?? []).filter((p) => !p.me && p.ref !== null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
    body.current?.focus()
  }, [])

  function finish(message: string | null) {
    outcome.current = message
    ref.current?.close()
  }

  /** 창이 닫히면 (X, 뒤로 가기·ESC 포함): 답하기 전이었으면 "닫기"로 남겨 다시 묻지 않는다 */
  function onClose() {
    const message = outcome.current
    if (message !== null) {
      if (message) toast.show(message, 'ok')
    } else if (step === 'sorry') {
      toast.show('고마워요. 다음엔 더 좋은 3교시가 되게 할게요', 'ok')
    } else {
      submit.mutate({ id: item.meetupId, input: { skip: true } })
      toast.show('닫았어요. 3교시 상세에서 나중에 남길 수 있어요')
    }
    onDone()
  }

  function send(input: FeedbackInput, then: () => void) {
    submit.mutate(
      { id: item.meetupId, input },
      {
        onSuccess: then,
        onError: (e) => {
          toast.show(e.message, 'error')
          finish('')
        },
      },
    )
  }

  const p = kstParts(new Date(item.meetAt))
  const mmdd = `${p.month}.${String(p.day).padStart(2, '0')}`
  const day = formatDayLabel(item.meetAt)

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="feedback-title"
      className="sheet-up mx-auto mt-auto mb-0 w-full max-w-lg rounded-t-[26px] border border-line bg-surface p-0 text-ink backdrop:bg-black/50 sm:mb-auto sm:rounded-3xl"
    >
      <div ref={body} tabIndex={-1} className="px-5 pt-2.5 pb-[calc(28px+env(safe-area-inset-bottom))] outline-none sm:pb-6">
        <div aria-hidden className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-line-strong sm:hidden" />
        <div className="flex items-center justify-between gap-2">
          <p className="text-[13px] font-extrabold text-accent-ink">
            {day === '어제' ? '어제' : '지난'} 3교시 후기 · {step === 'met' ? '1/2' : '2/2'}
          </p>
          <button
            type="button"
            aria-label="닫기"
            onClick={() => ref.current?.close()}
            className="-mr-2 flex size-10 items-center justify-center rounded-xl text-ink-3 hover:bg-surface-2 hover:text-ink"
          >
            <X className="size-5" aria-hidden />
          </button>
        </div>

        <div className="mt-2 flex items-center gap-3 rounded-2xl bg-surface-2 p-3">
          <div className="flex size-14 shrink-0 flex-col items-center justify-center rounded-xl bg-surface shadow-[0_0_0_1px_var(--line),0_2px_0_var(--line-strong)]">
            <span className="tabular text-[11px] font-bold text-ink-3">{mmdd}</span>
            <span className="tabular text-base font-extrabold">{formatTime(item.meetAt)}</span>
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[15px] font-extrabold">{item.title}</p>
            <p className="mt-0.5 truncate text-[13px] text-ink-2">
              {item.place} · {item.participantCount}명
            </p>
          </div>
          <span
            aria-label="완료된 3교시"
            role="img"
            className="flex size-12 shrink-0 rotate-[-12deg] flex-col items-center justify-center rounded-full border-2 border-ok-ink text-ok-ink"
          >
            <span className="text-[11px] leading-none font-black">완료</span>
            <span className="tabular text-[9px] leading-tight font-extrabold">{mmdd}</span>
          </span>
        </div>

        {step === 'met' && (
          <div key="met" className="toast-in">
            <h2 id="feedback-title" className="mt-5 text-[22px] leading-snug font-extrabold tracking-[-0.02em]">
              실제로 모였나요?
            </h2>
            <p className="mt-1.5 text-sm leading-relaxed text-ink-2">
              한 번만 답하면 돼요. 누가 뭐라고 답했는지는 아무도 못 봐요.
            </p>
            <div className="mt-5 grid grid-cols-2 gap-2.5">
              <button
                type="button"
                disabled={submit.isPending}
                onClick={() => setStep('rate')}
                className={buttonClass('primary', 'lg')}
              >
                네, 모였어요
              </button>
              <button
                type="button"
                disabled={submit.isPending}
                onClick={() => send({ met: false }, () => finish('알려 줘서 고마워요'))}
                className={buttonClass('secondary', 'lg')}
              >
                안 모였어요
              </button>
            </div>
          </div>
        )}

        {step === 'rate' && (
          <div key="rate" className="toast-in">
            <h2 id="feedback-title" className="mt-5 text-[22px] leading-snug font-extrabold tracking-[-0.02em]">
              어땠어요?
            </h2>
            <p className="mt-1.5 text-sm leading-relaxed text-ink-2">연 사람에게도 안 보여요. 다음 3교시를 만드는 데만 써요.</p>
            <div className="mt-5 grid grid-cols-3 gap-2.5">
              {RATING_CHOICES.map(({ value, label, Icon }) => (
                <button
                  key={value}
                  type="button"
                  disabled={submit.isPending}
                  onClick={() =>
                    send({ met: true, rating: value }, () =>
                      value === 'BAD' ? setStep('sorry') : finish('고마워요! 다음 3교시 만드는 데 쓸게요'),
                    )
                  }
                  className="key key-line flex h-[104px] flex-col items-center justify-center gap-2.5 rounded-2xl border border-line-strong bg-surface text-[15px] font-extrabold text-ink hover:bg-surface-2 disabled:opacity-50"
                >
                  <Icon className="size-8 text-ink-2" strokeWidth={2} aria-hidden />
                  {label}
                </button>
              ))}
            </div>
          </div>
        )}

        {step === 'sorry' && (
          <div key="sorry" className="toast-in">
            <h2 id="feedback-title" className="mt-5 text-[22px] leading-snug font-extrabold tracking-[-0.02em]">
              불편한 사람이 있었나요?
            </h2>
            <p className="mt-1.5 text-sm leading-relaxed text-ink-2">신고는 교수·대학원장만 보고, 누가 했는지는 안 보여요.</p>
            {others.length > 0 && (
              <ul className="mt-4 divide-y divide-line rounded-2xl border border-line">
                {others.map((person) => (
                  <li key={person.ref} className="flex items-center gap-2.5 py-2.5 pr-3 pl-3.5">
                    <span
                      aria-hidden
                      className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-2 text-sm font-extrabold text-ink-2"
                    >
                      {person.name.charAt(0)}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-[15px] font-bold">
                      {person.name}{' '}
                      <span className="text-[13px] font-medium text-ink-3">
                        {person.host ? '연 사람 · ' : ''}
                        {affiliation(person.cohortLabel, person.departmentLabel)}
                      </span>
                    </span>
                    <button
                      type="button"
                      aria-label={`${person.name} 신고하기`}
                      onClick={() =>
                        setReporting({
                          type: 'MEMBER',
                          id: person.ref!,
                          label: `${person.name} · ${affiliation(person.cohortLabel, person.departmentLabel)} · ${person.school.shortName}`,
                          fromFeedback: true,
                        })
                      }
                      className={buttonClass('danger', 'sm')}
                    >
                      <Flag className="size-4" aria-hidden />
                      신고
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <button
              type="button"
              onClick={() => finish('고마워요. 다음엔 더 좋은 3교시가 되게 할게요')}
              className={buttonClass('secondary', 'lg', 'mt-4 w-full')}
            >
              없었어요, 그냥 아쉬웠어요
            </button>
          </div>
        )}
      </div>

      {reporting && (
        <ReportDialog
          key={reporting.id}
          target={reporting}
          onClose={() => setReporting(null)}
          onReported={() => finish('')}
        />
      )}
    </dialog>
  )
}
