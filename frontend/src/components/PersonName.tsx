import { EyeOff } from 'lucide-react'
import type { PersonView } from '../api/types'

/** "김*수 · 25학번" — 가려진 이름이면 작은 아이콘으로 알려준다 */
export function PersonName({ person, showMe = true }: { person: PersonView; showMe?: boolean }) {
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
      <span className="truncate font-semibold">{person.name}</span>
      {person.masked && <EyeOff className="size-3.5 shrink-0 opacity-60" aria-label="이름 가려짐" />}
      <span className="shrink-0 opacity-70">· {person.admissionLabel}</span>
      {showMe && person.me && (
        <span className="shrink-0 rounded bg-board px-1.5 py-0.5 text-[11px] font-bold text-on-board">나</span>
      )}
    </span>
  )
}
