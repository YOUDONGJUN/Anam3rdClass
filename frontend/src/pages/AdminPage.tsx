import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowDown,
  ArrowUp,
  Ban,
  Check,
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  Pencil,
  RotateCcw,
  UserCheck,
  UserMinus,
  X,
} from 'lucide-react'
import {
  useAdminMeetups,
  useAdminMemberAction,
  useAdminMembers,
  useAdminReports,
  useAdminSchools,
  useAdminStats,
  useAssignEmail,
  useChangeRole,
  useResolveReport,
  useSchoolAdmin,
  useUpdateMemberProfile,
} from '../api/queries'
import type { AdminMember, Department, Report, School } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AffiliationFields } from '../components/AffiliationFields'
import { MeetupRow } from '../components/MeetupRow'
import { RoleTag } from '../components/PersonName'
import { REASON_LABEL } from '../lib/report'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, EmptyState, ErrorState, Field, FormError, Segmented, Spinner } from '../components/ui'
import { cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import {
  countByCohort,
  countByDepartment,
  countBySchool,
  EMPTY_FILTERS,
  filterMembers,
  sortMembers,
  type Bucket,
  type MemberFilters,
  type SortDir,
  type SortKey,
} from '../lib/members'
import { affiliation, ROLE_LABEL } from '../lib/person'
import { formatRelative } from '../lib/time'

type Tab = 'overview' | 'members' | 'reports' | 'meetups' | 'schools'

const PAGE_SIZE = 20

function useIsOwner() {
  return useAuth().member?.role === 'OWNER'
}

// ---------------- 현황 ----------------

function Overview() {
  const stats = useAdminStats()
  const s = stats.data
  const items = s
    ? [
        { label: '원우', value: s.members },
        { label: '3교시', value: s.meetups },
        { label: '성사', value: s.confirmedMeetups },
        { label: '공지', value: s.notices },
        { label: '처리 안 된 신고', value: s.openReports },
      ]
    : []

  return (
    <section className={`${cardClass} grid grid-cols-2 divide-line sm:grid-cols-5 sm:divide-x`}>
      {stats.isPending ? (
        <div className="col-span-full">
          <Spinner />
        </div>
      ) : stats.isError ? (
        <div className="col-span-full">
          <ErrorState message={stats.error.message} onRetry={() => stats.refetch()} />
        </div>
      ) : (
        items.map((it) => (
          <div key={it.label} className="px-5 py-4">
            <p className="text-sm text-ink-3">{it.label}</p>
            <p className="tabular mt-1 text-2xl font-extrabold text-ink">{it.value.toLocaleString('ko-KR')}</p>
          </div>
        ))
      )}
    </section>
  )
}

// ---------------- 원우: 통계 ----------------

/**
 * 가로 막대 목록 (한 계열이라 범례 없이 제목이 무엇을 세는지 말한다).
 * 막대는 기준선에서 자라고 끝만 둥글다. 값은 막대 끝에 글자 색으로.
 */
function BarList({ title, buckets, total }: { title: string; buckets: Bucket[]; total: number }) {
  const max = Math.max(1, ...buckets.map((b) => b.count))
  return (
    <section className="min-w-0">
      <h3 className="mb-3 text-sm font-bold text-ink-2">{title}</h3>
      {buckets.length === 0 ? (
        <p className="text-sm text-ink-3">해당하는 원우가 없어요</p>
      ) : (
        <ul className="flex flex-col gap-1">
          {buckets.map((b) => {
            const share = total > 0 ? Math.round((b.count / total) * 100) : 0
            return (
              <li
                key={b.key}
                title={`${b.label} · ${b.count}명 (${share}%)`}
                className="group grid grid-cols-[minmax(0,7.5rem)_minmax(0,1fr)] items-center gap-3 rounded-md py-1 hover:bg-surface-2"
              >
                <span className="truncate text-sm text-ink-2">{b.label}</span>
                <span className="flex min-w-0 items-center gap-2">
                  <span className="relative h-2.5 flex-1" aria-hidden>
                    <span
                      className="absolute inset-y-0 left-0 rounded-r-[4px] bg-chart-bar"
                      style={{ width: `${(b.count / max) * 100}%`, minWidth: 2 }}
                    />
                  </span>
                  <span className="tabular w-14 shrink-0 text-right text-sm font-semibold text-ink">
                    {b.count}
                    <span className="ml-1 text-xs font-medium text-ink-3">{share}%</span>
                  </span>
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function MemberStats({ list: all, multiSchool }: { list: AdminMember[]; multiSchool: boolean }) {
  // 탈퇴한 원우는 세지 않는다 (현황 탭과 같은 기준)
  const list = all.filter((m) => m.status !== 'WITHDRAWN')
  const active = list.filter((m) => m.status === 'ACTIVE').length
  const unlinked = list.filter((m) => !m.kakaoLinked).length
  const tiles = [
    { label: '원우', value: list.length },
    { label: '활동 중', value: active },
    { label: '차단', value: list.filter((m) => m.status === 'BANNED').length },
    { label: '카카오 미연결', value: unlinked },
  ]
  return (
    <section className={`${cardClass} flex flex-col gap-6 px-5 py-5`} aria-label="원우 통계">
      <div className="grid grid-cols-2 gap-y-3 sm:grid-cols-4">
        {tiles.map((t) => (
          <div key={t.label}>
            <p className="text-sm text-ink-3">{t.label}</p>
            <p className="tabular mt-0.5 text-xl font-extrabold text-ink">{t.value.toLocaleString('ko-KR')}</p>
          </div>
        ))}
      </div>
      <div className="grid gap-6 sm:grid-cols-2">
        {multiSchool && <BarList title="학교별 원우" buckets={countBySchool(list)} total={list.length} />}
        <BarList title="기수별 원우" buckets={countByCohort(list)} total={list.length} />
        <BarList title="학과별 원우" buckets={countByDepartment(list)} total={list.length} />
      </div>
    </section>
  )
}

// ---------------- 원우: 거르기 ----------------

function FilterSelect({
  label,
  value,
  onChange,
  children,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  children: ReactNode
}) {
  return (
    <label className="flex min-w-0 flex-col gap-1">
      <span className="text-xs font-semibold text-ink-3">{label}</span>
      <select className={`${inputClass} h-11`} value={value} onChange={(e) => onChange(e.target.value)}>
        {children}
      </select>
    </label>
  )
}

function MemberFilterBar({
  list,
  filters,
  onChange,
}: {
  list: AdminMember[]
  filters: MemberFilters
  onChange: (next: MemberFilters) => void
}) {
  const schools = useMemo(() => countBySchool(list), [list])
  const cohorts = useMemo(() => countByCohort(list), [list])
  const departments = useMemo(() => countByDepartment(list), [list])
  const set = (patch: Partial<MemberFilters>) => onChange({ ...filters, ...patch })

  return (
    <div className="flex flex-col gap-3">
      <input
        type="search"
        aria-label="원우 검색"
        placeholder="이름, 이메일, 기수, 학과, 학교로 찾기"
        className={inputClass}
        value={filters.q}
        onChange={(e) => set({ q: e.target.value })}
      />
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
        <FilterSelect
          label="학교"
          value={String(filters.school)}
          onChange={(v) => set({ school: v === 'all' ? v : Number(v) })}
        >
          <option value="all">전체</option>
          {schools.map((s) => (
            <option key={s.key} value={s.key}>
              {s.label}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect
          label="기수"
          value={String(filters.cohort)}
          onChange={(v) => set({ cohort: v })}
        >
          <option value="all">전체</option>
          {cohorts.map((c) => (
            <option key={c.key} value={c.key}>
              {c.label}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect
          label="학과"
          value={String(filters.department)}
          onChange={(v) => set({ department: v === 'all' || v === 'none' ? v : Number(v) })}
        >
          <option value="all">전체</option>
          {departments.map((d) => (
            <option key={d.key} value={d.key}>
              {d.label}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect label="직책" value={filters.role} onChange={(v) => set({ role: v as MemberFilters['role'] })}>
          <option value="all">전체</option>
          <option value="OWNER">{ROLE_LABEL.OWNER}</option>
          <option value="MANAGER">{ROLE_LABEL.MANAGER}</option>
          <option value="USER">{ROLE_LABEL.USER}</option>
        </FilterSelect>
        <FilterSelect label="상태" value={filters.status} onChange={(v) => set({ status: v as MemberFilters['status'] })}>
          <option value="all">전체</option>
          <option value="ACTIVE">활동 중</option>
          <option value="BANNED">차단됨</option>
          <option value="WITHDRAWN">탈퇴</option>
        </FilterSelect>
      </div>
    </div>
  )
}

// ---------------- 원우: 그리드 ----------------

function StatusBadges({ m }: { m: AdminMember }) {
  return (
    <>
      {m.role !== 'USER' && <RoleTag role={m.role} />}
      {m.status === 'BANNED' && (
        <span className="shrink-0 rounded bg-danger-soft px-1.5 py-0.5 text-[11px] leading-none font-bold text-danger-ink">
          차단됨
        </span>
      )}
      {m.status === 'WITHDRAWN' && (
        <span className="shrink-0 rounded bg-surface-2 px-1.5 py-0.5 text-[11px] leading-none font-bold text-ink-3">
          탈퇴
        </span>
      )}
      {!m.kakaoLinked && m.status !== 'WITHDRAWN' && (
        <span className="shrink-0 rounded bg-surface-2 px-1.5 py-0.5 text-[11px] leading-none font-bold text-ink-3">
          카카오 미연결
        </span>
      )}
    </>
  )
}

function SortHeader({
  label,
  sortKey,
  sort,
  onSort,
  className = '',
}: {
  label: string
  sortKey: SortKey
  sort: { key: SortKey; dir: SortDir }
  onSort: (key: SortKey) => void
  className?: string
}) {
  const active = sort.key === sortKey
  const Icon = sort.dir === 'asc' ? ArrowUp : ArrowDown
  return (
    <th
      scope="col"
      aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={`px-3 py-2.5 text-left text-xs font-semibold text-ink-3 ${className}`}
    >
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={`inline-flex items-center gap-1 rounded hover:text-ink ${active ? 'text-ink' : ''}`}
      >
        {label}
        {active && <Icon className="size-3.5" aria-hidden />}
      </button>
    </th>
  )
}

function MembersGrid({ list, onOpen }: { list: AdminMember[]; onOpen: (m: AdminMember) => void }) {
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: 'createdAt', dir: 'desc' })
  const [page, setPage] = useState(0)
  const sorted = useMemo(() => sortMembers(list, sort.key, sort.dir), [list, sort])
  const pages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
  const current = Math.min(page, pages - 1) // 거르기로 목록이 줄면 마지막 쪽으로
  const rows = sorted.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE)

  function onSort(key: SortKey) {
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: key === 'name' ? 'asc' : 'desc' }))
  }

  if (list.length === 0) {
    return (
      <div className={cardClass}>
        <EmptyState title="찾는 원우가 없어요" />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {/* 데스크톱: 표 */}
      <div className={`${cardClass} hidden overflow-x-auto md:block`}>
        <table className="w-full min-w-[640px] text-sm">
          <thead className="border-b border-line">
            <tr>
              <SortHeader label="이름" sortKey="name" sort={sort} onSort={onSort} />
              <SortHeader label="소속" sortKey="cohort" sort={sort} onSort={onSort} />
              <SortHeader label="학교" sortKey="school" sort={sort} onSort={onSort} />
              <th scope="col" className="px-3 py-2.5 text-left text-xs font-semibold text-ink-3">
                이메일
              </th>
              <SortHeader label="가입" sortKey="createdAt" sort={sort} onSort={onSort} className="text-right" />
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((m) => (
              <tr key={m.id} className="hover:bg-surface-2/70">
                <td className="max-w-56 px-3 py-2.5">
                  <span className="flex min-w-0 flex-wrap items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => onOpen(m)}
                      className="truncate font-semibold text-ink underline-offset-4 hover:underline"
                    >
                      {m.name}
                    </button>
                    <StatusBadges m={m} />
                  </span>
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap text-ink-2">
                  {affiliation(m.cohortLabel, m.departmentShortLabel)}
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap">
                  <SchoolBadge school={m.school} />
                </td>
                <td className="max-w-48 truncate px-3 py-2.5 text-ink-2">{m.email ?? '–'}</td>
                <td className="px-3 py-2.5 text-right whitespace-nowrap text-ink-3">{formatRelative(m.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 모바일: 카드 */}
      <ul className={`${cardClass} divide-y divide-line overflow-hidden md:hidden`}>
        {rows.map((m) => (
          <li key={m.id}>
            <button type="button" onClick={() => onOpen(m)} className="flex w-full flex-col gap-1 px-4 py-3 text-left hover:bg-surface-2">
              <span className="flex flex-wrap items-center gap-1.5">
                <span className="font-bold text-ink">{m.name}</span>
                <SchoolBadge school={m.school} />
                <StatusBadges m={m} />
              </span>
              <span className="text-sm text-ink-2">{affiliation(m.cohortLabel, m.departmentShortLabel)}</span>
              {m.email && <span className="truncate text-[13px] text-ink-3">{m.email}</span>}
            </button>
          </li>
        ))}
      </ul>

      {pages > 1 && (
        <nav aria-label="쪽 넘기기" className="flex items-center justify-between gap-3 px-1">
          <span className="tabular text-sm text-ink-3">
            {current * PAGE_SIZE + 1}–{Math.min(sorted.length, (current + 1) * PAGE_SIZE)} / {sorted.length}명
          </span>
          <span className="flex gap-2">
            <Button variant="secondary" size="sm" disabled={current === 0} onClick={() => setPage(current - 1)}>
              <ChevronLeft className="size-4" aria-hidden />
              이전
            </Button>
            <Button variant="secondary" size="sm" disabled={current >= pages - 1} onClick={() => setPage(current + 1)}>
              다음
              <ChevronRight className="size-4" aria-hidden />
            </Button>
          </span>
        </nav>
      )}
    </div>
  )
}

// ---------------- 원우: 관리 창 ----------------

function MemberDialog({ member: m, onClose }: { member: AdminMember; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const isOwner = useIsOwner()
  const toast = useToast()
  const action = useAdminMemberAction()
  const changeRole = useChangeRole()
  const updateProfile = useUpdateMemberProfile()
  const assignEmail = useAssignEmail()

  const [name, setName] = useState(m.name)
  const [cohort, setCohort] = useState(String(m.cohort))
  const [departmentId, setDepartmentId] = useState<number | null>(m.departmentId)
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  // 교수는 자기 학교 원우만 관리한다. 교수·대학원장 계정은 대학원장만. 탈퇴한 원우는 관리할 것이 없다
  const withdrawn = m.status === 'WITHDRAWN'
  const canManage = !withdrawn && (isOwner || m.role === 'USER')
  const banned = m.status === 'BANNED'

  const fail = (e: Error) => setError(e.message)

  function saveProfile(e: FormEvent) {
    e.preventDefault()
    if (!departmentId) return setError('학과를 선택해 주세요')
    setError(null)
    updateProfile.mutate(
      { id: m.id, name: name.trim(), cohort: Number(cohort), departmentId },
      { onSuccess: () => toast.show('프로필을 고쳤어요', 'ok'), onError: fail },
    )
  }

  function saveEmail(e: FormEvent) {
    e.preventDefault()
    setError(null)
    assignEmail.mutate(
      { id: m.id, email: email.trim() },
      {
        onSuccess: () => {
          setEmail('')
          toast.show('학교 이메일을 넣어 뒀어요. 본인이 그 이메일로 인증하면 이 계정으로 이어져요', 'ok')
        },
        onError: fail,
      },
    )
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="member-dialog-title"
      className="m-auto max-h-[88dvh] w-[min(32rem,calc(100%-2rem))] overflow-y-auto rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b border-line bg-surface px-5 py-4">
        <div className="min-w-0">
          <h2 id="member-dialog-title" className="flex flex-wrap items-center gap-1.5 text-lg font-extrabold">
            {m.name}
            <SchoolBadge school={m.school} />
            <StatusBadges m={m} />
          </h2>
          <p className="mt-0.5 text-sm text-ink-2">{affiliation(m.cohortLabel, m.departmentLabel)}</p>
          <p className="truncate text-sm text-ink-3">
            {m.email ?? '학교 이메일 없음'} · 가입 {formatRelative(m.createdAt)}
          </p>
        </div>
        <button
          type="button"
          aria-label="닫기"
          onClick={() => ref.current?.close()}
          className="flex size-10 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>

      <div className="flex flex-col gap-6 px-5 py-5">
        {withdrawn && (
          <p className="rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2">
            탈퇴한 원우예요. 이름·이메일·카카오 연결은 지워졌어요.
          </p>
        )}
        {!withdrawn && !canManage && (
          <p className="rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2">
            {ROLE_LABEL[m.role]} 계정은 대학원장만 관리할 수 있어요.
          </p>
        )}

        {canManage && (
          <form onSubmit={saveProfile} className="flex flex-col gap-4" noValidate>
            <h3 className="text-[15px] font-extrabold">이름·기수·학과 고치기</h3>
            <p className="-mt-2 text-sm text-ink-3">원우는 직접 못 바꿔요. 요청을 받으면 여기서 고쳐 주세요.</p>
            <Field label="이름">
              {({ id }) => <input id={id} className={inputClass} value={name} onChange={(e) => setName(e.target.value)} />}
            </Field>
            <AffiliationFields
              schoolId={m.school.id}
              cohort={cohort}
              departmentId={departmentId}
              onCohortChange={setCohort}
              onDepartmentChange={setDepartmentId}
            />
            <Button type="submit" loading={updateProfile.isPending} disabled={!name.trim() || !cohort || !departmentId} className="self-start">
              저장
            </Button>
          </form>
        )}

        {isOwner && !m.kakaoLinked && !withdrawn && (
          <form onSubmit={saveEmail} className="flex flex-col gap-3 border-t border-line pt-5" noValidate>
            <h3 className="text-[15px] font-extrabold">학교 이메일 넣어 두기</h3>
            <p className="-mt-1 text-sm leading-relaxed text-ink-3">
              아직 카카오로 로그인하지 않은 예전 회원이에요. 본인 학교 이메일을 넣어 두면, 본인이 카카오로 로그인해 그
              이메일을 인증할 때 이 계정(3교시 기록 포함)으로 이어져요.
            </p>
            <div className="flex gap-2">
              <input
                type="email"
                inputMode="email"
                aria-label="학교 이메일"
                className={`${inputClass} min-w-0 flex-1`}
                placeholder={m.email ?? 'hong@korea.ac.kr'}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              <Button type="submit" variant="secondary" loading={assignEmail.isPending} disabled={!email.trim()} className="shrink-0">
                넣기
              </Button>
            </div>
          </form>
        )}

        {canManage && m.role !== 'OWNER' && (
          <div className="flex flex-wrap gap-2 border-t border-line pt-5">
            {isOwner && !banned && (
              <Button
                variant="secondary"
                size="sm"
                loading={changeRole.isPending}
                onClick={() => {
                  const next = m.role === 'MANAGER' ? 'USER' : 'MANAGER'
                  const message =
                    next === 'MANAGER'
                      ? `${m.name} 님을 ${m.school.shortName} 교수로 임명할까요? ${m.school.shortName} 원우 차단·프로필 수정, 3교시 취소, 공지·신고 관리를 할 수 있게 돼요.`
                      : `${m.name} 님의 교수 직책을 내려놓을까요?`
                  if (!window.confirm(message)) return
                  changeRole.mutate(
                    { id: m.id, role: next },
                    {
                      onSuccess: () => toast.show(next === 'MANAGER' ? '교수로 임명했어요' : '원우로 돌아갔어요', 'ok'),
                      onError: fail,
                    },
                  )
                }}
              >
                {m.role === 'MANAGER' ? <UserMinus className="size-4" aria-hidden /> : <UserCheck className="size-4" aria-hidden />}
                {m.role === 'MANAGER' ? '교수 해임' : '교수 임명'}
              </Button>
            )}
            {canManage && (
              <Button
                size="sm"
                variant={banned ? 'secondary' : 'danger'}
                loading={action.isPending}
                onClick={() => {
                  if (!banned && !window.confirm(`${m.name} 님의 이용을 막을까요?

• 바로 로그아웃되고 다시 로그인할 수 없어요
• 그 사람이 연, 아직 모이기 전인 3교시는 취소되고 모집 중인 3교시에서는 빠져요`)) return
                  action.mutate(
                    { id: m.id, action: banned ? 'unban' : 'ban' },
                    { onSuccess: () => toast.show(banned ? '차단을 풀었어요' : '차단했어요', 'ok'), onError: fail },
                  )
                }}
              >
                {banned ? <RotateCcw className="size-4" aria-hidden /> : <Ban className="size-4" aria-hidden />}
                {banned ? '차단 풀기' : '차단'}
              </Button>
            )}
          </div>
        )}

        <FormError message={error} />
      </div>
    </dialog>
  )
}

function Members() {
  const { data, isPending, isError, error, refetch } = useAdminMembers()
  const isOwner = useIsOwner()
  const [filters, setFilters] = useState<MemberFilters>(EMPTY_FILTERS)
  const [openId, setOpenId] = useState<number | null>(null)

  const filtered = useMemo(() => (data ? filterMembers(data, filters) : []), [data, filters])

  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />

  const multiSchool = new Set(data.map((m) => m.school.id)).size > 1
  const opened = openId === null ? undefined : data.find((m) => m.id === openId)

  return (
    <div className="flex flex-col gap-5">
      <MemberFilterBar list={data} filters={filters} onChange={setFilters} />
      <MemberStats list={filtered} multiSchool={multiSchool} />
      {!isOwner && (
        <p className="px-1 text-sm text-ink-3">우리 학교 원우만 보여요. 교수에게는 원우 이메일이 가려져 보여요.</p>
      )}
      <MembersGrid list={filtered} onOpen={(m) => setOpenId(m.id)} />
      {/* 목록이 새로 고쳐져도 같은 원우를 계속 보여준다 */}
      {opened && <MemberDialog key={opened.id} member={opened} onClose={() => setOpenId(null)} />}
    </div>
  )
}

// ---------------- 3교시 ----------------

function Meetups() {
  const { data, isPending, isError, error, refetch } = useAdminMeetups()
  const isOwner = useIsOwner()
  const now = useNow()
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  if (data.length === 0)
    return (
      <div className={cardClass}>
        <EmptyState title="아직 3교시가 없어요" />
      </div>
    )
  return (
    <div className="flex flex-col gap-3">
      <p className="px-1 text-sm text-ink-3">
        {isOwner ? '다섯 학교의 3교시가 모두 보여요. ' : '우리 학교 원우가 연 3교시가 보여요. '}
        무산·취소된 것과 다른 기수 전용 3교시도 보여요.{' '}
        {isOwner ? '대학원장에게는 모두 실명으로 보여요.' : '교수에게도 이름은 원우와 똑같이 가려져요.'}
      </p>
      <div className={`${cardClass} divide-y divide-line overflow-hidden`}>
        {data.map((m) => (
          <MeetupRow key={m.id} meetup={m} now={now} showDate />
        ))}
      </div>
    </div>
  )
}

// ---------------- 신고 ----------------

const TARGET_LABEL: Record<Report['targetType'], string> = { MEETUP: '3교시', NOTICE: '공지', MEMBER: '사람' }

function ReportCard({ r }: { r: Report }) {
  const resolve = useResolveReport()
  const action = useAdminMemberAction()
  const toast = useToast()
  const isOwner = useIsOwner()
  const open = r.status === 'OPEN'
  // 대학원장은 아무도 막을 수 없고, 교수는 다른 교수를 막을 수 없다
  const canBan = r.memberRole === 'USER' || (r.memberRole === 'MANAGER' && isOwner)
  return (
    <li className={`flex flex-col gap-2.5 px-5 py-4 ${open ? '' : 'opacity-70'}`}>
      <div className="flex flex-wrap items-center gap-1.5">
        <span
          className={`rounded px-1.5 py-0.5 text-[11px] leading-none font-bold ${
            open ? 'bg-danger-soft text-danger-ink' : 'bg-surface-2 text-ink-3'
          }`}
        >
          {open ? '처리 전' : '처리 완료'}
        </span>
        <span className="rounded bg-board px-1.5 py-0.5 text-[11px] leading-none font-bold text-on-board">
          {TARGET_LABEL[r.targetType]}
        </span>
        <span className="text-[15px] font-bold text-ink">{REASON_LABEL[r.reason]}</span>
        {r.school && <SchoolBadge school={r.school} />}
        <span className="ml-auto text-sm text-ink-3">{formatRelative(r.createdAt)}</span>
      </div>
      <div className="flex flex-col gap-0.5 text-sm text-ink-2">
        {r.meetupId && (
          <p>
            3교시{' '}
            <Link to={`/meetups/${r.meetupId}`} className="font-semibold text-accent-ink underline underline-offset-4">
              {r.meetupTitle}
            </Link>
          </p>
        )}
        {r.targetType === 'NOTICE' &&
          (r.noticeId ? (
            <p>
              공지{' '}
              <Link to={`/notices/${r.noticeId}`} className="font-semibold text-accent-ink underline underline-offset-4">
                {r.noticeTitle}
              </Link>
            </p>
          ) : (
            <p>지워진 공지</p>
          ))}
        {r.memberName && (
          <p>
            {r.targetType === 'MEMBER' ? '신고된 사람' : r.targetType === 'MEETUP' ? '연 사람' : '쓴 사람'}{' '}
            <b className="text-ink">{r.memberName}</b>
            {r.memberAffiliation && ` · ${r.memberAffiliation}`}
            {r.memberBanned && ' · 차단됨'}
          </p>
        )}
        {r.targetSnapshot && (
          <details className="mt-1 rounded-lg border border-line px-3 py-2">
            <summary className="cursor-pointer text-[13px] font-semibold text-ink-3">신고 당시 내용</summary>
            <p className="mt-1.5 whitespace-pre-wrap text-ink">{r.targetSnapshot}</p>
          </details>
        )}
        {r.detail && <p className="mt-1 rounded-lg bg-surface-2 px-3 py-2 whitespace-pre-wrap text-ink">{r.detail}</p>}
      </div>
      <div className="flex flex-wrap gap-2">
        {open && r.memberId && !r.memberBanned && canBan && (
          <Button
            size="sm"
            variant="danger"
            loading={action.isPending}
            onClick={() => {
              if (!window.confirm(`${r.memberName} 님의 이용을 막을까요?

• 바로 로그아웃되고 다시 로그인할 수 없어요
• 그 사람이 연, 아직 모이기 전인 3교시는 취소되고 모집 중인 3교시에서는 빠져요`)) return
              action.mutate(
                { id: r.memberId!, action: 'ban' },
                { onSuccess: () => toast.show('차단했어요', 'ok'), onError: (e) => toast.show(e.message, 'error') },
              )
            }}
          >
            <Ban className="size-4" aria-hidden />
            이용 차단
          </Button>
        )}
        <Button
          size="sm"
          variant="secondary"
          loading={resolve.isPending}
          onClick={() =>
            resolve.mutate(
              { id: r.id, resolved: open },
              {
                onSuccess: () => toast.show(open ? '처리 완료로 바꿨어요' : '다시 처리 전으로 바꿨어요', 'ok'),
                onError: (e) => toast.show(e.message, 'error'),
              },
            )
          }
        >
          {open ? <Check className="size-4" aria-hidden /> : <RotateCcw className="size-4" aria-hidden />}
          {open ? '처리 완료' : '다시 열기'}
        </Button>
      </div>
    </li>
  )
}

function Reports() {
  const { data, isPending, isError, error, refetch } = useAdminReports()
  const isOwner = useIsOwner()
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  return (
    <div className="flex flex-col gap-3">
      <p className="px-1 text-sm leading-relaxed text-ink-3">
        {isOwner ? '다섯 학교의 신고가 모두 보여요.' : '우리 학교 3교시·공지·원우에 대한 신고가 보여요.'} 신고한 사람은
        보이지 않아요. 확인하면 글을 지우거나 3교시를 취소하고, 필요하면 이용을 막아요.
      </p>
      {data.length === 0 ? (
        <div className={cardClass}>
          <EmptyState title="들어온 신고가 없어요" />
        </div>
      ) : (
        <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
          {data.map((r) => (
            <ReportCard key={r.id} r={r} />
          ))}
        </ul>
      )}
    </div>
  )
}

// ---------------- 학교 · 학과 (바꾸기는 대학원장만) ----------------

function SchoolInfoForm({ school }: { school: School }) {
  const { updateSchool } = useSchoolAdmin()
  const toast = useToast()
  const [name, setName] = useState(school.name)
  const [shortName, setShortName] = useState(school.shortName)
  const [graduateSchool, setGraduateSchool] = useState(school.graduateSchool ?? '')
  const [latestCohort, setLatestCohort] = useState(String(school.latestCohort))
  const changed =
    name !== school.name ||
    shortName !== school.shortName ||
    graduateSchool !== (school.graduateSchool ?? '') ||
    Number(latestCohort) !== school.latestCohort

  return (
    <form
      noValidate
      className="grid gap-3 sm:grid-cols-2"
      onSubmit={(e) => {
        e.preventDefault()
        updateSchool.mutate(
          { id: school.id, name: name.trim(), shortName: shortName.trim(), graduateSchool: graduateSchool.trim(), latestCohort: Number(latestCohort) },
          { onSuccess: () => toast.show('학교 정보를 바꿨어요', 'ok'), onError: (err) => toast.show(err.message, 'error') },
        )
      }}
    >
      <Field label="학교 이름">
        {({ id }) => <input id={id} className={inputClass} value={name} onChange={(e) => setName(e.target.value)} />}
      </Field>
      <Field label="짧은 이름 (배지)">
        {({ id }) => (
          <input id={id} className={inputClass} maxLength={10} value={shortName} onChange={(e) => setShortName(e.target.value)} />
        )}
      </Field>
      <Field label="대학원 이름" optional>
        {({ id }) => (
          <input
            id={id}
            className={inputClass}
            maxLength={40}
            placeholder="예: SW·AI융합대학원"
            value={graduateSchool}
            onChange={(e) => setGraduateSchool(e.target.value)}
          />
        )}
      </Field>
      <Field label="최신 기수" hint="가입할 때 1기부터 이 기수까지 고를 수 있어요">
        {({ id, describedBy }) => (
          <div className="relative">
            <input
              id={id}
              type="number"
              inputMode="numeric"
              min={1}
              max={999}
              aria-describedby={describedBy}
              className={`${inputClass} tabular pr-10 font-semibold`}
              value={latestCohort}
              onChange={(e) => setLatestCohort(e.target.value)}
            />
            <span className="pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-ink-3">기</span>
          </div>
        )}
      </Field>
      <div className="sm:col-span-2">
        <Button type="submit" variant="secondary" size="sm" loading={updateSchool.isPending} disabled={!changed || !name.trim() || !shortName.trim() || !latestCohort}>
          저장
        </Button>
      </div>
    </form>
  )
}

function DomainList({ school, editable }: { school: School; editable: boolean }) {
  const { addDomain, removeDomain } = useSchoolAdmin()
  const toast = useToast()
  const [domain, setDomain] = useState('')
  return (
    <div className="flex flex-col gap-2">
      <h4 className="text-sm font-bold text-ink-2">이메일 도메인</h4>
      <ul className="flex flex-wrap gap-2">
        {school.emailDomains.map((d) => (
          <li key={d.id} className="inline-flex h-9 items-center gap-1 rounded-lg border border-line-strong pr-1 pl-3 text-sm text-ink">
            <span className="tabular">@{d.domain}</span>
            {editable && school.emailDomains.length > 1 && (
              <button
                type="button"
                aria-label={`@${d.domain} 빼기`}
                onClick={() => {
                  if (!window.confirm(`@${d.domain} 을 빼면 이 도메인으로는 새로 인증할 수 없어요. 뺄까요?`)) return
                  removeDomain.mutate(
                    { schoolId: school.id, domainId: d.id },
                    { onSuccess: () => toast.show('도메인을 뺐어요', 'ok'), onError: (e) => toast.show(e.message, 'error') },
                  )
                }}
                className="flex size-7 items-center justify-center rounded-md text-ink-3 hover:bg-surface-2 hover:text-danger-ink"
              >
                <X className="size-4" aria-hidden />
              </button>
            )}
          </li>
        ))}
      </ul>
      <p className="text-[13px] text-ink-3">하위 도메인도 같은 학교로 봐요 (skku.edu 면 g.skku.edu 도).</p>
      {editable && (
        <form
          noValidate
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            addDomain.mutate(
              { schoolId: school.id, domain: domain.trim() },
              {
                onSuccess: () => {
                  setDomain('')
                  toast.show('도메인을 더했어요', 'ok')
                },
                onError: (err) => toast.show(err.message, 'error'),
              },
            )
          }}
        >
          <input
            aria-label={`${school.shortName} 이메일 도메인 더하기`}
            className={`${inputClass} h-11 min-w-0 flex-1`}
            autoCapitalize="none"
            placeholder="새 도메인"
            value={domain}
            onChange={(e) => setDomain(e.target.value)}
          />
          <Button type="submit" variant="secondary" loading={addDomain.isPending} disabled={!domain.trim()} className="h-11 shrink-0">
            더하기
          </Button>
        </form>
      )}
    </div>
  )
}

function DepartmentRow({ d, editable }: { d: Department; editable: boolean }) {
  const { updateDepartment } = useSchoolAdmin()
  const toast = useToast()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(d.name)
  const [shortName, setShortName] = useState(d.shortName)
  const [graduateSchool, setGraduateSchool] = useState(d.graduateSchool ?? '')

  function save(active: boolean, message: string) {
    updateDepartment.mutate(
      { id: d.id, name: name.trim(), shortName: shortName.trim(), graduateSchool: graduateSchool.trim(), active },
      {
        onSuccess: () => {
          setEditing(false)
          toast.show(message, 'ok')
        },
        onError: (e) => toast.show(e.message, 'error'),
      },
    )
  }

  if (editing) {
    return (
      <li className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:flex-wrap sm:items-center">
        <input aria-label="학과 이름" className={`${inputClass} h-11 min-w-0 sm:flex-[2]`} value={name} onChange={(e) => setName(e.target.value)} />
        <input
          aria-label="짧은 이름"
          className={`${inputClass} h-11 min-w-0 sm:flex-1`}
          maxLength={10}
          value={shortName}
          onChange={(e) => setShortName(e.target.value)}
        />
        <input
          aria-label="대학원 (학교와 다를 때만)"
          className={`${inputClass} h-11 min-w-0 sm:flex-[1.5]`}
          maxLength={40}
          placeholder="대학원 (다를 때만)"
          value={graduateSchool}
          onChange={(e) => setGraduateSchool(e.target.value)}
        />
        <span className="flex shrink-0 gap-2">
          <Button size="sm" loading={updateDepartment.isPending} disabled={!name.trim() || !shortName.trim()} onClick={() => save(d.active, '학과를 고쳤어요')}>
            저장
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
            취소
          </Button>
        </span>
      </li>
    )
  }
  return (
    <li className={`flex items-center gap-3 px-4 py-3 ${d.active ? '' : 'bg-surface-2/60'}`}>
      <span className="min-w-0 flex-1">
        <span className={`font-semibold ${d.active ? 'text-ink' : 'text-ink-3 line-through'}`}>{d.name}</span>
        <span className="ml-2 rounded bg-surface-2 px-1.5 py-0.5 text-xs font-bold text-ink-2">{d.shortName}</span>
        {d.graduateSchool && <span className="ml-2 text-xs font-semibold text-ink-2">{d.graduateSchool}</span>}
        {!d.active && <span className="ml-2 text-xs font-semibold text-ink-3">숨김</span>}
      </span>
      {editable && (
        <span className="flex shrink-0 gap-1">
          <button
            type="button"
            aria-label={`${d.name} 고치기`}
            onClick={() => setEditing(true)}
            className="flex size-9 items-center justify-center rounded-lg text-ink-3 hover:bg-surface-2 hover:text-ink"
          >
            <Pencil className="size-4" aria-hidden />
          </button>
          <button
            type="button"
            aria-label={d.active ? `${d.name} 숨기기` : `${d.name} 다시 보이기`}
            onClick={() => save(!d.active, d.active ? '숨겼어요. 가입할 때 고를 수 없어요' : '다시 보이게 했어요')}
            className="flex size-9 items-center justify-center rounded-lg text-ink-3 hover:bg-surface-2 hover:text-ink"
          >
            {d.active ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
          </button>
        </span>
      )}
    </li>
  )
}

function DepartmentList({ school, editable }: { school: School; editable: boolean }) {
  const { addDepartment } = useSchoolAdmin()
  const toast = useToast()
  const [name, setName] = useState('')
  const [shortName, setShortName] = useState('')
  const [graduateSchool, setGraduateSchool] = useState('')
  return (
    <div className="flex flex-col gap-2">
      <h4 className="text-sm font-bold text-ink-2">학과</h4>
      {school.departments.length === 0 ? (
        <p className="text-sm text-ink-3">아직 학과가 없어요.</p>
      ) : (
        <ul className="divide-y divide-line rounded-xl border border-line">
          {school.departments.map((d) => (
            <DepartmentRow key={`${d.id}-${d.name}-${d.shortName}-${d.graduateSchool}-${d.active}`} d={d} editable={editable} />
          ))}
        </ul>
      )}
      {editable && (
        <>
          <p className="text-[13px] text-ink-3">
            숨긴 학과는 가입할 때 고를 수 없지만, 이미 그 학과인 원우에게는 그대로 보여요. 짧은 이름은 이름 옆에 붙어요 (김*대 ·
            61기 · <b>인융</b>). 대학원은 학교에 적은 대학원과 다를 때만 적어요 (예: 연세대 공학대학원).
          </p>
          <form
            noValidate
            className="flex flex-col gap-2 sm:flex-row sm:flex-wrap"
            onSubmit={(e) => {
              e.preventDefault()
              addDepartment.mutate(
                { schoolId: school.id, name: name.trim(), shortName: shortName.trim(), graduateSchool: graduateSchool.trim() },
                {
                  onSuccess: () => {
                    setName('')
                    setShortName('')
                    setGraduateSchool('')
                    toast.show('학과를 더했어요', 'ok')
                  },
                  onError: (err) => toast.show(err.message, 'error'),
                },
              )
            }}
          >
            <input
              aria-label={`${school.shortName} 새 학과 이름`}
              className={`${inputClass} h-11 min-w-0 sm:flex-[2]`}
              placeholder="새 학과 이름"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <input
              aria-label={`${school.shortName} 새 학과 짧은 이름`}
              className={`${inputClass} h-11 min-w-0 sm:flex-1`}
              maxLength={10}
              placeholder="짧은 이름 (예: 인융)"
              value={shortName}
              onChange={(e) => setShortName(e.target.value)}
            />
            <input
              aria-label={`${school.shortName} 새 학과 대학원`}
              className={`${inputClass} h-11 min-w-0 sm:flex-[1.5]`}
              maxLength={40}
              placeholder="대학원 (다를 때만)"
              value={graduateSchool}
              onChange={(e) => setGraduateSchool(e.target.value)}
            />
            <Button type="submit" variant="secondary" loading={addDepartment.isPending} disabled={!name.trim() || !shortName.trim()} className="h-11 shrink-0">
              더하기
            </Button>
          </form>
        </>
      )}
    </div>
  )
}

function SchoolCard({ school, editable }: { school: School; editable: boolean }) {
  return (
    <section className={`${cardClass} flex flex-col gap-5 px-5 py-5`} aria-label={school.name}>
      <header className="flex flex-wrap items-center gap-2">
        <SchoolBadge school={school} />
        <h3 className="text-[17px] font-extrabold text-ink">{school.name}</h3>
        <span className="text-sm text-ink-3">
          {[school.graduateSchool, `최신 ${school.latestCohort}기`].filter(Boolean).join(' · ')}
        </span>
      </header>
      {editable && <SchoolInfoForm key={`${school.name}-${school.shortName}-${school.graduateSchool}-${school.latestCohort}`} school={school} />}
      <DomainList school={school} editable={editable} />
      <DepartmentList school={school} editable={editable} />
    </section>
  )
}

function AddSchoolForm() {
  const { addSchool } = useSchoolAdmin()
  const toast = useToast()
  const [name, setName] = useState('')
  const [shortName, setShortName] = useState('')
  const [domain, setDomain] = useState('')
  const [error, setError] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    addSchool.mutate(
      { name: name.trim(), shortName: shortName.trim(), graduateSchool: '', latestCohort: 1, emailDomain: domain.trim() },
      {
        onSuccess: () => {
          setName('')
          setShortName('')
          setDomain('')
          toast.show('학교를 추가했어요. 학과와 최신 기수도 넣어 주세요', 'ok')
        },
        onError: (err) => setError(err.message),
      },
    )
  }

  return (
    <section className={`${cardClass} px-5 py-5`}>
      <h2 className="text-[17px] font-extrabold text-ink">학교 추가</h2>
      <p className="mt-1 text-sm leading-relaxed text-ink-2">
        새로 넣은 학교는 기본 색으로 보여요. 학교 색 테마는 다섯 학교만 있어요.
      </p>
      <form onSubmit={submit} className="mt-4 grid gap-3 sm:grid-cols-[1fr_8rem_1fr_auto] sm:items-end" noValidate>
        <Field label="학교 이름">
          {({ id }) => <input id={id} className={inputClass} placeholder="예: 국민대학교" value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
        <Field label="짧은 이름">
          {({ id }) => (
            <input id={id} className={inputClass} maxLength={10} placeholder="국민대" value={shortName} onChange={(e) => setShortName(e.target.value)} />
          )}
        </Field>
        <Field label="이메일 도메인">
          {({ id }) => (
            <input
              id={id}
              className={inputClass}
              autoCapitalize="none"
              placeholder="예: kookmin.ac.kr"
              value={domain}
              onChange={(e) => setDomain(e.target.value)}
            />
          )}
        </Field>
        <Button type="submit" variant="secondary" loading={addSchool.isPending} disabled={!name.trim() || !shortName.trim() || !domain.trim()}>
          추가
        </Button>
        <div className="sm:col-span-4">
          <FormError message={error} />
        </div>
      </form>
    </section>
  )
}

function Schools() {
  const schools = useAdminSchools()
  const isOwner = useIsOwner()
  if (schools.isPending) return <Spinner />
  if (schools.isError) return <ErrorState message={schools.error.message} onRetry={() => schools.refetch()} />
  return (
    <div className="flex flex-col gap-6">
      <p className="px-1 text-sm leading-relaxed text-ink-3">
        {isOwner
          ? '연세대·성균관대·서강대·한양대의 학과와 최신 기수는 공개 자료로 넣은 임시값이에요. 각 학교 원우에게 확인해서 고쳐 주세요.'
          : '학교 정보는 대학원장만 바꿀 수 있어요. 고칠 게 있으면 대학원장에게 알려 주세요.'}
      </p>
      {schools.data.map((s) => (
        <SchoolCard key={s.id} school={s} editable={isOwner} />
      ))}
      {isOwner && <AddSchoolForm />}
    </div>
  )
}

export function AdminPage() {
  const { member } = useAuth()
  const [tab, setTab] = useState<Tab>('members')
  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">
            관리
            {member && member.role !== 'USER' && <RoleTag role={member.role} />}
          </h1>
          <p className="mt-1 text-[15px] text-ink-2">
            {member?.role === 'OWNER' ? '다섯 학교 원우·신고·3교시와 학교 정보' : `${member?.school.shortName} 원우·신고·3교시`}
          </p>
        </div>
        <Segmented
          label="관리 메뉴"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'members', label: '원우' },
            { value: 'reports', label: '신고' },
            { value: 'overview', label: '현황' },
            { value: 'meetups', label: '3교시' },
            { value: 'schools', label: '학교' },
          ]}
        />
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'members' && <Members />}
      {tab === 'reports' && <Reports />}
      {tab === 'meetups' && <Meetups />}
      {tab === 'schools' && <Schools />}
    </div>
  )
}
