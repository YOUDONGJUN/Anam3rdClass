import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Download, ImagePlus, X } from 'lucide-react'
import { absoluteUrl } from '../api/client'
import { fetchPhoto } from '../lib/photo'
import { useToast } from './toastContext'

/** 사진 저장: 폰은 공유 창(사진 앱에 저장), PC 는 내려받기 */
async function savePhoto(url: string) {
  const blob = await fetchPhoto(url)
  const file = new File([blob], `anam3-${Date.now()}.jpg`, { type: blob.type || 'image/jpeg' })
  if (navigator.canShare?.({ files: [file] })) {
    await navigator.share({ files: [file] })
    return
  }
  const link = document.createElement('a')
  link.href = URL.createObjectURL(file)
  link.download = file.name
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 10_000)
}

/**
 * 사진 크게 보기. 여러 장이면 옆으로 넘긴다 (화살표 · 키보드 ← →).
 * onAddToMemories 가 있으면 "추억에 올리기" (톡방 사진은 30일 뒤 지워지니까)
 */
export function PhotoViewer({
  urls,
  start = 0,
  onClose,
  onAddToMemories,
}: {
  urls: string[]
  start?: number
  onClose: () => void
  onAddToMemories?: (url: string) => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const [index, setIndex] = useState(start)
  const [saving, setSaving] = useState(false)
  const toast = useToast()
  const url = absoluteUrl(urls[index])
  const many = urls.length > 1

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') setIndex((i) => Math.min(urls.length - 1, i + 1))
      if (e.key === 'ArrowLeft') setIndex((i) => Math.max(0, i - 1))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [urls.length])

  // 손가락으로 옆으로 밀어 넘기기
  const touchX = useRef<number | null>(null)

  async function save() {
    setSaving(true)
    try {
      await savePhoto(url)
    } catch (e) {
      if (!(e instanceof DOMException && e.name === 'AbortError')) toast.show('사진을 저장하지 못했어요', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-label="사진 크게 보기"
      className="m-0 h-dvh max-h-none w-screen max-w-none bg-black p-0 text-white backdrop:bg-black"
    >
      <div className="flex h-full flex-col">
        <div className="flex items-center justify-between gap-2 px-2 pt-[calc(8px+env(safe-area-inset-top))] pb-2">
          <button
            type="button"
            onClick={() => ref.current?.close()}
            aria-label="닫기"
            className="flex size-11 items-center justify-center rounded-full hover:bg-white/10"
          >
            <X className="size-6" aria-hidden />
          </button>
          {many && (
            <span className="tabular text-sm font-semibold text-white/80">
              {index + 1} / {urls.length}
            </span>
          )}
          <div className="flex items-center gap-1">
            {onAddToMemories && (
              <button
                type="button"
                onClick={() => onAddToMemories(url)}
                className="inline-flex h-10 items-center gap-1.5 rounded-full bg-white/12 px-3.5 text-sm font-bold hover:bg-white/20"
              >
                <ImagePlus className="size-4" aria-hidden />
                추억에 올리기
              </button>
            )}
            <button
              type="button"
              onClick={save}
              disabled={saving}
              aria-label="사진 저장"
              className="flex size-11 items-center justify-center rounded-full hover:bg-white/10 disabled:opacity-50"
            >
              <Download className="size-5" aria-hidden />
            </button>
          </div>
        </div>
        <div
          className="relative flex min-h-0 flex-1 items-center justify-center"
          onTouchStart={(e) => (touchX.current = e.touches[0].clientX)}
          onTouchEnd={(e) => {
            if (touchX.current === null) return
            const dx = e.changedTouches[0].clientX - touchX.current
            touchX.current = null
            if (dx < -50) setIndex((i) => Math.min(urls.length - 1, i + 1))
            if (dx > 50) setIndex((i) => Math.max(0, i - 1))
          }}
        >
          <img src={url} alt={`사진 ${index + 1}`} className="max-h-full max-w-full object-contain select-none" />
          {many && index > 0 && (
            <button
              type="button"
              onClick={() => setIndex(index - 1)}
              aria-label="이전 사진"
              className="absolute left-2 hidden size-11 items-center justify-center rounded-full bg-black/40 hover:bg-black/60 sm:flex"
            >
              <ChevronLeft className="size-6" aria-hidden />
            </button>
          )}
          {many && index < urls.length - 1 && (
            <button
              type="button"
              onClick={() => setIndex(index + 1)}
              aria-label="다음 사진"
              className="absolute right-2 hidden size-11 items-center justify-center rounded-full bg-black/40 hover:bg-black/60 sm:flex"
            >
              <ChevronRight className="size-6" aria-hidden />
            </button>
          )}
        </div>
        <div className="h-[calc(16px+env(safe-area-inset-bottom))]" />
      </div>
    </dialog>
  )
}
