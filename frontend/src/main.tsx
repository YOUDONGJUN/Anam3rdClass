import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { ToastProvider } from './components/ToastProvider'
import { NativeBridge } from './components/NativeBridge'
import { UpdateWatcher } from './components/UpdateWatcher'
import { App } from './App'
import { applyTheme, loadPrefs, loadSchoolCode } from './lib/theme'
import { ThemeProvider } from './theme/ThemeProvider'
import './index.css'

// 첫 화면부터 마지막 학교 색·밝기로 (index.html 스크립트가 이미 했으면 같은 값을 한 번 더 붙인다)
applyTheme(loadPrefs(), loadSchoolCode())

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // 4xx 는 다시 시도해도 같으니 네트워크 오류만 한 번 더
      retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <UpdateWatcher />
        <AuthProvider>
          <ThemeProvider>
            <ToastProvider>
              <NativeBridge />
              <App />
            </ToastProvider>
          </ThemeProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
