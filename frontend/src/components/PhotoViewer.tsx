import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type WheelEvent } from 'react'
import { ChevronLeft, ChevronRight, Download, ImagePlus, X } from 'lucide-react'
import { absoluteUrl } from '../api/client'
import { savePhotoFile } from '../lib/native'
import { fetchPhoto } from '../lib/photo'
import { clampZoom, NO_ZOOM, pinch, spanOf, toggleZoom, zoomAt, type Point, type Span, type Zoom } from '../lib/zoom'
import { useToast } from './toastContext'

/** 이만큼 옆으로 밀면 다음 사진 */
const SWIPE = 50
/** 이만큼 안 움직였으면 탭 */
const TAP_SLOP = 8
/** 두 번 탭: 이 시간 · 거리 안 */
const DOUBLE_TAP_MS = 300
const DOUBLE_TAP_SLOP = 30

type Gesture =
  | { kind: 'pinch'; start: Zoom; from: Span }
  | { kind: 'drag'; start: Zoom; origin: Point; moved: boolean }

/**
 * 사진 크게 보기. 두 손가락으로 벌리거나 두 번 탭하면 확대, 확대한 채로 끌면 옮긴다 (PC: 휠 · 더블클릭).
 * 여러 장이면 옆으로 밀어 넘긴다 (확대하지 않았을 때) · 화살표 · 키보드 ← →.
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
  const boxRef = useRef<HTMLDivElement>(null)
  const imgRef = useRef<HTMLImageElement>(null)
  const [index, setIndex] = useState(start)
  const [zoom, setZoom] = useState<Zoom>(NO_ZOOM)
  /** 두 번 탭처럼 한 번에 바뀔 때만 부드럽게 (손가락을 따라갈 때는 바로) */
  const [smooth, setSmooth] = useState(false)
  const [saving, setSaving] = useState(false)
  const toast = useToast()
  const url = absoluteUrl(urls[index])
  const many = urls.length > 1

  const zoomRef = useRef<Zoom>(NO_ZOOM)
  const pointers = useRef(new Map<number, Point>())
  const gesture = useRef<Gesture | null>(null)
  const lastTap = useRef<{ time: number; at: Point } | null>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  function apply(next: Zoom, animate = false) {
    const img = imgRef.current
    const box = boxRef.current
    if (!img || !box) return
    const z = clampZoom(
      next,
      { width: img.offsetWidth, height: img.offsetHeight },
      { width: box.clientWidth, height: box.clientHeight },
    )
    zoomRef.current = z
    setSmooth(animate)
    setZoom(z)
  }

  /** 다른 사진으로: 확대는 풀고 */
  function go(next: number) {
    const i = Math.max(0, Math.min(urls.length - 1, next))
    if (i === index) return
    zoomRef.current = NO_ZOOM
    setZoom(NO_ZOOM)
    setIndex(i)
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
        zoomRef.current = NO_ZOOM
        setZoom(NO_ZOOM)
        setIndex((i) => Math.max(0, Math.min(urls.length - 1, i + (e.key === 'ArrowRight' ? 1 : -1))))
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [urls.length])

  /** 사진 칸 가운데를 (0,0) 으로 */
  function local(e: { clientX: number; clientY: number }): Point {
    const r = boxRef.current?.getBoundingClientRect()
    if (!r) return { x: 0, y: 0 }
    return { x: e.clientX - r.left - r.width / 2, y: e.clientY - r.top - r.height / 2 }
  }

  /** 손가락 수가 바뀌면 그 상태에서 새로 시작 (두 손가락 → 한 손가락이면 이어서 옮기기) */
  function begin(fromMulti: boolean) {
    const pts = [...pointers.current.values()]
    if (pts.length >= 2) gesture.current = { kind: 'pinch', start: zoomRef.current, from: spanOf(pts[0], pts[1]) }
    else if (pts.length === 1) gesture.current = { kind: 'drag', start: zoomRef.current, origin: pts[0], moved: fromMulti }
    else gesture.current = null
  }

  function onPointerDown(e: ReactPointerEvent<HTMLDivElement>) {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    if ((e.target as HTMLElement).closest('button')) return // 화살표 단추
    e.currentTarget.setPointerCapture(e.pointerId)
    pointers.current.set(e.pointerId, local(e))
    begin(false)
  }

  function onPointerMove(e: ReactPointerEvent<HTMLDivElement>) {
    if (!pointers.current.has(e.pointerId)) return
    pointers.current.set(e.pointerId, local(e))
    const g = gesture.current
    if (!g) return
    const pts = [...pointers.current.values()]
    if (g.kind === 'pinch' && pts.length >= 2) {
      apply(pinch(g.start, g.from, spanOf(pts[0], pts[1])))
    } else if (g.kind === 'drag') {
      const dx = pts[0].x - g.origin.x
      const dy = pts[0].y - g.origin.y
      if (Math.hypot(dx, dy) > TAP_SLOP) g.moved = true
      if (g.start.scale > 1) apply({ ...g.start, x: g.start.x + dx, y: g.start.y + dy })
    }
  }

  function onPointerUp(e: ReactPointerEvent<HTMLDivElement>, cancelled = false) {
    if (!pointers.current.has(e.pointerId)) return
    const at = local(e)
    const wasMulti = pointers.current.size > 1
    pointers.current.delete(e.pointerId)
    const g = gesture.current
    if (!cancelled && g?.kind === 'drag' && pointers.current.size === 0) {
      if (!g.moved) {
        tap(at)
      } else if (g.start.scale <= 1) {
        const dx = at.x - g.origin.x
        if (dx < -SWIPE) go(index + 1)
        if (dx > SWIPE) go(index - 1)
      }
    }
    begin(wasMulti)
  }

  function tap(at: Point) {
    const now = Date.now()
    const last = lastTap.current
    if (last && now - last.time < DOUBLE_TAP_MS && Math.hypot(at.x - last.at.x, at.y - last.at.y) < DOUBLE_TAP_SLOP) {
      lastTap.current = null
      apply(toggleZoom(zoomRef.current, at), true)
    } else {
      lastTap.current = { time: now, at }
    }
  }

  function onWheel(e: WheelEvent<HTMLDivElement>) {
    const z = zoomRef.current
    apply(zoomAt(z, local(e), z.scale * (e.deltaY < 0 ? 1.2 : 1 / 1.2)))
  }

  async function save() {
    setSaving(true)
    try {
      const blob = await fetchPhoto(url)
      const result = await savePhotoFile(blob, `anam3-${Date.now()}.jpg`)
      if (result === 'saved') toast.show('사진 앱에 저장했어요')
    } catch (e) {
      toast.show(e instanceof Error && e.message ? e.message : '사진을 저장하지 못했어요', 'error')
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
        <div className="relative z-10 flex items-center justify-between gap-2 bg-black px-2 pt-[calc(8px+env(safe-area-inset-top))] pb-2">
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
              className="inline-flex h-10 items-center gap-1.5 rounded-full px-3 text-sm font-bold hover:bg-white/10 disabled:opacity-50"
            >
              <Download className="size-4" aria-hidden />
              저장
            </button>
          </div>
        </div>
        <div
          ref={boxRef}
          className="relative flex min-h-0 flex-1 touch-none items-center justify-center overflow-hidden select-none"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={(e) => onPointerUp(e)}
          onPointerCancel={(e) => onPointerUp(e, true)}
          onWheel={onWheel}
        >
          <img
            ref={imgRef}
            key={url}
            src={url}
            alt={`사진 ${index + 1}`}
            draggable={false}
            className="max-h-full max-w-full object-contain will-change-transform"
            style={{
              transform: `translate(${zoom.x}px, ${zoom.y}px) scale(${zoom.scale})`,
              transition: smooth ? 'transform 0.2s ease-out' : 'none',
            }}
          />
          {many && index > 0 && (
            <button
              type="button"
              onClick={() => go(index - 1)}
              aria-label="이전 사진"
              className="absolute left-2 hidden size-11 items-center justify-center rounded-full bg-black/40 hover:bg-black/60 sm:flex"
            >
              <ChevronLeft className="size-6" aria-hidden />
            </button>
          )}
          {many && index < urls.length - 1 && (
            <button
              type="button"
              onClick={() => go(index + 1)}
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
