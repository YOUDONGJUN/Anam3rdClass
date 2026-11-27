import { useState } from 'react'
import { useWarnMember } from '../api/queries'
import type { ReportReason } from '../api/types'
import { REPORT_REASONS } from '../lib/report'
import { nextSanction, SANCTION_STEPS, warnEffects } from '../lib/sanction'
import { BottomSheet } from './BottomSheet'
import { buttonClass, chipClass, inputClass } from './styles'
import { FormError } from './ui'
import { useToast } from './toastContext'

/**
 * 경고 주기 (교수·대학원장). 단계는 쌓인 수로 정해진다: 1번째 경고 · 2번째 7일 정지 · 3번째 30일 정지 · 4번째 영구 정지.
 * 신고를 보고 주면(reportId) 그 신고도 처리 완료로 바뀐다
 */
export function WarnSheet({
  memberId,
  name,
  strikes,
  reason: initialReason = null,
  reportId = null,
  onClose,
}: {
  memberId: number
  name: string
  /** 지금까지 받은 경고 수 */
  strikes: number
  reason?: ReportReason | null
  reportId?: number | null
  onClose: () => void
}) {
  const warn = useWarnMember()
  const toast = useToast()
  const [reason, setReason] = useState<ReportReason | null>(initialReason)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const next = nextSanction(strikes)
  const severe = next.kind !== 'WARNING'

  function submit() {
    if (!reason) return setError('경고 이유를 골라 주세요')
    setError(null)
    warn.mutate(
      { id: memberId, reason, note: note.trim(), reportId },
      {
        onSuccess: (r) => {
          toast.show(r.kind === 'WARNING' ? '경고했어요' : `경고했어요 · ${r.kindLabel}`, 'ok')
          onClose()
        },
        onError: (e) => setError(e.message),
      },
    )
  }

  return (
    <BottomSheet
      title="경고 주기"
      onClose={onClose}
      footer={
        <div className="flex flex-col gap-2">
          <FormError message={error} />
          <div className="flex gap-2">
            <button type="button" className={buttonClass('secondary', 'md', 'shrink-0')} onClick={onClose}>
              취소
            </button>
            <button
              type="button"
              className={buttonClass(severe ? 'danger' : 'primary', 'md', 'flex-1')}
              disabled={warn.isPending}
              onClick={submit}
            >
              {next.strike}번째 경고 주기
            </button>
          </div>
        </div>
      }
    >
      <div className="flex flex-col gap-5">
        <div className="rounded-xl bg-surface-2 px-4 py-3">
          <p className="text-[15px] font-bold break-keep text-ink">
            {name} 님 · {next.strike}번째 경고
            {severe && <span className="text-danger-ink"> → {next.label}</span>}
          </p>
          <ul className="mt-1.5 flex list-disc flex-col gap-0.5 pl-4 text-sm leading-relaxed break-keep text-ink-2">
            {warnEffects(strikes).map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
          <p className="mt-2 text-[13px] break-keep text-ink-3">단계: {SANCTION_STEPS.join(' → ')}</p>
        </div>

        <fieldset>
          <legend className="mb-2 text-[15px] font-bold">이유</legend>
          <div className="flex flex-wrap gap-2">
            {REPORT_REASONS.map((r) => (
              <button
                key={r.value}
                type="button"
                aria-pressed={reason === r.value}
                className={chipClass(reason === r.value)}
                onClick={() => setReason(r.value)}
              >
                {r.label}
              </button>
            ))}
          </div>
        </fieldset>

        <label className="flex flex-col gap-2">
          <span className="text-[15px] font-bold">
            본인에게 보일 내용 <span className="text-xs font-medium text-ink-3">선택</span>
          </span>
          <textarea
            rows={3}
            maxLength={300}
            className={`${inputClass} h-auto py-3 leading-relaxed`}
            placeholder="예: 톡방에서 다른 원우에게 욕설을 했어요"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </label>
      </div>
    </BottomSheet>
  )
}
