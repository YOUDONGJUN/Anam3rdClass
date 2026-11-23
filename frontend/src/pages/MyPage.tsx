import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ChevronRight, KeyRound, LogOut, ShieldCheck, UserPen } from 'lucide-react'
import { api, ApiError } from '../api/client'
import { useMyMeetups, useUpdateProfile } from '../api/queries'
import type { Department, Member } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AffiliationFields } from '../components/AffiliationFields'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { AdminTag } from '../components/PersonName'
import { Button, EmptyState, ErrorState, Field, FormError } from '../components/ui'
import { buttonClass, cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { affiliation } from '../lib/person'

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

/** 이름·기수·학과 수정 */
function ProfileForm({ member, onDone }: { member: Member; onDone: () => void }) {
  const { updateMember } = useAuth()
  const update = useUpdateProfile()
  const toast = useToast()
  const [name, setName] = useState(member.name)
  const [cohort, setCohort] = useState(String(member.cohort))
  const [department, setDepartment] = useState<Department | ''>(member.department ?? '')
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!department) return setError('학과를 선택해 주세요')
    setError(null)
    try {
      const updated = await update.mutateAsync({ name: name.trim(), cohort: Number(cohort), department })
      updateMember(updated)
      toast.show('내 정보를 고쳤어요', 'ok')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '고치지 못했어요')
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 border-t border-line px-5 py-5" noValidate>
      <Field label="이름">
        {({ id }) => <input id={id} className={inputClass} value={name} onChange={(e) => setName(e.target.value)} />}
      </Field>
      <AffiliationFields cohort={cohort} department={department} onCohortChange={setCohort} onDepartmentChange={setDepartment} />
      <FormError message={error} />
      <div className="flex gap-2">
        <Button type="submit" loading={update.isPending} disabled={!name.trim() || !cohort || !department}>
          저장
        </Button>
        <Button variant="ghost" onClick={onDone}>
          취소
        </Button>
      </div>
    </form>
  )
}

export function MyPage() {
  const { member, signOut } = useAuth()
  const { data, isPending, isError, error, refetch } = useMyMeetups()
  const [editingPassword, setEditingPassword] = useState(false)
  const [editingProfile, setEditingProfile] = useState(false)
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
                <AdminTag />
              )}
            </p>
            <p className="mt-1 text-sm text-on-board-2">
              {affiliation(member.cohortLabel, member.departmentLabel)} · @{member.loginId}
            </p>
          </div>
        </div>
        <p className="border-t border-board-line px-6 py-3 text-sm text-on-board-2">
          다른 사람에게는 이름이 가운데가 가려진 채로 보여요. 성사된 3교시의 참여자에게만 실명이 보여요.
        </p>
      </section>

      {!member.department && !editingProfile && (
        <button
          type="button"
          onClick={() => setEditingProfile(true)}
          className="flex items-center justify-between gap-3 rounded-2xl bg-exit/25 px-5 py-4 text-left text-[15px] font-semibold text-ink"
        >
          학과를 아직 고르지 않았어요. 지금 고르기
          <ChevronRight className="size-5 shrink-0" aria-hidden />
        </button>
      )}

      <section>
        <h2 className="mb-3 px-1 text-[17px] font-extrabold text-ink">내 3교시</h2>
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
              title="아직 함께한 3교시가 없어요"
              body="내가 연 3교시와 참여한 3교시가 여기에 모여요. 무산된 것도 나만 볼 수 있게 남아요."
              action={
                <Link to="/" className={buttonClass('secondary')}>
                  열린 3교시 보기
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
            관리 페이지
          </Link>
        )}
        <button
          type="button"
          onClick={() => setEditingProfile((v) => !v)}
          aria-expanded={editingProfile}
          className="flex h-14 w-full items-center gap-3 border-b border-line px-5 text-left text-[15px] font-semibold text-ink hover:bg-surface-2"
        >
          <UserPen className="size-5 text-ink-3" aria-hidden />
          이름·기수·학과 고치기
        </button>
        {editingProfile && (
          <div className="border-b border-line">
            <ProfileForm member={member} onDone={() => setEditingProfile(false)} />
          </div>
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
