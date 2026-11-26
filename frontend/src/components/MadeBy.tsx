import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Copy, Mail, X } from 'lucide-react'
import { useAbout } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { COMPANY, MADE_BY, SERVICE } from '../lib/credit'
import { affiliation } from '../lib/person'
import { SchoolBadge } from './SchoolBadge'
import { useToast } from './toastContext'

/**
 * "써클"을 누르면 뜨는 만든 곳 정보: 만든 사람 이름 · 학교 · 기수 · 학과 · 이메일.
 * 로그인했으면 서버의 대학원장 계정 정보로, 로그인 전에는 이름과 소속만 (이메일이 긁히지 않게)
 */
function AboutDialog({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const { member } = useAuth()
  const { data, isPending } = useAbout(Boolean(member))
  const toast = useToast()
  const op = data?.operator

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast.show('이메일을 복사했어요', 'ok')
    } catch {
      toast.show(text)
    }
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && ref.current?.close()}
      aria-labelledby="about-title"
      className="m-auto w-[min(24rem,calc(100%-2rem))] rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="flex items-start justify-between gap-3 px-6 pt-6">
        <div>
          <p className="text-[13px] font-semibold text-ink-3">{SERVICE}를 만든 곳</p>
          <h2 id="about-title" className="mt-0.5 text-2xl font-extrabold tracking-[-0.02em]">
            {COMPANY}
          </h2>
        </div>
        <button
          type="button"
          onClick={() => ref.current?.close()}
          aria-label="닫기"
          className="-mr-2 flex size-10 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>

      <dl className="mx-6 mt-5 mb-6 flex flex-col gap-3 rounded-2xl bg-surface-2 px-4 py-4 text-[15px]">
        <div className="flex gap-3">
          <dt className="w-14 shrink-0 text-ink-3">만든 사람</dt>
          <dd className="min-w-0 font-bold">{op?.name ?? MADE_BY.name}</dd>
        </div>
        <div className="flex gap-3">
          <dt className="w-14 shrink-0 text-ink-3">소속</dt>
          <dd className="flex min-w-0 flex-col gap-1">
            {op ? (
              <>
                <span className="flex flex-wrap items-center gap-1.5">
                  <SchoolBadge school={op.school} full />
                  {op.graduateSchool && <span className="text-ink-2">{op.graduateSchool}</span>}
                </span>
                <span className="text-ink-2">{affiliation(op.cohortLabel, op.departmentLabel)}</span>
              </>
            ) : (
              <span className="text-ink-2">{MADE_BY.affiliation}</span>
            )}
          </dd>
        </div>
        {member && (
          <div className="flex gap-3">
            <dt className="w-14 shrink-0 text-ink-3">이메일</dt>
            <dd className="min-w-0">
              {isPending ? (
                <span className="text-ink-3">불러오는 중</span>
              ) : op?.email ? (
                <span className="flex items-center gap-1">
                  <a href={`mailto:${op.email}`} className="truncate font-semibold text-accent-ink underline underline-offset-4">
                    {op.email}
                  </a>
                  <button
                    type="button"
                    onClick={() => copy(op.email as string)}
                    aria-label="이메일 복사"
                    className="flex size-8 shrink-0 items-center justify-center rounded-lg text-ink-3 hover:bg-surface hover:text-ink"
                  >
                    <Copy className="size-4" aria-hidden />
                  </button>
                </span>
              ) : (
                <span className="text-ink-3">–</span>
              )}
            </dd>
          </div>
        )}
        {member && data?.supportEmail && data.supportEmail !== op?.email && (
          <div className="flex gap-3">
            <dt className="w-14 shrink-0 text-ink-3">문의</dt>
            <dd className="min-w-0">
              <a href={`mailto:${data.supportEmail}`} className="inline-flex items-center gap-1 truncate font-semibold text-accent-ink underline underline-offset-4">
                <Mail className="size-4 shrink-0" aria-hidden />
                {data.supportEmail}
              </a>
            </dd>
          </div>
        )}
      </dl>
      {!member && <p className="-mt-3 px-6 pb-6 text-[13px] text-ink-3">로그인하면 연락할 이메일이 보여요.</p>}
    </dialog>
  )
}

/** "써클" 글자: 누르면 만든 곳 정보 */
function CompanyButton({ className = '' }: { className?: string }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        className={`font-bold underline decoration-dotted underline-offset-[3px] hover:decoration-solid ${className}`}
      >
        {COMPANY}
      </button>
      {/* 문단(<p>) 안이라 창은 문서 끝에 그린다 */}
      {open && createPortal(<AboutDialog onClose={() => setOpen(false)} />, document.body)}
    </>
  )
}

/** "안암3교시 · by 써클" — 첫 화면 · PC 메뉴 아래. onBoard: 어두운 안내판 위 */
export function ByCompany({ onBoard = false, className = '' }: { onBoard?: boolean; className?: string }) {
  return (
    <p className={`text-xs leading-relaxed ${onBoard ? 'text-on-board-2' : 'text-ink-3'} ${className}`}>
      {SERVICE} · by <CompanyButton className={onBoard ? 'text-on-board' : 'text-ink-2'} />
    </p>
  )
}

/** "써클 · 만든 사람 유동준 (고려대 SW·AI융합대학원 61기)" — 내 정보 맨 아래. 좁으면 소속 앞에서 줄이 바뀐다 */
export function MadeBy({ className = '' }: { className?: string }) {
  return (
    <p className={`text-xs leading-relaxed text-ink-3 ${className}`}>
      <CompanyButton className="text-ink-2" />
      <span aria-hidden> · </span>
      <span className="whitespace-nowrap">
        만든 사람 <b className="font-semibold text-ink-2">{MADE_BY.name}</b>
      </span>{' '}
      <span className="whitespace-nowrap">({MADE_BY.affiliation})</span>
    </p>
  )
}
