import { NavLink, Outlet, useLocation, Link } from 'react-router'
import { CircleUser, LoaderCircle, Megaphone, Plus, ShieldCheck, TrainFront, type LucideIcon } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useSlowServer } from '../lib/hooks'
import { Brand } from './Brand'
import { buttonClass } from './styles'

interface NavItem {
  to: string
  label: string
  Icon: LucideIcon
  end?: boolean
}

function SlowServerBanner() {
  return (
    <div role="status" className="flex items-center justify-center gap-2 bg-exit px-4 py-2 text-sm font-semibold text-on-exit">
      <LoaderCircle className="size-4 animate-spin" aria-hidden />
      서버를 깨우는 중이에요. 무료 서버라 처음엔 최대 1분 걸릴 수 있어요
    </div>
  )
}

export function AppShell() {
  const { member } = useAuth()
  const slow = useSlowServer()
  const { pathname } = useLocation()

  const items: NavItem[] = [
    { to: '/', label: '번개', Icon: TrainFront, end: true },
    { to: '/notices', label: '공지', Icon: Megaphone },
    { to: '/me', label: '내 정보', Icon: CircleUser },
  ]
  if (member?.role === 'ADMIN') items.push({ to: '/admin', label: '운영', Icon: ShieldCheck })

  const showFab = pathname === '/' || pathname === '/me'

  return (
    <div className="min-h-dvh lg:flex">
      {/* 데스크톱: 왼쪽 안내판 레일 */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col bg-board text-on-board lg:flex">
        <div className="px-6 pt-7 pb-6">
          <Brand />
        </div>
        <div className="h-1 bg-accent" aria-hidden />
        <div className="px-5 pt-6">
          <Link to="/meetups/new" className={buttonClass('primary', 'lg', 'w-full')}>
            <Plus className="size-5" strokeWidth={2.6} aria-hidden />
            번개 열기
          </Link>
        </div>
        <nav aria-label="주 메뉴" className="mt-6 flex flex-col gap-1 px-3">
          {items.map(({ to, label, Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex h-11 items-center gap-3 rounded-xl px-3 text-[15px] font-semibold transition-colors ${
                  isActive ? 'bg-board-2 text-on-board' : 'text-on-board-2 hover:bg-board-2/60 hover:text-on-board'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={`size-5 ${isActive ? 'text-accent' : ''}`} aria-hidden />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </nav>
        {member && (
          <div className="mt-auto border-t border-board-line px-6 py-5">
            <p className="text-[15px] font-semibold text-on-board">{member.name}</p>
            <p className="mt-0.5 text-sm text-on-board-2">
              {member.admissionLabel} · @{member.loginId}
            </p>
          </div>
        )}
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* 모바일: 상단 안내판 + 6호선 띠 */}
        <header className="sticky top-0 z-30 bg-board pt-[env(safe-area-inset-top)] lg:hidden">
          <div className="flex h-14 items-center px-4">
            <Brand />
          </div>
          <div className="h-1 bg-accent" aria-hidden />
        </header>
        {slow && <SlowServerBanner />}
        <main className="mx-auto w-full max-w-3xl flex-1 px-4 pt-6 pb-32 sm:px-6 lg:px-10 lg:pt-10 lg:pb-16">
          <Outlet />
        </main>
      </div>

      {/* 모바일: 하단 탭 */}
      <nav
        aria-label="주 메뉴"
        className="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface lg:hidden"
      >
        <div className="grid h-16" style={{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }}>
          {items.map(({ to, label, Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex flex-col items-center justify-center gap-1 text-[11px] font-semibold ${
                  isActive ? 'text-ink' : 'text-ink-3'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={`size-[22px] ${isActive ? 'text-accent-ink' : ''}`} strokeWidth={isActive ? 2.4 : 2} aria-hidden />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>

      {showFab && (
        <Link
          to="/meetups/new"
          className={buttonClass(
            'primary',
            'lg',
            'fixed right-4 bottom-[calc(80px+env(safe-area-inset-bottom))] z-40 rounded-full! px-5 shadow-[0_10px_28px_-8px_rgb(0_0_0/0.45)] lg:hidden',
          )}
        >
          <Plus className="size-5" strokeWidth={2.6} aria-hidden />
          번개 열기
        </Link>
      )}
    </div>
  )
}
