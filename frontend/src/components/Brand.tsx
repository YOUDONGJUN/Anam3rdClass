import { Link } from 'react-router'

/** 6호선 노선 배지처럼 생긴 "3" + 이름 */
export function Brand({ subtitle = true }: { subtitle?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2.5 rounded-lg">
      <span className="flex size-8 items-center justify-center rounded-full bg-accent text-[17px] font-black text-on-accent">
        3
      </span>
      <span className="flex flex-col leading-none">
        <span className="text-[17px] font-extrabold tracking-[-0.02em] text-on-board">안암3교시</span>
        {subtitle && <span className="mt-1 text-[11px] font-medium text-on-board-2">SW·AI융합대학원 번개</span>}
      </span>
    </Link>
  )
}
