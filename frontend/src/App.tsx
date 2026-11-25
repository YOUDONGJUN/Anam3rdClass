import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { Spinner } from './components/ui'
import { useSlowServer } from './lib/hooks'
import { isStaff } from './lib/person'
import { AdminPage } from './pages/AdminPage'
import { FeedPage } from './pages/FeedPage'
import { MeetupDetailPage } from './pages/MeetupDetailPage'
import { MeetupCreatePage, MeetupEditPage } from './pages/MeetupFormPage'
import { MyMeetupsPage } from './pages/MyMeetupsPage'
import { MyPage } from './pages/MyPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { NoticeCreatePage, NoticeDetailPage, NoticeEditPage, NoticesPage } from './pages/NoticePages'
import { EmailLoginPage, KakaoCallbackPage, PasswordPage, SignupPage } from './pages/SignupPages'
import { PrivacyPage, TermsGate, TermsPage } from './pages/TermsPages'
import { WelcomePage } from './pages/WelcomePage'

/** 로그인 상태를 확인하는 동안. 서버가 잠들어 있으면 깨우는 중이라고 알려준다 */
function BootSplash() {
  const slow = useSlowServer()
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-5 px-6 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-accent text-2xl font-black text-on-accent">3</span>
      <Spinner label={slow ? '서버를 깨우는 중이에요. 최대 1분 걸릴 수 있어요' : '들어가는 중'} />
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

/** 교수·대학원장만 */
function RequireStaff({ children }: { children: ReactNode }) {
  const { member } = useAuth()
  if (!isStaff(member?.role)) return <NotFoundPage />
  return children
}

export function App() {
  return (
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
        <Route path="meetups/:id" element={<MeetupDetailPage />} />
        <Route path="meetups/:id/edit" element={<MeetupEditPage />} />
        <Route path="notices" element={<NoticesPage />} />
        <Route path="notices/new" element={<NoticeCreatePage />} />
        <Route path="notices/:id" element={<NoticeDetailPage />} />
        <Route path="notices/:id/edit" element={<NoticeEditPage />} />
        <Route path="my-meetups" element={<MyMeetupsPage />} />
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
  )
}
