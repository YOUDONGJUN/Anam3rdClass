import { MADE_BY } from '../lib/credit'

/** "Made by 유동준 · 고려대 SW·AI융합대학원 61기" — 좁으면 소속 앞에서 줄이 바뀐다. onBoard: 어두운 이름판 위 */
export function MadeBy({ onBoard = false, className = '' }: { onBoard?: boolean; className?: string }) {
  return (
    <p className={`text-xs leading-relaxed ${onBoard ? 'text-on-board-2' : 'text-ink-3'} ${className}`}>
      <span className="whitespace-nowrap">
        Made by <b className={`font-semibold ${onBoard ? 'text-on-board' : 'text-ink-2'}`}>{MADE_BY.name}</b>
      </span>
      <span aria-hidden> · </span>
      <span className="whitespace-nowrap">{MADE_BY.affiliation}</span>
    </p>
  )
}
