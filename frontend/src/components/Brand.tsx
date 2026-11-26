import { Link } from 'react-router'
import { useServiceName } from '../lib/useServiceName'

/** 6호선 노선 배지처럼 생긴 "3" + 이름 (로그인하면 우리 학교 동네: 신촌3교시) */
export function Brand({ subtitle = true }: { subtitle?: boolean }) {
  const name = useServiceName()
  return (
    <Link to="/" className="flex items-center gap-2.5 rounded-lg">
      <span className="flex size-8 items-center justify-center rounded-full bg-accent text-[17px] font-black text-on-accent shadow-[inset_0_-2px_0_rgb(0_0_0/0.12),0_2px_0_rgb(0_0_0/0.22)]">
        3
      </span>
      <span className="flex flex-col leading-none">
        <span className="text-[17px] font-extrabold tracking-[-0.02em] text-on-board">{name}</span>
        {subtitle && <span className="mt-1 text-[11px] font-medium text-on-board-2">IT연합회 대학원 번개</span>}
      </span>
    </Link>
  )
}
