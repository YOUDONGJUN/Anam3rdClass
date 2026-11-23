import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { Spinner } from './components/ui'
import { useSlowServer } from './lib/hooks'
import { AdminPage } from './pages/AdminPage'
import { FeedPage } from './pages/FeedPage'
import { MeetupDetailPage } from './pages/MeetupDetailPage'
import { MeetupCreatePage, MeetupEditPage } from './pages/MeetupFormPage'
import { MyPage } from './pages/MyPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { NoticeCreatePage, NoticeDetailPage, NoticeEditPage, NoticesPage } from './pages/NoticePages'
import { WelcomePage } from './pages/WelcomePage'

/** 로그인 상태를 확인하는 동안. 무료 서버가 잠들어 있으면 깨우는 중이라고 알려준다 */
function BootSplash() {
  const slow = useSlowServer()
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-5 px-6 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-accent text-2xl font-black text-on-accent">3</span>
      <Spinner label={slow ? '무료 서버를 깨우는 중이에요. 최대 1분 걸릴 수 있어요' : '들어가는 중'} />
    </div>
  )
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <BootSplash />
  if (status === 'guest') return <Navigate to="/welcome" replace state={{ from: location.pathname + location.search }} />
  return children
}

function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <BootSplash />
  if (status === 'authed') return <Navigate to="/" replace />
  return children
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { member } = useAuth()
  if (member?.role !== 'ADMIN') return <NotFoundPage />
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
        <Route path="me" element={<MyPage />} />
        <Route
          path="admin"
          element={
            <RequireAdmin>
              <AdminPage />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
