import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ArrowUpRight, Bell, Check, PartyPopper, Pencil, ReceiptText, Undo2, X } from 'lucide-react'
import { usePayLinks, useSettlementAction, type SettlementAction } from '../api/queries'
import type { MeetupDetail, SettlementView } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import {
  defaultTotal,
  formatWon,
  normalizeKakaoPayUrl,
  normalizeTossId,
  parseWon,
  payLinksError,
  perPerson,
  totalError,
} from '../lib/settlement'
import { formatDateTime } from '../lib/time'
import { PayLinksFields } from './PayLinksFields'
import { PersonName } from './PersonName'
import { Button, FormError } from './ui'
import { buttonClass, cardClass, inputClass } from './styles'
import { useToast } from './toastContext'

/**
 * 3교시 상세의 정산 칸 (함께한 원우에게만). 돈은 서비스가 받지 않는다 —
 * 송금은 각자 토스·카카오페이 앱에서 하고, 여기서는 얼마씩 · 어디로 · 누가 보냈는지만 본다.
 */
export function SettlementCard({ m }: { m: MeetupDetail }) {
  const [sheet, setSheet] = useState<'create' | 'edit' | null>(null)
  if (!m.settlement && !m.canSettle) return null
  return (
    <>
      {m.settlement ? (
        <SettlementBody m={m} s={m.settlement} onEdit={() => setSheet('edit')} />
      ) : (
        <section className={`${cardClass} flex flex-wrap items-center gap-x-4 gap-y-3 px-5 py-4`} aria-labelledby="settle-title">
          <span className="flex min-w-[13rem] flex-1 items-center gap-3">
            <SettleIcon />
            <span className="min-w-0">
              <span id="settle-title" className="block text-[15px] font-bold text-ink">
                정산하기
              </span>
              <span className="block text-[13px] leading-snug text-ink-3">
                계산한 원우가 총액을 넣으면 1인 금액과 송금 링크를 보내 드려요
              </span>
            </span>
          </span>
          <Button className="w-full sm:w-auto" onClick={() => setSheet('create')}>
            정산하기
          </Button>
        </section>
      )}
      {sheet && <SettlementSheet m={m} editing={sheet === 'edit'} onClose={() => setSheet(null)} />}
    </>
  )
}

function SettleIcon({ done = false }: { done?: boolean }) {
  return (
    <span
      className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${done ? 'bg-ok-soft text-ok-ink' : 'bg-surface-2 text-ink-2'}`}
    >
      {done ? <PartyPopper className="size-5" aria-hidden /> : <ReceiptText className="size-5" aria-hidden />}
    </span>
  )
}

function SettlementBody({ m, s, onEdit }: { m: MeetupDetail; s: SettlementView; onEdit: () => void }) {
  const action = useSettlementAction()
  const toast = useToast()
  const { member } = useAuth()
  const mineShare = s.shares.find((sh) => sh.person.me)
  const total = s.shares.length
  const progress = total === 0 ? 0 : Math.round((s.sentCount / total) * 100)

  function run(a: SettlementAction, ok?: string) {
    action.mutate(
      { id: m.id, action: a },
      {
        onSuccess: () => ok && toast.show(ok, 'ok'),
        onError: (e) => toast.show(e.message, 'error'),
      },
    )
  }

  function cancel() {
    const warn = s.sentCount > 0 ? '\n이미 보낸 원우가 있어요. 다시 만들면 보냄 표시도 처음부터예요.' : ''
    if (!window.confirm(`정산을 취소할까요?${warn}`)) return
    run({ kind: 'cancel' }, '정산을 취소했어요')
  }

  return (
    <section className={`${cardClass} overflow-hidden`} aria-labelledby="settle-title">
      <header className="flex items-start gap-3 px-5 pt-4 pb-3">
        <SettleIcon done={s.done} />
        <div className="min-w-0 flex-1">
          <h2 id="settle-title" className="text-[15px] font-bold text-ink">
            {s.done ? '정산 끝 · 모두 보냈어요' : '정산'}
          </h2>
          <p className="tabular mt-0.5 text-[22px] leading-tight font-extrabold tracking-[-0.02em] text-ink">
            1인 {formatWon(s.perPerson)}
          </p>
          <p className="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[13px] text-ink-3">
            {s.mine ? (
              <span className="font-semibold text-ink-2">내가 계산했어요</span>
            ) : (
              <span>
                <b className="font-semibold text-ink-2">{s.payer.name}</b> 님이 계산했어요
              </span>
            )}
            <span className="tabular">
              · 총 {formatWon(s.total)} ÷ {s.headcount}명
            </span>
          </p>
        </div>
      </header>

      {/* 보낼 원우: 송금 버튼과 보냈어요 */}
      {!s.mine && (
        <div className="border-t border-line px-5 py-4">
          {!mineShare ? (
            <p className="text-sm text-ink-3">이번 정산에서는 빠졌어요</p>
          ) : mineShare.sent ? (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="flex items-center gap-2 text-[15px] font-bold text-ok-ink">
                <Check className="size-5" aria-hidden />
                보냈어요
                {mineShare.sentAt && (
                  <span className="text-[13px] font-medium text-ink-3">{formatDateTime(mineShare.sentAt)}</span>
                )}
              </p>
              <Button
                variant="ghost"
                size="sm"
                loading={action.isPending}
                onClick={() => run({ kind: 'sent', sent: false }, '보냄 표시를 취소했어요')}
              >
                <Undo2 className="size-4" aria-hidden />
                보냄 취소
              </Button>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {s.tossUrl || s.kakaoPayUrl ? (
                <div className="flex flex-col gap-2 sm:flex-row">
                  {s.tossUrl && (
                    <a href={s.tossUrl} target="_blank" rel="noreferrer" className={buttonClass('secondary', 'md', 'w-full sm:w-auto sm:flex-1')}>
                      토스로 보내기
                      <ArrowUpRight className="size-4 text-ink-3" aria-hidden />
                    </a>
                  )}
                  {s.kakaoPayUrl && (
                    <a href={s.kakaoPayUrl} target="_blank" rel="noreferrer" className={buttonClass('secondary', 'md', 'w-full sm:w-auto sm:flex-1')}>
                      카카오페이로 보내기
                      <ArrowUpRight className="size-4 text-ink-3" aria-hidden />
                    </a>
                  )}
                </div>
              ) : (
                <p className="rounded-xl bg-surface-2 px-3.5 py-3 text-sm leading-relaxed text-ink-2">
                  계산한 원우가 송금 링크를 넣지 않았어요. 계좌는 단톡방에서 확인해 주세요.
                </p>
              )}
              {s.kakaoPayUrl && (
                <p className="text-[13px] text-ink-3">
                  카카오페이는 금액({formatWon(s.perPerson)})을 직접 넣어 주세요
                </p>
              )}
              <Button
                variant="ok"
                size="lg"
                className="w-full"
                loading={action.isPending}
                onClick={() => run({ kind: 'sent', sent: true }, '보냈다고 알렸어요')}
              >
                <Check className="size-5" aria-hidden />
                보냈어요
              </Button>
            </div>
          )}
        </div>
      )}

      {/* 누가 보냈나 */}
      <div className="border-t border-line px-5 pt-3.5 pb-4">
        <div className="mb-2.5 flex items-center justify-between gap-3">
          <p className="text-[13px] font-bold text-ink-2">누가 보냈나</p>
          <p className="tabular text-[13px] text-ink-3">
            {total}명 중 {s.sentCount}명 보냄
          </p>
        </div>
        <span
          className="relative mb-3 block h-2 w-full overflow-hidden rounded-full bg-surface-2"
          role="progressbar"
          aria-label="보낸 원우"
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={s.sentCount}
        >
          <span className="absolute inset-y-0 left-0 rounded-full bg-ok transition-[width] duration-300" style={{ width: `${progress}%` }} />
        </span>
        <ul className="flex flex-col divide-y divide-line">
          {s.shares.map((sh) => (
            <li key={sh.person.ref ?? sh.person.name} className="flex min-h-12 items-center justify-between gap-3 py-1.5">
              <span className="min-w-0 text-[15px] font-semibold text-ink">
                <PersonName person={sh.person} />
              </span>
              {s.mine && sh.person.ref !== null ? (
                <button
                  type="button"
                  aria-pressed={sh.sent}
                  disabled={action.isPending}
                  onClick={() => run({ kind: 'share', ref: sh.person.ref as number, sent: !sh.sent })}
                  className={`inline-flex h-9 shrink-0 items-center gap-1 rounded-lg px-3 text-sm font-semibold transition-colors disabled:opacity-50 ${
                    sh.sent ? 'bg-ok-soft text-ok-ink hover:brightness-95' : 'border border-line-strong text-ink-2 hover:bg-surface-2'
                  }`}
                >
                  {sh.sent && <Check className="size-4" aria-hidden />}
                  {sh.sent ? '받음' : '받았어요'}
                </button>
              ) : (
                <span
                  className={`inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-0.5 text-[13px] font-semibold ${
                    sh.sent ? 'bg-ok-soft text-ok-ink' : 'bg-surface-2 text-ink-3'
                  }`}
                >
                  {sh.sent && <Check className="size-3.5" aria-hidden />}
                  {sh.sent ? '보냄' : '아직'}
                </span>
              )}
            </li>
          ))}
        </ul>
        {s.mine && <p className="mt-2 text-[13px] text-ink-3">현금으로 받았으면 [받았어요]를 눌러 표시해요</p>}
      </div>

      {/* 계산한 원우: 다시 알리기 · 고치기 · 취소 (끝난 정산은 기록으로만 남긴다) */}
      {s.mine && !s.done && (
        <div className="flex flex-col gap-3 border-t border-line bg-surface-2/50 px-5 py-4">
          {!member?.tossId && !member?.kakaoPayUrl && (
            <p className="text-sm leading-relaxed text-ink-2">
              내 송금 링크가 없어서 원우들에게 송금 버튼이 안 보여요.{' '}
              <Link to="/me#pay-links" className="font-semibold text-accent-ink underline underline-offset-4">
                송금 링크 넣기
              </Link>
            </p>
          )}
          <div className="flex flex-col gap-1.5">
            <Button
              variant="secondary"
              className="w-full"
              disabled={!s.canRemind}
              loading={action.isPending}
              onClick={() => run({ kind: 'remind' }, '안 보낸 원우에게 다시 알렸어요')}
            >
              <Bell className="size-4" aria-hidden />
              안 보낸 원우에게 다시 알리기
            </Button>
            {!s.canRemind && s.remindableAt && (
              <p className="text-center text-[13px] text-ink-3">
                하루에 한 번 · {formatDateTime(s.remindableAt)}부터 다시 알릴 수 있어요
              </p>
            )}
          </div>
          <div className="flex flex-wrap justify-end gap-1">
            {s.editable && (
              <Button variant="ghost" size="sm" onClick={onEdit}>
                <Pencil className="size-4" aria-hidden />
                고치기
              </Button>
            )}
            <button
              type="button"
              onClick={cancel}
              disabled={action.isPending}
              className="inline-flex h-9 items-center rounded-xl px-3 text-sm font-semibold text-danger-ink transition-colors hover:bg-danger-soft disabled:opacity-50"
            >
              정산 취소
            </button>
          </div>
        </div>
      )}
    </section>
  )
}

/** 정산 만들기 · 고치기 */
function SettlementSheet({ m, editing, onClose }: { m: MeetupDetail; editing: boolean; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const { member } = useAuth()
  const action = useSettlementAction()
  const payLinks = usePayLinks()
  const toast = useToast()
  const s = editing ? m.settlement : null
  // 나눌 수 있는 원우: 나와 탈퇴한 원우는 빼고
  const others = m.participants.filter((p) => !p.me && !p.withdrawn && p.ref !== null)
  const [picked, setPicked] = useState<Set<number>>(
    () => new Set(s ? s.shares.map((sh) => sh.person.ref).filter((r): r is number => r !== null) : others.map((p) => p.ref as number)),
  )
  const [total, setTotal] = useState<number | null>(() => s?.total ?? defaultTotal(m.fee, m.participantCount))
  const needLinks = !member?.tossId && !member?.kakaoPayUrl
  const [tossId, setTossId] = useState('')
  const [kakaoPayUrl, setKakaoPayUrl] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  const headcount = picked.size + 1
  const each = total === null ? 0 : perPerson(total, headcount)
  const busy = action.isPending || payLinks.isPending

  function toggle(r: number) {
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(r)) next.delete(r)
      else next.add(r)
      return next
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const bad = totalError(total)
    if (bad) return setError(bad)
    if (picked.size === 0) return setError('나 말고 한 명 이상 골라 주세요')
    if (needLinks && (tossId.trim() || kakaoPayUrl.trim())) {
      const linkError = payLinksError(tossId, kakaoPayUrl)
      if (linkError) return setError(linkError)
      try {
        await payLinks.mutateAsync({
          tossId: normalizeTossId(tossId) ?? '',
          kakaoPayUrl: normalizeKakaoPayUrl(kakaoPayUrl) ?? '',
        })
      } catch (err) {
        return setError(err instanceof Error ? err.message : '송금 링크를 저장하지 못했어요')
      }
    }
    action.mutate(
      { id: m.id, action: { kind: editing ? 'update' : 'create', input: { total: total as number, refs: [...picked] } } },
      {
        onSuccess: () => {
          toast.show(editing ? '정산을 고쳤어요' : '정산을 요청했어요', 'ok')
          ref.current?.close()
        },
        onError: (err) => setError(err.message),
      },
    )
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="settle-sheet-title"
      className="m-auto max-h-[88dvh] w-[min(30rem,calc(100%-2rem))] overflow-y-auto rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="sticky top-0 z-10 flex items-center justify-between gap-3 border-b border-line bg-surface px-5 py-4">
        <h2 id="settle-sheet-title" className="text-lg font-extrabold">
          {editing ? '정산 고치기' : '정산하기'}
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={() => ref.current?.close()}
          className="flex size-10 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-5 px-5 py-5" noValidate>
        <div className="flex flex-col gap-2">
          <label htmlFor="settle-total" className="text-[15px] font-semibold text-ink">
            계산한 총액
          </label>
          <div className="relative">
            <input
              id="settle-total"
              className={`${inputClass} tabular pr-10 text-right text-lg font-bold`}
              inputMode="numeric"
              autoComplete="off"
              placeholder="0"
              value={total === null ? '' : total.toLocaleString('ko-KR')}
              onChange={(e) => setTotal(parseWon(e.target.value))}
            />
            <span className="pointer-events-none absolute inset-y-0 right-3.5 flex items-center text-base text-ink-3">원</span>
          </div>
          {m.fee !== null && !editing && (
            <p className="text-sm text-ink-3">참여비 {formatWon(m.fee)} × {m.participantCount}명으로 채워 뒀어요</p>
          )}
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[15px] font-semibold text-ink">같이 나눌 원우</legend>
          <p className="-mt-1 mb-1 text-sm text-ink-3">안 온 원우는 체크를 풀어요. 나는 자동으로 들어가요</p>
          <ul className="flex flex-col divide-y divide-line rounded-xl border border-line">
            {others.map((p) => (
              <li key={p.ref}>
                <label className="flex min-h-12 cursor-pointer items-center gap-3 px-3.5 py-2">
                  <input
                    type="checkbox"
                    checked={picked.has(p.ref as number)}
                    onChange={() => toggle(p.ref as number)}
                    className="size-5 shrink-0 accent-[var(--accent)]"
                  />
                  <span className="min-w-0 text-[15px] font-semibold text-ink">
                    <PersonName person={p} />
                  </span>
                </label>
              </li>
            ))}
          </ul>
        </fieldset>

        <div className="flex items-center justify-between gap-3 rounded-xl bg-surface-2 px-4 py-3" aria-live="polite">
          <span className="min-w-0 text-sm leading-snug text-ink-2">
            나 포함 {headcount}명
            <span className="block text-[13px] text-ink-3">100원 단위 올림</span>
          </span>
          <span className="tabular shrink-0 text-lg font-extrabold whitespace-nowrap text-ink">1인 {formatWon(each)}</span>
        </div>

        {needLinks ? (
          <div className="flex flex-col gap-4 border-t border-line pt-5">
            <div>
              <p className="text-[15px] font-semibold text-ink">내 송금 링크</p>
              <p className="mt-0.5 text-sm text-ink-3">
                넣으면 원우에게 송금 버튼이 보여요. 한 번 넣으면 내 정보에 저장돼요. 계좌번호는 받지 않아요.
              </p>
            </div>
            <PayLinksFields
              tossId={tossId}
              kakaoPayUrl={kakaoPayUrl}
              onTossIdChange={setTossId}
              onKakaoPayUrlChange={setKakaoPayUrl}
            />
          </div>
        ) : (
          <p className="text-sm text-ink-3">
            내 송금 링크({[member?.tossId && '토스', member?.kakaoPayUrl && '카카오페이'].filter(Boolean).join(' · ')})가 원우에게
            보여요.{' '}
            <Link to="/me#pay-links" className="font-semibold text-accent-ink underline underline-offset-4">
              바꾸기
            </Link>
          </p>
        )}

        <FormError message={error} />

        <div className="flex gap-2.5">
          <Button variant="secondary" className="flex-1" onClick={() => ref.current?.close()}>
            닫기
          </Button>
          <Button type="submit" className="flex-1" loading={busy}>
            {editing ? '고치기' : '정산 요청'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
