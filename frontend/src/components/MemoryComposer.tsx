import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Globe2, ImagePlus, Users, X } from 'lucide-react'
import { useCreateMemory } from '../api/queries'
import type { MemoryVisibility } from '../api/types'
import { MEMORY_SIDE, PER_SEND, shrinkPhoto } from '../lib/photo'
import { PhotoPicks } from './PhotoPicks'
import { Button, FormError } from './ui'
import { inputClass } from './styles'
import { useToast } from './toastContext'

const CAPTION_MAX = 200

const VISIBILITY: { value: MemoryVisibility; label: string; hint: string; Icon: typeof Users }[] = [
  { value: 'PARTICIPANTS', label: '함께한 원우만', hint: '이 3교시에 함께한 원우만 봐요', Icon: Users },
  {
    value: 'PUBLIC',
    label: '모두에게 공개',
    hint: '이 3교시를 볼 수 있는 원우 모두가 추억 탭에서 봐요. 함께하지 않은 원우에게는 이름이 가려져요',
    Icon: Globe2,
  },
]

/**
 * 추억 올리기: 사진 1~5장(3교시마다 모두 10장) + 한 줄 + 공개 범위.
 * 사진은 폰에서 줄여서 올린다. initialPhotos: 톡방 사진에서 "추억에 올리기"로 왔을 때
 */
export function MemoryComposer({
  meetupId,
  photosLeft,
  initialPhotos = [],
  onClose,
  onDone,
}: {
  meetupId: number
  photosLeft: number
  initialPhotos?: Blob[]
  onClose: () => void
  onDone?: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const create = useCreateMemory(meetupId)
  const toast = useToast()
  const max = Math.min(PER_SEND, photosLeft)
  const [photos, setPhotos] = useState<Blob[]>(initialPhotos.slice(0, max))
  const [caption, setCaption] = useState('')
  const [visibility, setVisibility] = useState<MemoryVisibility>('PARTICIPANTS')
  const [preparing, setPreparing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  async function pick(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length === 0) return
    const room = max - photos.length
    if (files.length > room) toast.show(`${room}장까지 더 고를 수 있어요`)
    setPreparing(true)
    setError(null)
    try {
      const shrunk = await Promise.all(files.slice(0, room).map((f) => shrinkPhoto(f, MEMORY_SIDE, 0.85)))
      setPhotos((prev) => [...prev, ...shrunk].slice(0, max))
    } catch (err) {
      setError(err instanceof Error ? err.message : '사진을 열지 못했어요')
    } finally {
      setPreparing(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (photos.length === 0) {
      setError('사진을 한 장 이상 골라 주세요')
      return
    }
    setError(null)
    create.mutate(
      { photos, caption: caption.trim(), visibility },
      {
        onSuccess: () => {
          toast.show('추억을 남겼어요 📸', 'ok')
          onDone?.()
          ref.current?.close()
        },
        onError: (err) => setError(err.message),
      },
    )
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="memory-title"
      className="m-auto max-h-[92dvh] w-[min(32rem,calc(100%-1.5rem))] overflow-y-auto rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="sticky top-0 z-10 flex items-center justify-between gap-3 border-b border-line bg-surface px-5 py-4">
        <div>
          <h2 id="memory-title" className="text-lg font-extrabold">
            추억 남기기
          </h2>
          <p className="text-sm text-ink-3">인생네컷, 단체 사진, 그날의 한 장</p>
        </div>
        <button
          type="button"
          onClick={() => ref.current?.close()}
          aria-label="닫기"
          className="flex size-10 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>

      <form onSubmit={submit} className="flex flex-col gap-5 px-5 py-5" noValidate>
        <div className="flex flex-col gap-2.5">
          <div className="flex items-baseline justify-between">
            <span className="text-[15px] font-bold">사진</span>
            <span className="tabular text-sm text-ink-3">
              {photos.length}/{max}장 · 이 3교시에 {photosLeft}장 남음
            </span>
          </div>
          <PhotoPicks photos={photos} onRemove={(i) => setPhotos((p) => p.filter((_, j) => j !== i))} />
          {photos.length < max && (
            <Button variant="secondary" loading={preparing} onClick={() => fileRef.current?.click()} className="self-start">
              <ImagePlus className="size-4" aria-hidden />
              사진 고르기
            </Button>
          )}
          <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={pick} />
        </div>

        <label className="flex flex-col gap-2">
          <span className="flex items-baseline justify-between text-[15px] font-bold">
            한 줄
            <span className="tabular text-sm font-medium text-ink-3">
              {caption.length}/{CAPTION_MAX}
            </span>
          </span>
          <textarea
            value={caption}
            onChange={(e) => setCaption(e.target.value.slice(0, CAPTION_MAX))}
            rows={2}
            placeholder="예: 2차까지 완주! 다음엔 노래방 가요"
            className={`${inputClass} h-auto resize-none py-3`}
          />
        </label>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[15px] font-bold">누가 볼까요</legend>
          {VISIBILITY.map(({ value, label, hint, Icon }) => (
            <label
              key={value}
              className={`flex cursor-pointer items-start gap-3 rounded-2xl border px-4 py-3 transition-colors ${
                visibility === value ? 'border-accent bg-accent/8' : 'border-line hover:bg-surface-2'
              }`}
            >
              <input
                type="radio"
                name="visibility"
                value={value}
                checked={visibility === value}
                onChange={() => setVisibility(value)}
                className="mt-1 accent-(--accent)"
              />
              <span className="flex flex-col gap-0.5">
                <span className="flex items-center gap-1.5 text-[15px] font-semibold">
                  <Icon className="size-4 text-ink-3" aria-hidden />
                  {label}
                </span>
                <span className="text-sm text-ink-3">{hint}</span>
              </span>
            </label>
          ))}
        </fieldset>

        <FormError message={error} />
        <Button type="submit" size="lg" loading={create.isPending} disabled={preparing || photos.length === 0}>
          추억 올리기
        </Button>
      </form>
    </dialog>
  )
}
