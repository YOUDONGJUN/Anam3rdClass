import { useState } from 'react'
import { Check } from 'lucide-react'
import { useSubmitFeedback } from '../api/queries'
import type { FeedbackInput, MeetupDetail } from '../api/types'
import { feedbackDeadline } from '../lib/feedback'
import { formatDayLabel } from '../lib/time'
import { RATING_CHOICES } from './ratingChoices'
import { buttonClass, cardClass } from './styles'
import { useToast } from './toastContext'

type Step = 'met' | 'rate' | 'sorry' | 'done'

/**
 * 3교시 상세의 후기 칸: 후기 창을 닫았거나 놓쳤어도, 기간 안에는 여기서 남길 수 있다.
 * 이미 남겼으면 보이지 않는다 (이 화면에서 방금 남겼으면 고맙다는 줄만 남는다).
 */
export function FeedbackCard({ m, now }: { m: MeetupDetail; now: Date }) {
  const [step, setStep] = useState<Step | null>(null)
  const [doneText, setDoneText] = useState('')
  const submit = useSubmitFeedback()
  const toast = useToast()

  if (!m.feedback || (m.feedback.answered && step === null)) return null
  const current: Step = step ?? 'met'

  function send(input: FeedbackInput, next: Step, text = '후기를 남겼어요. 고마워요!') {
    submit.mutate(
      { id: m.id, input },
      {
        onSuccess: () => {
          setDoneText(text)
          setStep(next)
        },
        onError: (e) => toast.show(e.message, 'error'),
      },
    )
  }

  const deadline = feedbackDeadline(m.feedback.until, now)

  return (
    <section aria-labelledby="feedback-card-title" className={`${cardClass} px-5 py-5`}>
      <div className="flex items-center justify-between gap-2">
        <h2 id="feedback-card-title" className="text-[17px] font-extrabold text-ink">
          {current === 'done'
            ? '3교시 후기'
            : `${formatDayLabel(m.meetAt, now) === '어제' ? '어제' : '지난'} 3교시 어땠어요?`}
        </h2>
        {current !== 'done' && deadline && (
          <span className="shrink-0 rounded-lg bg-accent-soft px-2 py-1 text-xs font-extrabold text-accent-ink">
            {deadline}
          </span>
        )}
      </div>

      {current === 'met' && (
        <>
          <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">
            실제로 모였나요? 누가 뭐라고 답했는지는 아무도 못 봐요.
          </p>
          <div className="mt-4 grid grid-cols-2 gap-2.5">
            <button type="button" disabled={submit.isPending} onClick={() => setStep('rate')} className={buttonClass('primary')}>
              네, 모였어요
            </button>
            <button
              type="button"
              disabled={submit.isPending}
              onClick={() => send({ met: false }, 'done', '안 모였다고 남겼어요. 고마워요')}
              className={buttonClass('secondary')}
            >
              안 모였어요
            </button>
          </div>
        </>
      )}

      {current === 'rate' && (
        <>
          <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">어땠어요? 연 사람에게도 안 보여요.</p>
          <div className="mt-4 grid grid-cols-3 gap-2">
            {RATING_CHOICES.map(({ value, label, Icon }) => (
              <button
                key={value}
                type="button"
                disabled={submit.isPending}
                onClick={() => send({ met: true, rating: value }, value === 'BAD' ? 'sorry' : 'done')}
                className="key key-line flex h-[84px] flex-col items-center justify-center gap-2 rounded-xl border border-line-strong bg-surface text-sm font-extrabold text-ink hover:bg-surface-2 disabled:opacity-50"
              >
                <Icon className="size-7 text-ink-2" strokeWidth={2} aria-hidden />
                {label}
              </button>
            ))}
          </div>
        </>
      )}

      {current === 'sorry' && (
        <>
          <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">
            불편한 사람이 있었다면 아래 참여자 옆 ⋯ 버튼에서 신고할 수 있어요. 누가 했는지는 안 보여요.
          </p>
          <button type="button" onClick={() => setStep('done')} className={buttonClass('secondary', 'md', 'mt-4 w-full')}>
            괜찮아요, 그냥 아쉬웠어요
          </button>
        </>
      )}

      {current === 'done' && (
        <p role="status" className="mt-3 flex items-center gap-2.5 rounded-xl bg-ok-soft px-3.5 py-3 text-[15px] font-bold text-ok-ink">
          <Check className="size-5 shrink-0" strokeWidth={2.6} aria-hidden />
          {doneText}
        </p>
      )}
    </section>
  )
}
