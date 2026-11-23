import { Link } from 'react-router'
import { buttonClass } from '../components/styles'

export function NotFoundPage() {
  return (
    <div className="flex flex-col items-center gap-4 py-20 text-center">
      <p className="tabular text-6xl font-black tracking-[-0.04em] text-ink-3">404</p>
      <p className="text-lg font-bold text-ink">여기는 없는 역이에요</p>
      <p className="text-[15px] text-ink-2">주소가 바뀌었거나 지워진 페이지예요.</p>
      <Link to="/" className={buttonClass('primary')}>
        3교시 목록으로
      </Link>
    </div>
  )
}
