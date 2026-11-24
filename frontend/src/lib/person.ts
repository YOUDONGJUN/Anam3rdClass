import type { Role } from '../api/types'

/** 기수·학과를 "61기 · 인융" 형태로 (없는 값은 빼고) */
export function affiliation(cohortLabel: string | null | undefined, departmentLabel: string | null | undefined) {
  return [cohortLabel, departmentLabel].filter(Boolean).join(' · ')
}

/** 직책 이름 — 대학원 느낌으로. 바꾸고 싶으면 여기 한 곳만 고치면 된다 */
export const ROLE_LABEL: Record<Role, string> = {
  OWNER: '대학원장',
  MANAGER: '교수',
  USER: '원우',
}

/** 관리 화면에 들어갈 수 있는 직책 (교수·대학원장) */
export function isStaff(role: Role | null | undefined): boolean {
  return role === 'OWNER' || role === 'MANAGER'
}
