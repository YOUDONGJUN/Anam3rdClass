import { type ReactNode, useLayoutEffect } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigationType, useParams } from 'react-router'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { Button, Spinner } from './components/ui'
import { useSlowServer } from './lib/hooks'
import { isStaff } from './lib/person'
import { AdminPage } from './pages/AdminPage'
import { ChatPage } from './pages/ChatPage'
import { FeedPage } from './pages/FeedPage'
import { MemoriesPage } from './pages/MemoriesPage'
import { MeetupDetailPage } from './pages/MeetupDetailPage'
import { MeetupCreatePage, MeetupEditPage } from './pages/MeetupFormPage'
import { MyMeetupsPage } from './pages/MyMeetupsPage'
import { MyPage } from './pages/MyPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { NoticeCreatePage, NoticeDetailPage, NoticeEditPage, NoticesPage } from './pages/NoticePages'
import { NotificationsPage } from './pages/NotificationsPage'
import { EmailLoginPage, KakaoCallbackPage, PasswordPage, SignupPage } from './pages/SignupPages'
import { RankingPage } from './pages/RankingPage'
import { PrivacyPage, TermsGate, TermsPage } from './pages/TermsPages'
import { WelcomePage } from './pages/WelcomePage'

/** 로그인 상태를 확인하는 동안. 서버가 잠들어 있으면 깨우는 중, 인터넷이 안 되면 그렇다고 알려준다 */
function BootSplash() {
  const slow = useSlowServer()
  const { offline, retry } = useAuth()
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-5 px-6 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-accent text-2xl font-black text-on-accent">3</span>
      {offline ? (
        <div className="flex flex-col items-center gap-3">
          <p className="text-[15px] font-semibold text-ink">인터넷 연결을 확인해 주세요</p>
          <p className="text-sm text-ink-3">연결되면 저절로 들어가요</p>
          <Button variant="secondary" onClick={retry}>
            다시 시도
          </Button>
        </div>
      ) : (
        <Spinner label={slow ? '서버를 깨우는 중이에요. 최대 1분 걸릴 수 있어요' : '들어가는 중'} />
      )}
    </div>
  )
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { status, member } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <BootSplash />
  if (status === 'guest') return <Navigate to="/welcome" replace state={{ from: location.pathname + location.search }} />
  // 약관이 생기기 전에 가입한 원우는 먼저 동의한다
  if (member && !member.termsAgreed) return <TermsGate />
  return children
}

function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <BootSplash />
  if (status === 'authed') return <Navigate to="/" replace />
  return children
}

/** 상세에서 다른 상세로 바로 가면 앞 화면의 상태(도장·후기·신고 창)를 버린다 */
function MeetupDetailRoute() {
  const { id } = useParams()
  return <MeetupDetailPage key={id} />
}

/** 다른 톡방으로 바로 가면(알림) 앞 톡방의 상태를 버린다 */
function ChatRoute() {
  const { id } = useParams()
  return <ChatPage key={id} />
}

/** 교수·대학원장만 */
function RequireStaff({ children }: { children: ReactNode }) {
  const { member } = useAuth()
  if (!isStaff(member?.role)) return <NotFoundPage />
  return children
}

/**
 * 다른 화면으로 가면 맨 위부터 보여 준다. 뒤로·앞으로(POP)는 브라우저가 보던 자리로 돌려 놓게 둔다.
 * 같은 화면 안에서 주소의 ?tab= 만 바뀔 때는 그대로 둔다
 */
function ScrollToTop() {
  const { pathname } = useLocation()
  const type = useNavigationType()
  useLayoutEffect(() => {
    if (type !== 'POP') window.scrollTo(0, 0)
  }, [pathname, type])
  return null
}

export function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route
          path="/welcome"
          element={
            <GuestOnly>
              <WelcomePage />
            </GuestOnly>
          }
        />
        {/* 약관은 로그인 없이도 본다 (가입 화면에서 연다) */}
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        {/* 카카오가 돌려보내는 곳 — 로그인 처리 중이라 GuestOnly 로 감싸지 않는다 */}
        <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
        <Route
          path="/signup"
          element={
            <GuestOnly>
              <SignupPage />
            </GuestOnly>
          }
        />
        <Route
          path="/login/email"
          element={
            <GuestOnly>
              <EmailLoginPage />
            </GuestOnly>
          }
        />
        {/* 비밀번호 찾기(로그아웃 상태) · 비밀번호 만들기(내 정보에서, 로그인한 채로) 둘 다 여기 */}
        <Route path="/password" element={<PasswordPage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route index element={<FeedPage />} />
          <Route path="meetups/new" element={<MeetupCreatePage />} />
          <Route path="meetups/:id" element={<MeetupDetailRoute />} />
          <Route path="meetups/:id/edit" element={<MeetupEditPage />} />
          <Route path="meetups/:id/chat" element={<ChatRoute />} />
          <Route path="memories" element={<MemoriesPage />} />
          <Route path="notices" element={<NoticesPage />} />
          <Route path="notices/new" element={<NoticeCreatePage />} />
          <Route path="notices/:id" element={<NoticeDetailPage />} />
          <Route path="notices/:id/edit" element={<NoticeEditPage />} />
          <Route path="my-meetups" element={<MyMeetupsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="ranking" element={<RankingPage />} />
          <Route path="me" element={<MyPage />} />
          <Route
            path="admin"
            element={
              <RequireStaff>
                <AdminPage />
              </RequireStaff>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </>
  )
}
