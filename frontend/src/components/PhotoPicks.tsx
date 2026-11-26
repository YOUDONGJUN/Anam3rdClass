import { useEffect, useMemo } from 'react'
import { X } from 'lucide-react'

/** 고른 사진 미리보기 (보내기·올리기 전에 하나씩 뺄 수 있다) */
export function PhotoPicks({ photos, onRemove, size = 'md' }: { photos: Blob[]; onRemove: (index: number) => void; size?: 'sm' | 'md' }) {
  const urls = useMemo(() => photos.map((p) => URL.createObjectURL(p)), [photos])
  useEffect(() => () => urls.forEach((u) => URL.revokeObjectURL(u)), [urls])
  if (photos.length === 0) return null
  const box = size === 'sm' ? 'size-16' : 'size-20'
  return (
    <ul className="flex gap-2 overflow-x-auto pb-1" aria-label="고른 사진">
      {urls.map((url, i) => (
        <li key={url} className={`relative shrink-0 overflow-hidden rounded-xl border border-line bg-surface-2 ${box}`}>
          <img src={url} alt={`고른 사진 ${i + 1}`} className="size-full object-cover" />
          <button
            type="button"
            onClick={() => onRemove(i)}
            aria-label={`사진 ${i + 1} 빼기`}
            className="absolute top-1 right-1 flex size-6 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80"
          >
            <X className="size-3.5" aria-hidden />
          </button>
        </li>
      ))}
    </ul>
  )
}
