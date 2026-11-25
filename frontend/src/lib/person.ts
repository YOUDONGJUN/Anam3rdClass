import type { Member, Role } from '../api/types'

/** 기수·학과를 "61기 · 인융" 형태로 (없는 값은 빼고) */
export function affiliation(cohortLabel: string | null | undefined, departmentLabel: string | null | undefined) {
  return [cohortLabel, departmentLabel].filter(Boolean).join(' · ')
}

/** 이름 가운데를 가린다 (서버 NameMasker 와 같은 규칙): 김고대 → 김*대, 이수 → 이* */
export function maskName(name: string): string {
  const chars = [...name.trim()]
  if (chars.length === 0) return '익명'
  if (chars.length === 1) return '*'
  if (chars.length === 2) return `${chars[0]}*`
  return chars[0] + chars.slice(1, -1).map((c) => (c === ' ' ? ' ' : '*')).join('') + chars[chars.length - 1]
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

/** 내 정보에서 이름을 고칠 수 있는지 — 대학원장만. 교수·원우는 요청한다 */
export function canEditOwnName(role: Role | null | undefined): boolean {
  return role === 'OWNER'
}

/** 원우가 학과·기수를 다시 고칠 수 있는 때 (30일에 한 번). 지금 고칠 수 있으면 null */
export function profileLockedUntil(member: Pick<Member, 'profileEditableAt'>, now: Date = new Date()): Date | null {
  if (!member.profileEditableAt) return null
  const at = new Date(member.profileEditableAt)
  return at.getTime() > now.getTime() ? at : null
}

/** 내 정보 카드의 고치기 안내 한 줄 */
export function profileEditRule(role: Role): string {
  if (role === 'OWNER') return '이름·학과·기수를 언제든 고쳐요'
  if (role === 'MANAGER') return '학과·기수는 언제든 · 이름은 대학원장에게 요청'
  return '학과·기수는 30일에 한 번 · 이름은 교수·대학원장에게 요청'
}
