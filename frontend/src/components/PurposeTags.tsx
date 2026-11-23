/** 모임 목적 태그 (친목, 학업 …) */
export function PurposeTags({ purposes, tone = 'surface' }: { purposes: readonly string[]; tone?: 'surface' | 'board' }) {
  const tag = tone === 'board' ? 'bg-board-line text-on-board' : 'bg-surface-2 text-ink-2'
  return (
    <ul className="flex flex-wrap gap-1" aria-label="모임 목적">
      {purposes.map((p) => (
        <li key={p} className={`rounded-md px-1.5 py-0.5 text-xs font-semibold ${tag}`}>
          {p}
        </li>
      ))}
    </ul>
  )
}
