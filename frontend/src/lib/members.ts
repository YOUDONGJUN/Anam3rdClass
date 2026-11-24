// 관리 화면 원우 그리드: 거르기·정렬·묶어 세기. 원우 수가 수백 명 규모라 화면에서 한다.
import type { AdminMember, MemberStatus, Role } from '../api/types'

export interface MemberFilters {
  q: string
  school: number | 'all'
  /** countByCohort 의 key: 한 학교면 '61', 여러 학교면 '학교번호-61' */
  cohort: string | 'all'
  /** 'none' = 학과 미입력 */
  department: number | 'all' | 'none'
  role: Role | 'all'
  status: MemberStatus | 'all'
}

export const EMPTY_FILTERS: MemberFilters = {
  q: '',
  school: 'all',
  cohort: 'all',
  department: 'all',
  role: 'all',
  status: 'all',
}

export function filterMembers(list: readonly AdminMember[], f: MemberFilters): AdminMember[] {
  const q = f.q.trim().toLowerCase()
  return list.filter((m) => {
    if (f.school !== 'all' && m.school.id !== f.school) return false
    if (f.cohort !== 'all' && f.cohort !== (f.cohort.includes('-') ? `${m.school.id}-${m.cohort}` : String(m.cohort)))
      return false
    if (f.department === 'none' ? m.departmentId !== null : f.department !== 'all' && m.departmentId !== f.department)
      return false
    if (f.role !== 'all' && m.role !== f.role) return false
    // 탈퇴한 원우는 상태에서 "탈퇴"를 골랐을 때만 보인다
    if (f.status === 'all' ? m.status === 'WITHDRAWN' : m.status !== f.status) return false
    if (!q) return true
    return [m.name, m.email ?? '', m.cohortLabel, m.departmentLabel ?? '', m.school.name, m.school.shortName].some(
      (s) => s.toLowerCase().includes(q),
    )
  })
}

export type SortKey = 'name' | 'cohort' | 'department' | 'school' | 'role' | 'status' | 'createdAt'
export type SortDir = 'asc' | 'desc'

const ROLE_RANK: Record<Role, number> = { OWNER: 0, MANAGER: 1, USER: 2 }

function compare(a: AdminMember, b: AdminMember, key: SortKey): number {
  switch (key) {
    case 'name':
      return a.name.localeCompare(b.name, 'ko')
    case 'cohort':
      return a.cohort - b.cohort
    case 'department':
      return (a.departmentLabel ?? '').localeCompare(b.departmentLabel ?? '', 'ko')
    case 'school':
      return a.school.shortName.localeCompare(b.school.shortName, 'ko')
    case 'role':
      return ROLE_RANK[a.role] - ROLE_RANK[b.role]
    case 'status':
      return a.status.localeCompare(b.status)
    case 'createdAt':
      return a.createdAt.localeCompare(b.createdAt)
  }
}

/** 같은 값이면 이름순으로 (안정적인 순서) */
export function sortMembers(list: readonly AdminMember[], key: SortKey, dir: SortDir): AdminMember[] {
  const sign = dir === 'asc' ? 1 : -1
  return [...list].sort((a, b) => sign * compare(a, b, key) || a.name.localeCompare(b.name, 'ko') || a.id - b.id)
}

export interface Bucket {
  key: string
  label: string
  count: number
}

function countBy(list: readonly AdminMember[], keyOf: (m: AdminMember) => [string, string]): Bucket[] {
  const map = new Map<string, Bucket>()
  for (const m of list) {
    const [key, label] = keyOf(m)
    const bucket = map.get(key) ?? { key, label, count: 0 }
    bucket.count += 1
    map.set(key, bucket)
  }
  return [...map.values()]
}

function byCountThenLabel(a: Bucket, b: Bucket) {
  return b.count - a.count || a.label.localeCompare(b.label, 'ko')
}

/** 학교별 — 많은 순 */
export function countBySchool(list: readonly AdminMember[]): Bucket[] {
  return countBy(list, (m) => [String(m.school.id), m.school.shortName]).sort(byCountThenLabel)
}

/** 기수별 — 최신 기수부터 (순서가 있는 값이라 많은 순으로 섞지 않는다). 학교마다 기수가 다르면 학교도 붙인다 */
export function countByCohort(list: readonly AdminMember[]): Bucket[] {
  const multi = new Set(list.map((m) => m.school.id)).size > 1
  return countBy(list, (m) =>
    multi ? [`${m.school.id}-${m.cohort}`, `${m.school.shortName} ${m.cohortLabel}`] : [String(m.cohort), m.cohortLabel],
  ).sort((a, b) => {
    const [as, ac] = a.key.split('-')
    const [bs, bc] = b.key.split('-')
    return bc === undefined ? Number(b.key) - Number(a.key) : Number(as) - Number(bs) || Number(bc) - Number(ac)
  })
}

/** 학과별 — 많은 순. 학교가 여럿이면 학교 이름을 앞에 붙인다 */
export function countByDepartment(list: readonly AdminMember[]): Bucket[] {
  const multi = new Set(list.map((m) => m.school.id)).size > 1
  return countBy(list, (m) => {
    if (m.departmentId === null) return ['none', '학과 미입력']
    const label = m.departmentLabel ?? ''
    return [String(m.departmentId), multi ? `${m.school.shortName} ${label}` : label]
  }).sort(byCountThenLabel)
}
