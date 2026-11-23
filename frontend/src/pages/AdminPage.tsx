import { useState, type FormEvent } from 'react'
import { Ban, Copy, KeyRound, RotateCcw } from 'lucide-react'
import {
  useAdminMeetups,
  useAdminMemberAction,
  useAdminMembers,
  useAdminSettings,
  useAdminStats,
  useChangeInviteCode,
  useResetPassword,
} from '../api/queries'
import type { AdminMember } from '../api/types'
import { MeetupRow } from '../components/MeetupRow'
import { Button, EmptyState, ErrorState, Field, FormError, Segmented, Spinner } from '../components/ui'
import { cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { formatRelative } from '../lib/time'

type Tab = 'overview' | 'members' | 'meetups'

function Overview() {
  const stats = useAdminStats()
  const settings = useAdminSettings()
  const change = useChangeInviteCode()
  const toast = useToast()
  const [code, setCode] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const current = settings.data?.inviteCode ?? ''
  const value = code ?? current

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await change.mutateAsync(value)
      setCode(null)
      toast.show('입장 코드를 바꿨어요', 'ok')
    } catch (err) {
      setError(err instanceof Error ? err.message : '바꾸지 못했어요')
    }
  }

  const s = stats.data
  const items = s
    ? [
        { label: '회원', value: s.members },
        { label: '번개', value: s.meetups },
        { label: '성사', value: s.confirmedMeetups },
        { label: '공지', value: s.notices },
      ]
    : []

  return (
    <div className="flex flex-col gap-6">
      <section className={`${cardClass} grid grid-cols-2 divide-line sm:grid-cols-4 sm:divide-x`}>
        {stats.isPending ? (
          <div className="col-span-full">
            <Spinner />
          </div>
        ) : (
          items.map((it) => (
            <div key={it.label} className="px-5 py-4">
              <p className="text-sm text-ink-3">{it.label}</p>
              <p className="tabular mt-1 text-2xl font-extrabold text-ink">{it.value}</p>
            </div>
          ))
        )}
      </section>

      <section className={`${cardClass} px-5 py-5`}>
        <h2 className="text-[17px] font-extrabold text-ink">입장 코드</h2>
        <p className="mt-1 text-sm leading-relaxed text-ink-2">
          가입할 때 필요한 코드예요. 단톡방 공지에만 올리고, 밖으로 새면 바꿔 주세요. 이미 가입한 사람에겐 영향이 없어요.
        </p>
        <form onSubmit={submit} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" noValidate>
          <div className="flex-1">
            <Field label="현재 코드">
              {({ id }) => (
                <input
                  id={id}
                  className={`${inputClass} tabular font-semibold`}
                  value={value}
                  autoComplete="off"
                  onChange={(e) => setCode(e.target.value)}
                />
              )}
            </Field>
          </div>
          <Button type="submit" loading={change.isPending} disabled={!value.trim() || value === current}>
            바꾸기
          </Button>
        </form>
        <div className="mt-3">
          <FormError message={error} />
        </div>
      </section>
    </div>
  )
}

function MemberItem({ m }: { m: AdminMember }) {
  const action = useAdminMemberAction()
  const reset = useResetPassword()
  const toast = useToast()
  const [temp, setTemp] = useState<string | null>(null)
  const banned = m.status === 'BANNED'

  return (
    <li className="px-5 py-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <p className="text-base font-bold text-ink">{m.name}</p>
        <span className="text-sm text-ink-3">
          {m.admissionLabel} · @{m.loginId}
        </span>
        {m.role === 'ADMIN' && <span className="rounded bg-board px-1.5 py-0.5 text-[11px] font-bold text-on-board">운영자</span>}
        {banned && <span className="rounded bg-danger-soft px-1.5 py-0.5 text-[11px] font-bold text-danger-ink">차단됨</span>}
        <span className="ml-auto text-[13px] text-ink-3">가입 {formatRelative(m.createdAt)}</span>
      </div>
      {m.role !== 'ADMIN' && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button
            size="sm"
            variant={banned ? 'secondary' : 'danger'}
            loading={action.isPending}
            onClick={() => {
              if (!banned && !window.confirm(`${m.name} 님을 차단할까요? 바로 로그아웃되고 다시 로그인할 수 없어요.`)) return
              action.mutate(
                { id: m.id, action: banned ? 'unban' : 'ban' },
                {
                  onSuccess: () => toast.show(banned ? '차단을 풀었어요' : '차단했어요', 'ok'),
                  onError: (e) => toast.show(e.message, 'error'),
                },
              )
            }}
          >
            {banned ? <RotateCcw className="size-4" aria-hidden /> : <Ban className="size-4" aria-hidden />}
            {banned ? '차단 풀기' : '차단'}
          </Button>
          <Button
            size="sm"
            variant="secondary"
            loading={reset.isPending}
            onClick={() => {
              if (!window.confirm(`${m.name} 님의 비밀번호를 임시 비밀번호로 바꿀까요?`)) return
              reset.mutate(m.id, {
                onSuccess: (d) => setTemp(d.temporaryPassword),
                onError: (e) => toast.show(e.message, 'error'),
              })
            }}
          >
            <KeyRound className="size-4" aria-hidden />
            임시 비밀번호
          </Button>
        </div>
      )}
      {temp && (
        <div className="mt-3 flex flex-wrap items-center gap-3 rounded-xl bg-exit/25 px-4 py-3 text-sm text-ink">
          <span>임시 비밀번호</span>
          <code className="tabular rounded bg-surface px-2 py-1 text-base font-bold tracking-wider">{temp}</code>
          <Button
            size="sm"
            variant="ghost"
            onClick={() =>
              navigator.clipboard
                .writeText(temp)
                .then(() => toast.show('복사했어요. 본인에게 직접 전달해 주세요', 'ok'))
                .catch(() => toast.show('복사하지 못했어요', 'error'))
            }
          >
            <Copy className="size-4" aria-hidden />
            복사
          </Button>
        </div>
      )}
    </li>
  )
}

function Members() {
  const { data, isPending, isError, error, refetch } = useAdminMembers()
  const [query, setQuery] = useState('')
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  const q = query.trim().toLowerCase()
  const list = q ? data.filter((m) => m.name.includes(q) || m.loginId.includes(q) || m.admissionLabel.includes(q)) : data

  return (
    <div className="flex flex-col gap-3">
      <input
        type="search"
        aria-label="회원 검색"
        placeholder="이름, 아이디, 학번으로 찾기"
        className={inputClass}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <p className="px-1 text-sm text-ink-3">회원 {data.length}명</p>
      {list.length === 0 ? (
        <div className={cardClass}>
          <EmptyState title="찾는 회원이 없어요" />
        </div>
      ) : (
        <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
          {list.map((m) => (
            <MemberItem key={m.id} m={m} />
          ))}
        </ul>
      )}
    </div>
  )
}

function Meetups() {
  const { data, isPending, isError, error, refetch } = useAdminMeetups()
  const now = useNow()
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  if (data.length === 0)
    return (
      <div className={cardClass}>
        <EmptyState title="아직 번개가 없어요" />
      </div>
    )
  return (
    <div className="flex flex-col gap-3">
      <p className="px-1 text-sm text-ink-3">운영자에게는 모든 번개가 실명으로 보여요. 무산·취소된 번개도 포함돼요.</p>
      <div className={`${cardClass} divide-y divide-line overflow-hidden`}>
        {data.map((m) => (
          <MeetupRow key={m.id} meetup={m} now={now} showDate />
        ))}
      </div>
    </div>
  )
}

export function AdminPage() {
  const [tab, setTab] = useState<Tab>('overview')
  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">운영</h1>
          <p className="mt-1 text-[15px] text-ink-2">회원 관리, 입장 코드, 전체 번개</p>
        </div>
        <Segmented
          label="운영 메뉴"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'overview', label: '현황' },
            { value: 'members', label: '회원' },
            { value: 'meetups', label: '번개' },
          ]}
        />
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'members' && <Members />}
      {tab === 'meetups' && <Meetups />}
    </div>
  )
}
