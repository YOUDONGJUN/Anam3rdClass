import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { KeyRound, LogOut, ShieldCheck } from 'lucide-react'
import { api, ApiError } from '../api/client'
import { useMyMeetups } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { Button, EmptyState, ErrorState, Field, FormError } from '../components/ui'
import { buttonClass, cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'

function PasswordForm({ onDone }: { onDone: () => void }) {
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await api<void>('/api/me/password', { method: 'PUT', body: { currentPassword: current, newPassword: next } })
      toast.show('비밀번호를 바꿨어요', 'ok')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '바꾸지 못했어요')
      setPending(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 border-t border-line px-5 py-5" noValidate>
      <Field label="현재 비밀번호">
        {({ id }) => (
          <input id={id} type="password" autoComplete="current-password" className={inputClass} value={current} onChange={(e) => setCurrent(e.target.value)} />
        )}
      </Field>
      <Field label="새 비밀번호" hint="8자 이상">
        {({ id, describedBy }) => (
          <input
            id={id}
            type="password"
            autoComplete="new-password"
            aria-describedby={describedBy}
            className={inputClass}
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
        )}
      </Field>
      <FormError message={error} />
      <Button type="submit" loading={pending} disabled={!current || next.length < 8} className="self-start">
        비밀번호 바꾸기
      </Button>
    </form>
  )
}

export function MyPage() {
  const { member, signOut } = useAuth()
  const { data, isPending, isError, error, refetch } = useMyMeetups()
  const [editingPassword, setEditingPassword] = useState(false)
  const now = useNow()

  if (!member) return null

  return (
    <div className="flex flex-col gap-8">
      <section className="overflow-hidden rounded-3xl bg-board text-on-board">
        <div className="flex items-center gap-4 px-6 py-6">
          <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-accent text-xl font-black text-on-accent">
            {member.name.slice(0, 1)}
          </span>
          <div className="min-w-0">
            <p className="flex items-center gap-2 text-xl font-extrabold">
              {member.name}
              {member.role === 'ADMIN' && (
                <span className="rounded bg-exit px-1.5 py-0.5 text-[11px] font-bold text-on-exit">운영자</span>
              )}
            </p>
            <p className="mt-1 text-sm text-on-board-2">
              {member.admissionLabel} · @{member.loginId}
            </p>
          </div>
        </div>
        <p className="border-t border-board-line px-6 py-3 text-sm text-on-board-2">
          다른 사람에게는 이름이 가운데가 가려진 채로 보여요. 성사된 번개의 참여자에게만 실명이 보여요.
        </p>
      </section>

      <section>
        <h2 className="mb-3 px-1 text-[17px] font-extrabold text-ink">내 번개</h2>
        {isPending ? (
          <div className={`${cardClass} divide-y divide-line`}>
            <MeetupRowSkeleton />
            <MeetupRowSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error.message} onRetry={() => refetch()} />
        ) : data.length === 0 ? (
          <div className={cardClass}>
            <EmptyState
              title="아직 함께한 번개가 없어요"
              body="연 번개와 참여한 번개가 여기에 모여요. 무산된 번개도 나만 볼 수 있게 남아요."
              action={
                <Link to="/" className={buttonClass('secondary')}>
                  열린 번개 보기
                </Link>
              }
            />
          </div>
        ) : (
          <div className={`${cardClass} divide-y divide-line overflow-hidden`}>
            {data.map((m) => (
              <MeetupRow key={m.id} meetup={m} now={now} showDate />
            ))}
          </div>
        )}
      </section>

      <section className={cardClass}>
        <h2 className="sr-only">계정</h2>
        {member.role === 'ADMIN' && (
          <Link to="/admin" className="flex h-14 items-center gap-3 border-b border-line px-5 text-[15px] font-semibold text-ink hover:bg-surface-2">
            <ShieldCheck className="size-5 text-ink-3" aria-hidden />
            운영 페이지
          </Link>
        )}
        <button
          type="button"
          onClick={() => setEditingPassword((v) => !v)}
          aria-expanded={editingPassword}
          className="flex h-14 w-full items-center gap-3 px-5 text-left text-[15px] font-semibold text-ink hover:bg-surface-2"
        >
          <KeyRound className="size-5 text-ink-3" aria-hidden />
          비밀번호 바꾸기
        </button>
        {editingPassword && <PasswordForm onDone={() => setEditingPassword(false)} />}
        <button
          type="button"
          onClick={() => {
            if (window.confirm('로그아웃할까요?')) signOut()
          }}
          className="flex h-14 w-full items-center gap-3 border-t border-line px-5 text-left text-[15px] font-semibold text-danger-ink hover:bg-danger-soft"
        >
          <LogOut className="size-5" aria-hidden />
          로그아웃
        </button>
      </section>
    </div>
  )
}
