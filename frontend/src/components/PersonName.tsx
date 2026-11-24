import { EyeOff } from 'lucide-react'
import type { PersonView, Role } from '../api/types'
import { affiliation, ROLE_LABEL } from '../lib/person'
import { SchoolBadge } from './SchoolBadge'

/** 직책 표시 (대학원장·교수). 이름 옆에 붙는다: "유동준 [대학원장] · 61기 · 인융" */
export function RoleTag({ role = 'OWNER', className = '' }: { role?: Exclude<Role, 'USER'>; className?: string }) {
  const tone = role === 'OWNER' ? 'bg-exit text-on-exit' : 'bg-board text-on-board'
  return (
    <span className={`shrink-0 rounded px-1.5 py-0.5 text-[11px] leading-none font-bold ${tone} ${className}`}>
      {ROLE_LABEL[role]}
    </span>
  )
}

/**
 * "김*대 · 61기 · 인융" — 가려진 이름이면 작은 아이콘으로 알려준다.
 * showSchool 이면 학교 배지를 붙인다 (연합 3교시처럼 여러 학교 사람이 섞일 때).
 * wrap 이면 좁은 화면에서 이름을 자르지 않고 기수·배지를 다음 줄로 넘긴다 (참여자 노선도).
 */
export function PersonName({
  person,
  showMe = true,
  showSchool = false,
  wrap = false,
}: {
  person: PersonView
  showMe?: boolean
  showSchool?: boolean
  wrap?: boolean
}) {
  return (
    <span className={`inline-flex min-w-0 items-center ${wrap ? 'flex-wrap gap-x-1.5 gap-y-1' : 'gap-1.5'}`}>
      <span className="truncate font-semibold">{person.name}</span>
      {person.owner && <RoleTag />}
      {person.masked && <EyeOff className="size-3.5 shrink-0 opacity-60" aria-label="이름 가려짐" />}
      <span className="shrink-0 opacity-70">· {affiliation(person.cohortLabel, person.departmentLabel)}</span>
      {showSchool && <SchoolBadge school={person.school} />}
      {showMe && person.me && (
        <span className="shrink-0 rounded bg-board px-1.5 py-0.5 text-[11px] font-bold text-on-board">나</span>
      )}
    </span>
  )
}
