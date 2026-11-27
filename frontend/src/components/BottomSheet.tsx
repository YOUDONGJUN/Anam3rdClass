import { useEffect, useId, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

/**
 * 아래에서 올라오는 창 (넓은 화면에서는 가운데 창). 머리(제목 · 닫기)와 바닥(버튼)은 고정, 가운데만 스크롤.
 * 열려 있는 동안만 그린다: 그리면 열리고, 닫히면(X · 바깥 누르기 · 뒤로·ESC) onClose
 */
export function BottomSheet({
  title,
  onClose,
  children,
  footer,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  /** 바닥 버튼 (닫으려면 부모가 이 창을 그리지 않으면 된다) */
  footer?: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  const close = () => ref.current?.close()

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      // 창 바깥(어두운 곳)을 누르면 닫는다
      onClick={(e) => {
        if (e.target === ref.current) close()
      }}
      aria-labelledby={titleId}
      className="sheet-up mx-auto mt-auto mb-0 w-full max-w-lg overflow-hidden rounded-t-[26px] border border-line bg-surface p-0 text-ink backdrop:bg-black/50 sm:mb-auto sm:rounded-3xl"
    >
      <div className="flex max-h-[85dvh] flex-col">
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-line py-2 pr-2 pl-5">
          <h2 id={titleId} className="text-lg font-extrabold">
            {title}
          </h2>
          <button
            type="button"
            aria-label="닫기"
            onClick={close}
            className="flex size-11 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
          >
            <X className="size-5" aria-hidden />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4">{children}</div>
        {footer && (
          <div className="shrink-0 border-t border-line px-5 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] sm:pb-4">
            {footer}
          </div>
        )}
        {!footer && <div className="h-[env(safe-area-inset-bottom)] shrink-0" aria-hidden />}
      </div>
    </dialog>
  )
}
