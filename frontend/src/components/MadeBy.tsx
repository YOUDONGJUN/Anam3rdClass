import { COMPANY, MADE_BY, SERVICE } from '../lib/credit'

/** "안암3교시 · by 써클" — 첫 화면 · PC 메뉴 아래. onBoard: 어두운 안내판 위 */
export function ByCompany({ onBoard = false, className = '' }: { onBoard?: boolean; className?: string }) {
  return (
    <p className={`text-xs leading-relaxed ${onBoard ? 'text-on-board-2' : 'text-ink-3'} ${className}`}>
      {SERVICE} · by <b className={`font-bold ${onBoard ? 'text-on-board' : 'text-ink-2'}`}>{COMPANY}</b>
    </p>
  )
}

/** "써클 · 만든 사람 유동준 (고려대 SW·AI융합대학원 61기)" — 내 정보 맨 아래. 좁으면 소속 앞에서 줄이 바뀐다 */
export function MadeBy({ className = '' }: { className?: string }) {
  return (
    <p className={`text-xs leading-relaxed text-ink-3 ${className}`}>
      <b className="font-bold text-ink-2">{COMPANY}</b>
      <span aria-hidden> · </span>
      <span className="whitespace-nowrap">
        만든 사람 <b className="font-semibold text-ink-2">{MADE_BY.name}</b>
      </span>{' '}
      <span className="whitespace-nowrap">({MADE_BY.affiliation})</span>
    </p>
  )
}
