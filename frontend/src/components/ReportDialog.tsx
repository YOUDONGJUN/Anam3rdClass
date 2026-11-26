import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Ban, Flag, X } from 'lucide-react'
import { useBlock, useReport } from '../api/queries'
import type { ReportReason, ReportTarget } from '../api/types'
import { REPORT_REASONS } from '../lib/report'
import { Button, FormError } from './ui'
import { inputClass } from './styles'
import { useToast } from './toastContext'

export interface ReportTargetInfo {
  type: ReportTarget
  /** 사람이면 이름 (연 사람 메뉴의 확인 문구에 쓴다) */
  name?: string
  /** MEETUP → 3교시 번호, NOTICE → 공지 번호, MEMBER → 참여 번호(PersonView.ref) */
  id: number
  /** 창 제목에 보일 이름: "3교시 ‘치맥 번개’", "김*대 · 61기 · 인융" */
  label: string
  /** 3교시 후기에서 '아쉬웠어요' 뒤에 하는 사람 신고 (서버가 정말 그렇게 답했는지 확인하고 표시한다) */
  fromFeedback?: boolean
}

/**
 * 신고하기 (+ 사람이면 차단하기). 신고는 그 학교 교수·대학원장에게 가고, 신고한 사람은 보이지 않는다.
 */
export function ReportDialog({
  target,
  onClose,
  onReported,
  hostTools,
}: {
  target: ReportTargetInfo
  onClose: () => void
  /** 신고나 차단을 마쳤을 때 (닫기만 했을 때는 부르지 않는다) */
  onReported?: () => void
  /** 연 사람 메뉴 (내보내기 · 연 사람 넘기기). close 를 받아 일을 마치면 창을 닫는다 */
  hostTools?: (close: () => void) => ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const report = useReport()
  const block = useBlock()
  const toast = useToast()
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [detail, setDetail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const person = target.type === 'MEMBER'

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  function close() {
    ref.current?.close()
  }

  // 연 사람 메뉴로 일을 마치면 창을 닫는다 (그리는 중에 창을 건드리지 않게 상태로 넘긴다)
  const [finished, setFinished] = useState(false)
  useEffect(() => {
    if (finished) ref.current?.close()
  }, [finished])

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!reason) return setError('신고 이유를 골라 주세요')
    setError(null)
    report.mutate(
      { targetType: target.type, targetId: target.id, reason, detail: detail.trim(), fromFeedback: target.fromFeedback },
      {
        onSuccess: () => {
          toast.show('신고했어요. 교수·대학원장이 확인할게요', 'ok')
          onReported?.()
          close()
        },
        onError: (err) => setError(err.message),
      },
    )
  }

  function doBlock() {
    if (!window.confirm('차단할까요?\n\n• 서로가 연 3교시가 안 보여요\n• 서로의 3교시에 참여할 수 없어요')) return
    setError(null)
    block.mutate(target.id, {
      onSuccess: () => {
        toast.show('차단했어요. 내 정보에서 풀 수 있어요', 'ok')
        onReported?.()
        close()
      },
      onError: (err) => setError(err.message),
    })
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="report-title"
      className="m-auto max-h-[88dvh] w-[min(30rem,calc(100%-2rem))] overflow-y-auto rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b border-line bg-surface px-5 py-4">
        <div className="min-w-0">
          <h2 id="report-title" className="text-lg font-extrabold">
            {person ? '신고·차단' : '신고하기'}
          </h2>
          <p className="mt-0.5 truncate text-sm text-ink-2">{target.label}</p>
        </div>
        <button
          type="button"
          aria-label="닫기"
          onClick={close}
          className="flex size-10 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>

      <form onSubmit={submit} className="flex flex-col gap-4 px-5 py-5" noValidate>
        {hostTools && (
          <div className="flex flex-col gap-2 border-b border-line pb-5">
            <p className="text-[15px] font-bold">연 사람 메뉴</p>
            {hostTools(() => setFinished(true))}
          </div>
        )}
        <fieldset>
          <legend className="mb-2 text-[15px] font-bold">어떤 문제가 있나요?</legend>
          <div className="grid grid-cols-2 gap-2">
            {REPORT_REASONS.map((r) => {
              const active = reason === r.value
              return (
                <label
                  key={r.value}
                  className={`flex min-h-11 cursor-pointer items-center rounded-xl border px-3 text-sm font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
                    active ? 'border-board bg-board text-on-board' : 'border-line-strong text-ink hover:border-ink-3'
                  }`}
                >
                  <input
                    type="radio"
                    name="reason"
                    value={r.value}
                    checked={active}
                    onChange={() => setReason(r.value)}
                    className="sr-only"
                  />
                  {r.label}
                </label>
              )
            })}
          </div>
        </fieldset>
        <label className="flex flex-col gap-2">
          <span className="text-[15px] font-bold">
            자세한 내용 <span className="text-xs font-medium text-ink-3">선택</span>
          </span>
          <textarea
            rows={3}
            maxLength={500}
            className={`${inputClass} h-auto py-3 leading-relaxed`}
            placeholder="무엇이 문제인지 적어 주면 확인이 빨라요"
            value={detail}
            onChange={(e) => setDetail(e.target.value)}
          />
        </label>
        <p className="text-[13px] leading-relaxed text-ink-3">
          교수·대학원장만 봐요. 누가 신고했는지는 아무도 몰라요.
        </p>
        <FormError message={error} />
        <Button type="submit" variant="danger" loading={report.isPending}>
          <Flag className="size-4" aria-hidden />
          신고하기
        </Button>

        {person && (
          <div className="mt-2 flex flex-col gap-2 border-t border-line pt-5">
            <p className="text-[15px] font-bold">차단하기</p>
            <p className="text-sm leading-relaxed text-ink-2">
              서로의 3교시가 안 보이고 참여도 못 해요. 상대는 몰라요.
            </p>
            <Button variant="secondary" loading={block.isPending} onClick={doBlock}>
              <Ban className="size-4" aria-hidden />
              이 사람 차단
            </Button>
          </div>
        )}
      </form>
    </dialog>
  )
}
