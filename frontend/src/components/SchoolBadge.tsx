import type { SchoolTag } from '../api/types'

/**
 * 학교 배지 "✓ 고려대" — 학교 이메일로 인증했다는 표시와 학교를 한 번에 보여준다.
 * 색은 학교마다 정해져 있다 (index.css .school-badge). 색만으로는 구분이 어려운 학교가 있어 이름을 늘 글자로 쓴다.
 */
export function SchoolBadge({ school, full = false, className = '' }: { school: SchoolTag; full?: boolean; className?: string }) {
  const label = full ? school.name : school.shortName
  return (
    <span
      className={`school-badge ${className}`}
      data-code={school.code ?? undefined}
      title={`${school.name} 이메일로 인증한 원우`}
    >
      <svg viewBox="0 0 12 12" className="size-[11px]" aria-hidden>
        <path d="M2.5 6.2 5 8.6l4.6-5" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="sr-only">학교 인증 · </span>
      {label}
    </span>
  )
}
