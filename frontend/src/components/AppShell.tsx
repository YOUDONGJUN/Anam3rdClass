import { useEffect } from 'react'
import { NavLink, Outlet, useLocation, Link } from 'react-router'
import { Bell, CircleUser, Images, LoaderCircle, LogOut, Megaphone, Plus, ShieldCheck, Ticket, TrainFront, Trophy, type LucideIcon } from 'lucide-react'
import { useAdminStats, useChatRooms, useMySettlements, useUnreadNotifications } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { useToast } from './toastContext'
import { useSlowServer } from '../lib/hooks'
import { SERVICE } from '../lib/credit'
import { useServiceName } from '../lib/useServiceName'
import { affiliation, isStaff } from '../lib/person'
import { Brand } from './Brand'
import { FeedbackPrompter } from './FeedbackSheet'
import { LiveBridge } from './LiveBridge'
import { ByCompany } from './MadeBy'
import { RoleTag } from './PersonName'
import { SchoolBadge } from './SchoolBadge'
import { buttonClass } from './styles'
import { PullToRefresh } from './PullToRefresh'

interface NavItem {
  to: string
  label: string
  Icon: LucideIcon
  end?: boolean
  /** 처리할 일 수 (관리: 처리 안 된 신고 · 내 3교시: 안 읽은 톡 + 보낼 정산) */
  badge?: number
  /** 화면 읽기 프로그램용 배지 이름 ("처리할 신고") */
  badgeLabel?: string
}

function NavBadge({ count, label = '처리할 신고', className = '' }: { count: number; label?: string; className?: string }) {
  return (
    <span
      className={`tabular inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1.5 text-[11px] leading-none font-bold text-white ${className}`}
      aria-label={`${label} ${count}건`}
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

/** 브라우저 탭 제목도 우리 학교 화면 이름으로 (로그아웃하면 공식 이름으로 되돌린다) */
function useDocumentTitle() {
  const name = useServiceName()
  useEffect(() => {
    document.title = `${name} · 수업 끝나고 번개`
    return () => {
      document.title = `${SERVICE} · 수업 끝나고 번개`
    }
  }, [name])
}

export function AppShell() {
  useDocumentTitle()
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
  // 내가 보낼 정산이 있으면 내 3교시에 숫자
  const dues = useMySettlements(Boolean(member)).data?.length
  // 안 읽은 알림 수 (웹은 1분마다, 앱은 푸시가 오면 바로 다시 센다)
  const unread = useUnreadNotifications(Boolean(member)).data
  // 안 읽은 톡 (알림을 끈 톡방은 빼고). 실시간 연결이 오면 바로 다시 센다
  const unreadChats = useChatRooms(Boolean(member)).data?.totalUnread ?? 0
  const mineBadge = (dues ?? 0) + unreadChats

  const feed: NavItem = { to: '/', label: '3교시', Icon: TrainFront, end: true }
  const mine: NavItem = {
    to: '/my-meetups',
    label: '내 3교시',
    Icon: Ticket,
    badge: mineBadge,
    badgeLabel: unreadChats > 0 ? '안 읽은 톡·보낼 정산' : '보낼 정산',
  }
  const memories: NavItem = { to: '/memories', label: '추억', Icon: Images }
  const alerts: NavItem = { to: '/notifications', label: '알림', Icon: Bell, badge: unread, badgeLabel: '새 알림' }
  const notices: NavItem = { to: '/notices', label: '공지', Icon: Megaphone }
  const ranking: NavItem = { to: '/ranking', label: '랭킹', Icon: Trophy }
  const me: NavItem = { to: '/me', label: '내 정보', Icon: CircleUser }
  const admin: NavItem = { to: '/admin', label: '관리', Icon: ShieldCheck, badge: openReports }
  /** 데스크톱 왼쪽 레일: 관리까지 다 */
  const railItems: NavItem[] = [feed, mine, memories, alerts, notices, ranking, me]
  if (staff) railItems.push(admin)
  // 관리 화면은 표·카드가 많아 더 넓게
  const wide = pathname.startsWith('/admin')
  // 3교시 상세: 하단 메뉴 대신 그 3교시의 행동 버튼(나도 콜 …)만 바닥에 둔다
  const detail = /^\/meetups\/\d+$/.test(pathname)
  /** 톡방: 화면 전체를 톡방이 쓴다 (머리·하단 메뉴 없이, 자기 머리와 입력창) */
  const chat = /^\/meetups\/\d+\/chat$/.test(pathname)
  /** 쓰는 화면: 하단 메뉴가 제출 버튼을 가리고, 열기 화면에 '열기' 버튼이 또 있어서 감춘다 */
  const writing = /^\/(meetups|notices)\/(new|\d+\/edit)$/.test(pathname)
  // 안내판이 머리에서 이어지는 화면은 머리 아래 띠를 빼서 한 장으로 보이게
  const continuesBoard = pathname === '/' || pathname === '/my-meetups' || pathname === '/ranking' || detail

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
          {railItems.map(({ to, label, Icon, end, badge, badgeLabel }) => (
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
                  {!!badge && <NavBadge count={badge} label={badgeLabel} className="ml-auto" />}
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
            <ByCompany onBoard className="mt-4" />
          </div>
        )}
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* 모바일: 상단 안내판 + 6호선 띠. 오른쪽은 공지 · 랭킹 · (교수·대학원장) 관리 · 알림 — 로그아웃은 잘못 누르기 쉬워 내 정보에만 */}
        <header className={`sticky top-0 z-30 bg-board pt-[env(safe-area-inset-top)] lg:hidden ${chat ? 'hidden' : ''}`}>
          <div className="flex h-14 items-center justify-between gap-3 px-4">
            <Brand />
            {member && (
              <nav aria-label="바로가기" className="-mr-2 flex shrink-0 items-center gap-0.5">
                <HeaderLink item={notices} />
                <HeaderLink item={ranking} />
                {staff && <HeaderLink item={admin} />}
                <HeaderLink item={alerts} />
              </nav>
            )}
          </div>
          {!continuesBoard && <div className="h-1 bg-accent" aria-hidden />}
        </header>
        {slow && <SlowServerBanner />}
        {/* 모바일 아래 여백: 하단 메뉴(64px) + 가운데 열기 버튼이 위로 솟은 만큼 마지막 줄이 가리지 않게. 상세는 자기 행동 바만큼 따로 둔다 */}
        <main
          className={
            chat
              ? 'mx-auto w-full max-w-3xl flex-1'
              : `mx-auto w-full flex-1 px-4 pt-6 sm:px-6 lg:px-10 lg:pt-10 lg:pb-16 ${detail || writing ? 'pb-8' : 'pb-32'} ${
                  wide ? 'max-w-5xl' : 'max-w-3xl'
                }`
          }
        >
          <Outlet />
        </main>
      </div>

      {/* 앱을 열면 어제(최근) 성사된 3교시 후기를 한 번 묻는다 */}
      <FeedbackPrompter />
      {/* 앱: 맨 위에서 당기면 새로 불러온다 (톡방은 메시지 목록을 위로 끌어 올리니 끈다) */}
      {!chat && <PullToRefresh />}
      {/* 새 톡 메시지 등을 바로 받는 실시간 연결 */}
      <LiveBridge />

      {/* 모바일: 하단 메뉴 5칸 — 3교시 · 내 3교시(톡방) · (열기) · 추억 · 내 정보. 공지는 위 머리로 */}
      {!detail && !writing && !chat && (
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
            <TabLink item={memories} />
            <TabLink item={me} />
          </div>
        </nav>
      )}
    </div>
  )
}

/** 모바일 상단 오른쪽 아이콘 바로가기 (이름은 아래에 작게) */
function HeaderLink({ item: { to, label, Icon, badge, badgeLabel } }: { item: NavItem }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `relative flex h-11 min-w-11 flex-col items-center justify-center gap-0.5 rounded-lg px-1.5 text-[10px] font-bold ${
          isActive ? 'text-on-board' : 'text-on-board-2 hover:text-on-board'
        }`
      }
    >
      {({ isActive }) => (
        <>
          <Icon className={`size-5 ${isActive ? 'text-accent' : ''}`} aria-hidden />
          {label}
          {!!badge && <NavBadge count={badge} label={badgeLabel} className="absolute -top-0.5 right-0" />}
        </>
      )}
    </NavLink>
  )
}

function TabLink({ item: { to, label, Icon, end, badge, badgeLabel } }: { item: NavItem }) {
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
            {!!badge && <NavBadge count={badge} label={badgeLabel} className="absolute -top-1.5 left-3.5" />}
          </span>
          {label}
        </>
      )}
    </NavLink>
  )
}
