import { createContext, useContext } from 'react'

export type ToastTone = 'default' | 'ok' | 'error'

export interface ToastApi {
  show: (message: string, tone?: ToastTone) => void
}

export const ToastContext = createContext<ToastApi | null>(null)

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('ToastProvider 안에서만 쓸 수 있어요')
  return ctx
}
