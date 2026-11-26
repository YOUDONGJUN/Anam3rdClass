import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { CircleAlert, Check } from 'lucide-react'
import { ToastContext, type ToastApi, type ToastTone } from './toastContext'

interface Toast {
  id: number
  message: string
  tone: ToastTone
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const show = useCallback<ToastApi['show']>((message, tone = 'default') => {
    const id = nextId.current++
    setToasts((list) => [...list.slice(-2), { id, message, tone }])
    window.setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), tone === 'error' ? 5500 : 3200)
  }, [])

  const api = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-[calc(96px+env(safe-area-inset-bottom))] z-50 flex flex-col items-center gap-2 px-4 lg:bottom-8"
      >
        {/* 화면 위쪽(뒤로 버튼)을 덮지 않게 아래에 띄우고, 눌러도 뒤의 버튼이 눌린다 (알림 메시지에는 누를 것이 없다) */}
        {toasts.map((t) => (
          <div
            key={t.id}
            className="toast-in flex max-w-md items-center gap-2.5 rounded-xl bg-board px-4 py-3 text-[15px] font-medium text-on-board shadow-[0_8px_24px_-6px_rgb(0_0_0/0.35)]"
          >
            {t.tone === 'ok' && <Check className="size-4 shrink-0 text-ok" strokeWidth={3} aria-hidden />}
            {t.tone === 'error' && <CircleAlert className="size-4 shrink-0 text-danger" aria-hidden />}
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
