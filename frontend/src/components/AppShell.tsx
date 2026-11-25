import { NavLink, Outlet, useLocation, Link } from 'react-router'
import { CircleUser, LoaderCircle, LogOut, Megaphone, Plus, ShieldCheck, Ticket, TrainFront, type LucideIcon } from 'lucide-react'
import { useAdminStats } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { useToast } from './toastContext'
import { useSlowServer } from '../lib/hooks'
import { affiliation, isStaff } from '../lib/person'
import { Brand } from './Brand'
import { MadeBy } from './MadeBy'
import { RoleTag } from './PersonName'
import { SchoolBadge } from './SchoolBadge'
import { buttonClass } from './styles'

interface NavItem {
  to: string
  label: string
  Icon: LucideIcon
  end?: boolean
  /** 처리할 일 수 (관리: 처리 안 된 신고) */
  badge?: number
}

function NavBadge({ count, className = '' }: { count: number; className?: string }) {
  return (
    <span
      className={`tabular inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1.5 text-[11px] leading-none font-bold text-white ${className}`}
      aria-label={`처리할 신고 ${count}건`}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}

function SlowServerBanner() {
  return (
    <div role="status" className="flex items-center justify-center gap-2 bg-exit px-4 py-2 text-sm font-semibold text-on-exit">
      <LoaderCircle className="size-4 animate-spin" aria-hidden />
      서버 응답이 늦어지고 있어요. 잠시만 기다려 주세요
    </div>
  )
}

export function AppShell() {
  const { member, signOut } = useAuth()
  const toast = useToast()
  /** 확인 없이 바로 — 카카오로 금방 다시 들어올 수 있다 */
  const logout = () => {
    signOut()
    toast.show('로그아웃했어요')
  }
  const slow = useSlowServer()
  const { pathname, search } = useLocation()
  // 연합 게시판을 보고 있으면 "3교시 열기"도 연합으로
  const newMeetupPath = pathname === '/' && new URLSearchParams(search).get('board') === 'all' ? '/meetups/new?audience=ALL' : '/meetups/new'

  const staff = isStaff(member?.role)
  const stats = useAdminStats(staff)
  const openReports = staff ? stats.data?.openReports : undefined

  const feed: NavItem = { to: '/', label: '3교시', Icon: TrainFront, end: true }
  const mine: NavItem = { to: '/my-meetups', label: '내 3교시', Icon: Ticket }
  const notices: NavItem = { to: '/notices', label: '공지', Icon: Megaphone }
  // 모바일 하단 메뉴에는 관리 칸이 없다 — 교수·대학원장은 내 정보 › 관리 화면, 처리할 신고 수는 내 정보에 붙는다
  const me: NavItem = { to: '/me', label: '내 정보', Icon: CircleUser, badge: openReports }
  /** 데스크톱 왼쪽 레일: 관리까지 다 */
  const railItems: NavItem[] = [feed, mine, notices, { ...me, badge: undefined }]
  if (staff) railItems.push({ to: '/admin', label: '관리', Icon: ShieldCheck, badge: openReports })
  // 관리 화면은 표·카드가 많아 더 넓게
  const wide = pathname.startsWith('/admin')
  // 3교시 상세: 하단 메뉴 대신 그 3교시의 행동 버튼(나도 콜 …)만 바닥에 둔다
  const detail = /^\/meetups\/\d+$/.test(pathname)
  // 안내판이 머리에서 이어지는 화면은 머리 아래 띠를 빼서 한 장으로 보이게
  const continuesBoard = pathname === '/' || pathname === '/my-meetups' || detail

  return (
    <div className="min-h-dvh lg:flex">
      {/* 데스크톱: 왼쪽 안내판 레일 */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col bg-board text-on-board lg:flex">
        <div className="px-6 pt-7 pb-6">
          <Brand />
        </div>
        <div className="h-1 bg-accent" aria-hidden />
        <div className="px-5 pt-6">
          <Link to={newMeetupPath} className={buttonClass('primary', 'lg', 'w-full')}>
            <Plus className="size-5" strokeWidth={2.6} aria-hidden />
            3교시 열기
          </Link>
        </div>
        <nav aria-label="주 메뉴" className="mt-6 flex flex-col gap-1 px-3">
          {railItems.map(({ to, label, Icon, end, badge }) => (
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
                  {!!badge && <NavBadge count={badge} className="ml-auto" />}
                </>
              )}
            </NavLink>
          ))}
        </nav>
        {member && (
          <div className="mt-auto border-t border-board-line px-6 py-5">
            <p className="flex flex-wrap items-center gap-1.5 text-[15px] font-semibold text-on-board">
              {member.name}
              <SchoolBadge school={member.school} />
              {member.role !== 'USER' && <RoleTag role={member.role} />}
            </p>
            <p className="mt-0.5 truncate text-sm text-on-board-2">
              {affiliation(member.cohortLabel, member.departmentLabel)}
            </p>
            <button
              type="button"
              onClick={logout}
              className="-ml-2 mt-3 inline-flex h-9 items-center gap-1.5 rounded-lg px-2 text-sm font-semibold text-on-board-2 hover:bg-board-2 hover:text-on-board"
            >
              <LogOut className="size-4" aria-hidden />
              로그아웃
            </button>
            <MadeBy onBoard className="mt-4" />
          </div>
        )}
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* 모바일: 상단 안내판 + 6호선 띠 */}
        <header className="sticky top-0 z-30 bg-board pt-[env(safe-area-inset-top)] lg:hidden">
          <div className="flex h-14 items-center justify-between gap-3 px-4">
            <Brand />
            {member && (
              <button
                type="button"
                onClick={logout}
                className="-mr-2 inline-flex h-10 shrink-0 items-center gap-1 rounded-lg px-2 text-[13px] font-semibold text-on-board-2 hover:text-on-board"
              >
                <LogOut className="size-4" aria-hidden />
                로그아웃
              </button>
            )}
          </div>
          {!continuesBoard && <div className="h-1 bg-accent" aria-hidden />}
        </header>
        {slow && <SlowServerBanner />}
        {/* 모바일 아래 여백: 하단 메뉴(64px) + 가운데 열기 버튼이 위로 솟은 만큼 마지막 줄이 가리지 않게. 상세는 자기 행동 바만큼 따로 둔다 */}
        <main
          className={`mx-auto w-full flex-1 px-4 pt-6 sm:px-6 lg:px-10 lg:pt-10 lg:pb-16 ${detail ? 'pb-8' : 'pb-32'} ${
            wide ? 'max-w-5xl' : 'max-w-3xl'
          }`}
        >
          <Outlet />
        </main>
      </div>

      {/* 모바일: 하단 메뉴 5칸 — 3교시 · 내 3교시 · (열기) · 공지 · 내 정보 */}
      {!detail && (
        <nav
          aria-label="주 메뉴"
          className="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface shadow-[0_-8px_20px_-16px_rgb(0_0_0/0.4)] lg:hidden"
        >
          <div className="grid h-16 grid-cols-5">
            <TabLink item={feed} />
            <TabLink item={mine} />
            <Link
              to={newMeetupPath}
              aria-label="3교시 열기"
              className="group relative flex flex-col items-center justify-end pb-2.5 text-[11px] font-bold text-accent-ink"
            >
              <span className="absolute -top-6 left-1/2 flex size-14 -translate-x-1/2 items-center justify-center rounded-full bg-accent text-on-accent shadow-[0_4px_0_color-mix(in_oklab,var(--accent)_62%,#000),0_0_0_6px_var(--surface)] transition-transform duration-100 group-active:translate-y-[3px]">
                <Plus className="size-[26px]" strokeWidth={2.8} aria-hidden />
              </span>
              열기
            </Link>
            <TabLink item={notices} />
            <TabLink item={me} />
          </div>
        </nav>
      )}
    </div>
  )
}

function TabLink({ item: { to, label, Icon, end, badge } }: { item: NavItem }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `relative flex flex-col items-center justify-center gap-1 text-[11px] ${
          isActive ? 'font-extrabold text-ink' : 'font-semibold text-ink-3'
        }`
      }
    >
      {({ isActive }) => (
        <>
          <span className="relative">
            <Icon className={`size-[22px] ${isActive ? 'text-accent-ink' : ''}`} strokeWidth={isActive ? 2.4 : 2} aria-hidden />
            {!!badge && <NavBadge count={badge} className="absolute -top-1.5 left-3.5" />}
          </span>
          {label}
        </>
      )}
    </NavLink>
  )
}
