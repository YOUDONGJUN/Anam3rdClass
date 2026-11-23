import { EyeOff } from 'lucide-react'
import type { PersonView } from '../api/types'
import { affiliation } from '../lib/person'

/** 관리자 표시. 이름 옆에 붙는다: "유동준 [관리자] · 61기 · 인융" */
export function AdminTag({ className = '' }: { className?: string }) {
  return (
    <span className={`shrink-0 rounded bg-exit px-1.5 py-0.5 text-[11px] leading-none font-bold text-on-exit ${className}`}>
      관리자
    </span>
  )
}

/** "김*수 · 61기 · 인융" — 가려진 이름이면 작은 아이콘으로 알려준다 */
export function PersonName({ person, showMe = true }: { person: PersonView; showMe?: boolean }) {
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
      <span className="truncate font-semibold">{person.name}</span>
      {person.admin && <AdminTag />}
      {person.masked && <EyeOff className="size-3.5 shrink-0 opacity-60" aria-label="이름 가려짐" />}
      <span className="shrink-0 opacity-70">· {affiliation(person.cohortLabel, person.departmentLabel)}</span>
      {showMe && person.me && (
        <span className="shrink-0 rounded bg-board px-1.5 py-0.5 text-[11px] font-bold text-on-board">나</span>
      )}
    </span>
  )
}
