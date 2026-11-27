import { useEffect, useRef } from 'react'
import { ShieldAlert } from 'lucide-react'
import { useAckSanction } from '../api/queries'
import type { SanctionNotice } from '../api/types'
import { formatDateTime } from '../lib/time'
import { SANCTION_STEPS } from '../lib/sanction'
import { Button } from './ui'
import { useToast } from './toastContext'

/**
 * 경고 받은 원우가 앱을 열면 보이는 창. "확인했어요"를 눌러야 닫힌다 (ESC·뒤로로는 닫히지 않는다).
 * 기간 정지는 끝난 뒤 처음 열 때 보인다 (정지 중에는 로그인할 수 없다)
 */
export function SanctionNoticeDialog({ notice }: { notice: SanctionNotice }) {
  const ref = useRef<HTMLDialogElement>(null)
  const ack = useAckSanction()
  const toast = useToast()

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  const suspension = notice.kind === 'SUSPEND_7' || notice.kind === 'SUSPEND_30'
  const title = suspension ? `${notice.kindLabel}가 끝났어요` : '운영진 경고를 받았어요'
  const current = Math.min(Math.max(notice.strikes, 1), SANCTION_STEPS.length)

  return (
    <dialog
      ref={ref}
      onCancel={(e) => e.preventDefault()}
      aria-labelledby="sanction-title"
      className="m-auto max-h-[88dvh] w-[min(26rem,calc(100%-2rem))] overflow-y-auto rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/55"
    >
      <div className="flex flex-col gap-4 px-5 pt-6 pb-5">
        <div className="flex flex-col items-center gap-2 text-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-danger-soft text-danger-ink">
            <ShieldAlert className="size-6" aria-hidden />
          </span>
          <h2 id="sanction-title" className="text-lg font-extrabold break-keep">
            {title}
          </h2>
          <p className="text-sm text-ink-3">{formatDateTime(notice.createdAt)}</p>
        </div>

        <dl className="flex flex-col gap-2 rounded-xl bg-surface-2 px-4 py-3 text-sm">
          <div className="flex gap-3">
            <dt className="w-10 shrink-0 font-semibold text-ink-3">이유</dt>
            <dd className="min-w-0 font-semibold break-keep text-ink">{notice.reasonLabel}</dd>
          </div>
          {notice.note && (
            <div className="flex gap-3">
              <dt className="w-10 shrink-0 font-semibold text-ink-3">내용</dt>
              <dd className="min-w-0 break-words whitespace-pre-wrap text-ink">{notice.note}</dd>
            </div>
          )}
          {suspension && notice.endsAt && (
            <div className="flex gap-3">
              <dt className="w-10 shrink-0 font-semibold text-ink-3">정지</dt>
              <dd className="min-w-0 text-ink">{formatDateTime(notice.endsAt)}까지였어요</dd>
            </div>
          )}
        </dl>

        <div>
          <p className="mb-2 text-sm font-bold text-ink">경고가 쌓이면</p>
          <ol className="grid grid-cols-4 gap-1" aria-label={`지금 ${notice.strikes}번째`}>
            {SANCTION_STEPS.map((label, i) => {
              const step = i + 1
              const reached = step <= current
              return (
                <li key={label} className="flex flex-col items-center gap-1.5" aria-current={step === current ? 'step' : undefined}>
                  <span className={`h-1.5 w-full rounded-full ${reached ? 'bg-danger' : 'bg-line'}`} aria-hidden />
                  <span
                    className={`text-center text-[12px] leading-tight whitespace-nowrap ${
                      step === current ? 'font-extrabold text-danger-ink' : 'text-ink-3'
                    }`}
                  >
                    {label}
                  </span>
                </li>
              )
            })}
          </ol>
          <p className="mt-3 text-sm leading-relaxed break-keep text-ink-2">
            지금 경고 {notice.strikes}번이에요. 한 번 더 받으면 <b className="font-bold text-ink">{notice.nextLabel}</b>예요.
          </p>
        </div>

        <p className="text-[13px] leading-relaxed break-keep text-ink-3">
          궁금한 점은 교수·대학원장에게 문의해 주세요.
        </p>

        <Button
          className="w-full"
          loading={ack.isPending}
          onClick={() =>
            ack.mutate(notice.id, {
              onSuccess: () => ref.current?.close(),
              onError: (e) => toast.show(e.message, 'error'),
            })
          }
        >
          확인했어요
        </Button>
      </div>
    </dialog>
  )
}
