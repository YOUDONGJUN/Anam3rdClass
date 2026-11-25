import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  ArrowDown,
  ArrowUp,
  Ban,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  Copy,
  Eye,
  EyeOff,
  Flag,
  Frown,
  History,
  Pencil,
  RotateCcw,
  TriangleAlert,
  UserCheck,
  UserMinus,
  UserX,
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
  useWithdrawMember,
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
import { FeedbackStatsPanel } from './AdminFeedbackStats'
import { useNow } from '../lib/hooks'
import {
  countByCohort,
  countByDepartment,
  countBySchool,
  EMPTY_FILTERS,
  filterMembers,
  findDuplicates,
  isLegacy,
  sortMembers,
  type Bucket,
  type MemberFilters,
  type SortDir,
  type SortKey,
} from '../lib/members'
import { affiliation, ROLE_LABEL } from '../lib/person'
import { formatRelative } from '../lib/time'

type Tab = 'home' | 'members' | 'reports' | 'meetups' | 'schools' | 'stats'

const PAGE_SIZE = 20

function useIsOwner() {
  return useAuth().member?.role === 'OWNER'
}

// ---------------- 홈: 할 일 + 한눈에 ----------------

/** 최신 기수를 아직 공개 자료 임시값(99)으로 둔 학교 */
const PLACEHOLDER_COHORT = 99

interface Task {
  key: string
  tone: 'danger' | 'warn' | 'info'
  Icon: typeof Flag
  title: string
  detail?: string
  action: string
  tab: Tab
}

function TaskList({ tasks, onGo }: { tasks: Task[]; onGo: (tab: Tab) => void }) {
  if (tasks.length === 0) {
    return (
      <div className={`${cardClass} flex items-center gap-3 px-5 py-4`}>
        <CircleCheck className="size-5 shrink-0 text-ok" aria-hidden />
        <p className="font-semibold text-ink">지금 처리할 일이 없어요</p>
      </div>
    )
  }
  const tone = {
    danger: 'bg-danger-soft text-danger-ink',
    warn: 'bg-exit/30 text-ink',
    info: 'bg-surface-2 text-ink-2',
  }
  return (
    <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
      {tasks.map((t) => (
        <li key={t.key}>
          <button
            type="button"
            onClick={() => onGo(t.tab)}
            className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2"
          >
            <span className={`flex size-9 shrink-0 items-center justify-center rounded-full ${tone[t.tone]}`}>
              <t.Icon className="size-4" aria-hidden />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block font-bold text-ink">{t.title}</span>
              {t.detail && <span className="mt-0.5 block truncate text-sm text-ink-3">{t.detail}</span>}
            </span>
            <span className="flex shrink-0 items-center gap-0.5 text-sm font-semibold text-accent-ink">
              {t.action}
              <ChevronRight className="size-4" aria-hidden />
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}

function AdminHome({ onGo }: { onGo: (tab: Tab) => void }) {
  const isOwner = useIsOwner()
  const stats = useAdminStats()
  const members = useAdminMembers()
  const meetups = useAdminMeetups()
  const reports = useAdminReports()
  const schools = useAdminSchools()
  const now = useNow()

  if (stats.isPending || members.isPending || meetups.isPending || reports.isPending || schools.isPending) return <Spinner />
  const failed = [stats, members, meetups, reports, schools].find((q) => q.isError && !q.data)
  if (failed?.error) return <ErrorState message={failed.error.message} onRetry={() => failed.refetch()} />
  if (!stats.data || !members.data || !meetups.data || !reports.data || !schools.data) return null

  const s = stats.data
  const current = members.data.filter((m) => m.status !== 'WITHDRAWN')
  const openReports = reports.data.filter((r) => r.status === 'OPEN')
  const placeholder = schools.data.filter((x) => x.latestCohort === PLACEHOLDER_COHORT)
  const noDepartments = schools.data.filter((x) => !x.departments.some((d) => d.active))
  const legacy = current.filter((m) => isLegacy(m) && m.status === 'ACTIVE')
  const duplicates = findDuplicates(members.data)

  const tasks: Task[] = []
  if (openReports.length > 0)
    tasks.push({
      key: 'reports',
      tone: 'danger',
      Icon: Flag,
      title: `처리 안 된 신고 ${openReports.length}건`,
      detail: REASON_LABEL[openReports[0].reason] + (openReports.length > 1 ? ` 외 ${openReports.length - 1}건` : ''),
      action: '확인',
      tab: 'reports',
    })
  if (isOwner && placeholder.length > 0)
    tasks.push({
      key: 'cohort',
      tone: 'warn',
      Icon: TriangleAlert,
      title: `최신 기수 확인 필요 ${placeholder.length}곳`,
      detail: placeholder.map((x) => x.shortName).join(' · '),
      action: '설정',
      tab: 'schools',
    })
  if (isOwner && noDepartments.length > 0)
    tasks.push({
      key: 'departments',
      tone: 'warn',
      Icon: TriangleAlert,
      title: `학과가 없는 학교 ${noDepartments.length}곳`,
      detail: noDepartments.map((x) => x.shortName).join(' · '),
      action: '설정',
      tab: 'schools',
    })
  if (duplicates.length > 0)
    tasks.push({
      key: 'duplicates',
      tone: 'warn',
      Icon: Copy,
      title: `중복 가입 의심 ${duplicates.length}건`,
      detail:
        duplicates
          .slice(0, 2)
          .map((g) => `${g[0].school.shortName} ${g[0].name} ${g[0].cohortLabel} · ${g.length}개 계정`)
          .join(', ') + (duplicates.length > 2 ? ` 외 ${duplicates.length - 2}건` : ''),
      action: '보기',
      tab: 'members',
    })
  if (isOwner && legacy.length > 0)
    tasks.push({
      key: 'legacy',
      tone: 'info',
      Icon: History,
      title: `아직 안 들어온 예전 회원 ${legacy.length}명`,
      detail: '예전 계정 · 처음 들어올 때 이어져요',
      action: '보기',
      tab: 'members',
    })

  const kpis = [
    { label: '원우', value: s.members },
    { label: '3교시', value: s.meetups },
    { label: '성사', value: s.confirmedMeetups },
    { label: '공지', value: s.notices },
  ]

  const upcoming = meetups.data
    .filter((m) => (m.status === 'RECRUITING' || m.status === 'CONFIRMED') && new Date(m.meetAt) > now)
    .sort((a, b) => a.meetAt.localeCompare(b.meetAt))
    .slice(0, 3)

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <h2 className="px-1 text-[17px] font-extrabold text-ink">할 일</h2>
        <TaskList tasks={tasks} onGo={onGo} />
      </section>

      <section className={`${cardClass} grid grid-cols-2 sm:grid-cols-4 sm:divide-x sm:divide-line`} aria-label="한눈에">
        {kpis.map((k) => (
          <div key={k.label} className="px-5 py-4">
            <p className="text-sm text-ink-3">{k.label}</p>
            <p className="tabular mt-0.5 text-2xl font-extrabold text-ink">{k.value.toLocaleString('ko-KR')}</p>
          </div>
        ))}
      </section>

      {isOwner ? (
        <section className="flex flex-col gap-3">
          <h2 className="px-1 text-[17px] font-extrabold text-ink">학교별</h2>
          <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
            {schools.data.map((school) => {
              const count = current.filter((m) => m.school.id === school.id).length
              const meetupCount = meetups.data.filter((m) => m.school.id === school.id).length
              const reportCount = openReports.filter((r) => r.school?.id === school.id).length
              const temp = school.latestCohort === PLACEHOLDER_COHORT
              return (
                <li key={school.id} className="flex flex-wrap items-center gap-x-4 gap-y-1.5 px-5 py-3.5">
                  <span className="flex min-w-0 flex-1 basis-40 items-center gap-2">
                    <SchoolBadge school={school} />
                    <span className="truncate text-sm text-ink-3">{school.graduateSchool}</span>
                  </span>
                  <span className="tabular flex shrink-0 items-center gap-3 text-sm text-ink-2">
                    <span>원우 <b className="text-ink">{count}</b></span>
                    <span>3교시 <b className="text-ink">{meetupCount}</b></span>
                    <span className={reportCount > 0 ? 'font-semibold text-danger-ink' : ''}>
                      신고 <b>{reportCount}</b>
                    </span>
                    <span className={temp ? 'rounded bg-exit/30 px-1.5 py-0.5 font-semibold text-ink' : ''}>
                      {temp ? '기수 미정' : `최신 ${school.latestCohort}기`}
                    </span>
                  </span>
                </li>
              )
            })}
          </ul>
        </section>
      ) : (
        <section className="flex flex-col gap-3">
          <h2 className="px-1 text-[17px] font-extrabold text-ink">다가오는 우리 학교 3교시</h2>
          {upcoming.length === 0 ? (
            <div className={cardClass}>
              <EmptyState title="다가오는 3교시가 없어요" />
            </div>
          ) : (
            <div className={`${cardClass} divide-y divide-line overflow-hidden`}>
              {upcoming.map((m) => (
                <MeetupRow key={m.id} meetup={m} now={now} showDate />
              ))}
            </div>
          )}
        </section>
      )}
    </div>
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
  const unlinked = list.filter(isLegacy).length
  const tiles = [
    { label: '원우', value: list.length },
    { label: '활동 중', value: active },
    { label: '차단', value: list.filter((m) => m.status === 'BANNED').length },
    { label: '예전 회원', value: unlinked },
  ]
  return (
    <section className={`${cardClass} flex flex-col px-5 py-4`} aria-label="원우 통계">
      <div className="grid grid-cols-2 gap-y-3 sm:grid-cols-4">
        {tiles.map((t) => (
          <div key={t.label}>
            <p className="text-sm text-ink-3">{t.label}</p>
            <p className="tabular mt-0.5 text-xl font-extrabold text-ink">{t.value.toLocaleString('ko-KR')}</p>
          </div>
        ))}
      </div>
      <details className="group mt-3 border-t border-line pt-3">
        <summary className="flex cursor-pointer list-none items-center gap-1 text-sm font-semibold text-ink-2">
          학교·학과·기수별 보기
          <ChevronDown className="size-4 transition-transform group-open:rotate-180" aria-hidden />
        </summary>
        <div className="mt-4 grid gap-6 sm:grid-cols-2">
          {multiSchool && <BarList title="학교별 원우" buckets={countBySchool(list)} total={list.length} />}
          <BarList title="학과별 원우" buckets={countByDepartment(list)} total={list.length} />
          <BarList title="기수별 원우" buckets={countByCohort(list)} total={list.length} />
        </div>
      </details>
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
  schools,
  filters,
  onChange,
}: {
  list: AdminMember[]
  /** 등록된 모든 학교 (원우가 없는 학교도 보인다) */
  schools: School[]
  filters: MemberFilters
  onChange: (next: MemberFilters) => void
}) {
  const isOwner = useIsOwner()
  const set = (patch: Partial<MemberFilters>) => onChange({ ...filters, ...patch })
  const current = list.filter((m) => m.status !== 'WITHDRAWN')
  const countOf = (schoolId: number) => current.filter((m) => m.school.id === schoolId).length
  // 학교를 고르면 학과·기수는 그 학교 것만
  const picked = filters.school === 'all' ? null : schools.find((s) => s.id === filters.school) ?? null
  const scoped = picked ? list.filter((m) => m.school.id === picked.id) : list
  const cohorts = countByCohort(scoped.filter((m) => m.status !== 'WITHDRAWN'))
  const departmentSchools = picked ? [picked] : isOwner ? schools : schools.filter((s) => list.some((m) => m.school.id === s.id))

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
      <div className={`grid grid-cols-2 gap-2 ${isOwner ? 'sm:grid-cols-5' : 'sm:grid-cols-4'}`}>
        {isOwner && (
          <FilterSelect
            label="학교"
            value={String(filters.school)}
            // 학교를 바꾸면 학과·기수는 처음부터
            onChange={(v) => set({ school: v === 'all' ? v : Number(v), department: 'all', cohort: 'all' })}
          >
            <option value="all">전체 ({current.length})</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.shortName} ({countOf(s.id)})
              </option>
            ))}
          </FilterSelect>
        )}
        <FilterSelect
          label="학과"
          value={String(filters.department)}
          onChange={(v) => set({ department: v === 'all' || v === 'none' ? v : Number(v) })}
        >
          <option value="all">전체</option>
          {departmentSchools.length === 1
            ? departmentSchools[0].departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))
            : departmentSchools.map((s) => (
                <optgroup key={s.id} label={s.shortName}>
                  {s.departments.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </optgroup>
              ))}
          <option value="none">학과 미입력</option>
        </FilterSelect>
        <FilterSelect label="기수" value={String(filters.cohort)} onChange={(v) => set({ cohort: v })}>
          <option value="all">전체</option>
          {cohorts.map((c) => (
            <option key={c.key} value={c.key}>
              {c.label}
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
          <option value="all">전체 (탈퇴 빼고)</option>
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
      {isLegacy(m) && (
        <span className="shrink-0 rounded bg-surface-2 px-1.5 py-0.5 text-[11px] leading-none font-bold text-ink-3">
          예전 회원
        </span>
      )}
      {!m.kakaoLinked && m.hasPassword && m.status !== 'WITHDRAWN' && (
        <span className="shrink-0 rounded bg-surface-2 px-1.5 py-0.5 text-[11px] leading-none font-bold text-ink-3">
          이메일 가입
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
        <table className="w-full min-w-[720px] text-sm">
          <thead className="border-b border-line">
            <tr>
              <SortHeader label="이름" sortKey="name" sort={sort} onSort={onSort} />
              <SortHeader label="학교" sortKey="school" sort={sort} onSort={onSort} />
              <SortHeader label="학과" sortKey="department" sort={sort} onSort={onSort} />
              <SortHeader label="기수" sortKey="cohort" sort={sort} onSort={onSort} />
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
                <td className="px-3 py-2.5 whitespace-nowrap">
                  <SchoolBadge school={m.school} />
                </td>
                <td className="max-w-44 truncate px-3 py-2.5 text-ink-2" title={m.departmentLabel ?? undefined}>
                  {m.departmentLabel ?? '–'}
                </td>
                <td className="tabular px-3 py-2.5 whitespace-nowrap text-ink-2">{m.cohortLabel}</td>
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
              <span className="text-sm text-ink-2">{[m.departmentLabel, m.cohortLabel].filter(Boolean).join(' · ')}</span>
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
  const withdrawMember = useWithdrawMember()

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
          toast.show('이메일을 넣었어요. 본인이 인증하면 이어져요', 'ok')
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
            탈퇴한 원우예요. 개인정보는 지워졌어요.
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
            <p className="-mt-2 text-sm text-ink-3">원우가 요청하면 여기서 고쳐요</p>
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

        {isOwner && isLegacy(m) && (
          <form onSubmit={saveEmail} className="flex flex-col gap-3 border-t border-line pt-5" noValidate>
            <h3 className="text-[15px] font-extrabold">학교 이메일 넣어 두기</h3>
            <p className="-mt-1 text-sm leading-relaxed text-ink-3">
              카카오로 아직 안 들어온 예전 회원이에요.
              <br />
              이메일을 넣어 두면 본인 인증 때 이 계정으로 이어져요.
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
                      ? `${m.name} 님을 ${m.school.shortName} 교수로 임명할까요?\n\n${m.school.shortName} 원우·3교시·공지·신고를 관리하게 돼요.`
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

        {isOwner && !withdrawn && m.role !== 'OWNER' && (
          <div className="flex flex-col gap-2 rounded-xl border border-danger/40 px-4 py-4">
            <p className="text-[15px] font-extrabold text-danger-ink">탈퇴시키기</p>
            <p className="text-sm text-ink-2">이름·이메일·카카오 연결을 지워요. 되돌릴 수 없어요.</p>
            <Button
              size="sm"
              variant="danger"
              className="self-start"
              loading={withdrawMember.isPending}
              onClick={() => {
                if (
                  !window.confirm(
                    `${m.name} 님을 탈퇴시킬까요?\n\n• 이름·이메일·카카오 연결이 지워져요\n• 연 3교시는 취소, 모집 중인 3교시에서는 빠져요\n• 같은 학교 이메일로 다시 가입할 수는 있어요`,
                  )
                )
                  return
                withdrawMember.mutate(m.id, {
                  onSuccess: () => {
                    toast.show('탈퇴시켰어요', 'ok')
                    ref.current?.close()
                  },
                  onError: fail,
                })
              }}
            >
              <UserX className="size-4" aria-hidden />
              탈퇴시키기
            </Button>
          </div>
        )}

        <FormError message={error} />
      </div>
    </dialog>
  )
}

function Members() {
  const { data, isPending, error, refetch } = useAdminMembers()
  const schools = useAdminSchools()
  const isOwner = useIsOwner()
  const [filters, setFilters] = useState<MemberFilters>(EMPTY_FILTERS)
  const [openId, setOpenId] = useState<number | null>(null)

  const filtered = useMemo(() => (data ? filterMembers(data, filters) : []), [data, filters])

  if (isPending || schools.isPending) return <Spinner />
  // 처음 불러오기가 실패했을 때만 (열어 둔 원우 수정 창은 다시 불러오기가 실패해도 그대로)
  if (!data) return <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
  if (!schools.data)
    return <ErrorState message={schools.error?.message ?? '불러오지 못했어요'} onRetry={() => schools.refetch()} />

  const multiSchool = isOwner
  const opened = openId === null ? undefined : data.find((m) => m.id === openId)

  return (
    <div className="flex flex-col gap-5">
      <MemberFilterBar list={data} schools={schools.data} filters={filters} onChange={setFilters} />
      <MemberStats list={filtered} multiSchool={multiSchool} />
      {!isOwner && (
        <p className="px-1 text-sm text-ink-3">우리 학교 원우 · 이메일은 가려져요</p>
      )}
      <MembersGrid list={filtered} onOpen={(m) => setOpenId(m.id)} />
      {/* 목록이 새로 고쳐져도 같은 원우를 계속 보여준다 */}
      {opened && <MemberDialog key={opened.id} member={opened} onClose={() => setOpenId(null)} />}
    </div>
  )
}

/** 다른 탭(신고)에서 원우 한 명을 바로 연다. 관리할 수 없는 원우(교수에게 다른 학교 등)면 알려 주고 닫는다 */
function MemberDialogById({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isPending } = useAdminMembers()
  const toast = useToast()
  const found = data?.find((m) => m.id === id)
  const missing = !isPending && !found
  useEffect(() => {
    if (!missing) return
    toast.show('원우 목록에 없는 사람이에요', 'error')
    onClose()
  }, [missing, toast, onClose])
  if (!found) return null
  return <MemberDialog key={found.id} member={found} onClose={onClose} />
}

// ---------------- 3교시 ----------------

function Meetups() {
  const { data, isPending, error, refetch } = useAdminMeetups()
  const isOwner = useIsOwner()
  const now = useNow()
  if (isPending) return <Spinner />
  if (!data) return <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
  if (data.length === 0)
    return (
      <div className={cardClass}>
        <EmptyState title="아직 3교시가 없어요" />
      </div>
    )
  return (
    <div className="flex flex-col gap-3">
      <p className="px-1 text-sm text-ink-3">
        {isOwner ? '다섯 학교 · 무산·취소 포함 · 실명' : '우리 학교 · 무산·취소 포함 · 이름은 가려져요'}
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

function ReportCard({ r, onOpenMember }: { r: Report; onOpenMember: (memberId: number) => void }) {
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
        {r.fromFeedback && (
          <span className="inline-flex items-center gap-1 rounded bg-accent-soft px-1.5 py-0.5 text-[11px] leading-none font-bold text-accent-ink">
            <Frown className="size-3" aria-hidden />
            3교시 후기에서
          </span>
        )}
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
            {r.memberId ? (
              // 이름을 누르면 원우 탭에서 찾지 않고 바로 원우 관리 창
              <button
                type="button"
                onClick={() => onOpenMember(r.memberId!)}
                className="font-bold text-ink underline decoration-line-strong underline-offset-4 hover:decoration-ink"
              >
                {r.memberName}
              </button>
            ) : (
              <b className="text-ink">{r.memberName}</b>
            )}
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

/** 신고 목록 위 보기 칩 (전체 · 후기에서) */
function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`h-8 rounded-full px-3 text-[13px] font-bold transition-colors ${
        active
          ? 'bg-board text-on-board shadow-[0_2px_0_rgb(0_0_0/0.25)]'
          : 'border border-line-strong bg-surface text-ink shadow-[0_2px_0_var(--line-strong)] hover:bg-surface-2'
      }`}
    >
      {children}
    </button>
  )
}

/**
 * 신고 목록. 대학원장은 "후기에서" 온 신고만 모아 볼 수 있다 (통계의 "신고 탭에서 보기"로 오면 그렇게 열린다).
 * 교수에게는 후기에서 온 신고라는 표시가 내려오지 않는다.
 */
function Reports({
  feedbackOnly,
  onFeedbackOnly,
  onOpenMember,
}: {
  feedbackOnly: boolean
  onFeedbackOnly: (on: boolean) => void
  onOpenMember: (memberId: number) => void
}) {
  const { data, isPending, error, refetch } = useAdminReports()
  const isOwner = useIsOwner()
  if (isPending) return <Spinner />
  if (!data) return <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
  const fromFeedback = data.filter((r) => r.fromFeedback)
  const canFilter = isOwner && fromFeedback.length > 0
  const list = canFilter && feedbackOnly ? fromFeedback : data
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2 px-1">
        <p className="text-sm text-ink-3">
          {isOwner ? '다섯 학교 신고' : '우리 학교 신고'} · 신고한 사람은 안 보여요
        </p>
        {canFilter && (
          <div role="group" aria-label="신고 보기" className="flex gap-1.5">
            <FilterChip active={!feedbackOnly} onClick={() => onFeedbackOnly(false)}>
              전체 {data.length}
            </FilterChip>
            <FilterChip active={feedbackOnly} onClick={() => onFeedbackOnly(true)}>
              후기에서 {fromFeedback.length}
            </FilterChip>
          </div>
        )}
      </div>
      {list.length === 0 ? (
        <div className={cardClass}>
          <EmptyState title="들어온 신고가 없어요" />
        </div>
      ) : (
        <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
          {list.map((r) => (
            <ReportCard key={r.id} r={r} onOpenMember={onOpenMember} />
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
      <Field label="최신 기수" hint="가입 때 1기 ~ 이 기수 중에 골라요">
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
                  if (!window.confirm(`@${d.domain} 을 뺄까요?\n이 도메인으로는 새로 인증할 수 없어요.`)) return
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
      <p className="text-[13px] text-ink-3">하위 도메인도 포함돼요 (skku.edu → g.skku.edu)</p>
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
          <p className="text-[13px] text-ink-3">숨긴 학과는 가입 때 안 보여요 · 대학원은 학교와 다를 때만</p>
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
  const [open, setOpen] = useState(false)
  const temp = school.latestCohort === PLACEHOLDER_COHORT
  const departments = school.departments.filter((d) => d.active).length
  return (
    <section className={`${cardClass} overflow-hidden`} aria-label={school.name}>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2"
      >
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-2">
            <SchoolBadge school={school} />
            <span className="text-[17px] font-extrabold text-ink">{school.name}</span>
          </span>
          <span className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1 text-sm text-ink-3">
            {school.graduateSchool && <span>{school.graduateSchool}</span>}
            <span className={temp ? 'font-semibold text-danger-ink' : ''}>
              {temp ? '최신 기수 미정' : `최신 ${school.latestCohort}기`}
            </span>
            <span>학과 {departments}</span>
            <span>도메인 {school.emailDomains.length}</span>
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-1 text-sm font-semibold text-accent-ink">
          {editable ? (open ? '접기' : '고치기') : open ? '접기' : '보기'}
          <ChevronDown className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden />
        </span>
      </button>
      {open && (
        <div className="flex flex-col gap-6 border-t border-line px-5 py-5">
          {editable && (
            <SchoolInfoForm key={`${school.name}-${school.shortName}-${school.graduateSchool}-${school.latestCohort}`} school={school} />
          )}
          <DomainList school={school} editable={editable} />
          <DepartmentList school={school} editable={editable} />
        </div>
      )}
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
    <details className={`${cardClass} group px-5 py-4`}>
      <summary className="flex cursor-pointer list-none items-center gap-1 font-bold text-ink">
        학교 추가
        <ChevronDown className="size-4 text-ink-3 transition-transform group-open:rotate-180" aria-hidden />
        <span className="ml-auto text-sm font-medium text-ink-3">새 학교는 기본 색</span>
      </summary>
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
    </details>
  )
}

function Schools() {
  const schools = useAdminSchools()
  const isOwner = useIsOwner()
  if (schools.isPending) return <Spinner />
  if (!schools.data)
    return <ErrorState message={schools.error?.message ?? '불러오지 못했어요'} onRetry={() => schools.refetch()} />
  return (
    <div className="flex flex-col gap-3">
      <p className="px-1 text-sm text-ink-3">
        {isOwner ? '학과·최신 기수는 공개 자료로 넣은 값이에요. 학교별로 확인해 주세요' : '학교 정보는 대학원장만 고칠 수 있어요'}
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
  const [tab, setTab] = useState<Tab>('home')
  /** 신고 탭에서 후기에서 온 신고만 보기 (대학원장) */
  const [feedbackOnly, setFeedbackOnly] = useState(false)
  /** 신고 카드에서 이름을 눌러 연 원우 */
  const [openMemberId, setOpenMemberId] = useState<number | null>(null)
  const closeMember = useCallback(() => setOpenMemberId(null), [])
  const stats = useAdminStats()
  const open = stats.data?.openReports ?? 0
  const owner = member?.role === 'OWNER'
  const go = (next: Tab) => {
    setFeedbackOnly(false)
    setTab(next)
  }
  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">
            관리
            {member && member.role !== 'USER' && <RoleTag role={member.role} />}
          </h1>
          <p className="mt-1 text-[15px] text-ink-2">
            {member?.role === 'OWNER' ? '다섯 학교 전체' : `${member?.school.shortName}`}
          </p>
        </div>
        <Segmented
          label="관리 메뉴"
          value={tab}
          onChange={go}
          options={[
            { value: 'home', label: '홈' },
            { value: 'reports', label: open > 0 ? `신고 ${open}` : '신고' },
            { value: 'members', label: '원우' },
            { value: 'meetups', label: '3교시' },
            { value: 'schools', label: '학교' },
            // 3교시 후기 통계는 대학원장만
            ...(owner ? [{ value: 'stats' as const, label: '통계' }] : []),
          ]}
        />
      </div>
      {tab === 'home' && <AdminHome onGo={go} />}
      {tab === 'members' && <Members />}
      {tab === 'reports' && (
        <Reports feedbackOnly={feedbackOnly} onFeedbackOnly={setFeedbackOnly} onOpenMember={setOpenMemberId} />
      )}
      {tab === 'meetups' && <Meetups />}
      {tab === 'schools' && <Schools />}
      {tab === 'stats' && owner && (
        <FeedbackStatsPanel
          onOpenReports={() => {
            setTab('reports')
            setFeedbackOnly(true)
          }}
        />
      )}
      {openMemberId !== null && <MemberDialogById id={openMemberId} onClose={closeMember} />}
    </div>
  )
}
