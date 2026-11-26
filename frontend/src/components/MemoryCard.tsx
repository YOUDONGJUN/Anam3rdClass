import { useState } from 'react'
import { Link } from 'react-router'
import { Flag, Globe2, Trash2, TrainFront, Users } from 'lucide-react'
import { absoluteUrl } from '../api/client'
import { useDeleteMemory } from '../api/queries'
import type { Memory } from '../api/types'
import { formatDate, formatRelative } from '../lib/time'
import { PersonName } from './PersonName'
import { PhotoViewer } from './PhotoViewer'
import { cardClass } from './styles'
import { useToast } from './toastContext'

/**
 * 추억 한 장: 사진(옆으로 넘김) · 한 줄 · 올린 사람 · 어느 3교시.
 * onReport: 신고 (3교시 신고로 받는다)
 */
export function MemoryCard({
  memory: m,
  now,
  showMeetup = true,
  onReport,
}: {
  memory: Memory
  now: Date
  showMeetup?: boolean
  onReport?: (m: Memory) => void
}) {
  const [viewing, setViewing] = useState<number | null>(null)
  const [index, setIndex] = useState(0)
  /** 사진 칸 비율 (가로÷세로): 첫 사진에 맞추되 9:16 ~ 4:3 사이로. 불러오기 전에는 4:5 */
  const [ratio, setRatio] = useState(4 / 5)
  const remove = useDeleteMemory()
  const toast = useToast()
  const urls = m.photos.map((p) => p.url)

  function del() {
    if (!window.confirm('이 추억을 지울까요?\n\n• 사진도 함께 지워지고 되돌릴 수 없어요')) return
    remove.mutate(m.id, {
      onSuccess: () => toast.show('추억을 지웠어요'),
      onError: (e) => toast.show(e.message, 'error'),
    })
  }

  return (
    <article className={`${cardClass} overflow-hidden`}>
      <header className="flex items-center gap-2 px-4 py-3 text-sm text-ink-2">
        <span className="min-w-0 flex-1">
          <PersonName person={m.author} />
        </span>
        <span
          className="flex shrink-0 items-center gap-1 text-ink-3"
          title={m.visibility === 'PUBLIC' ? '모두에게 공개' : '함께한 원우만'}
        >
          {m.visibility === 'PUBLIC' ? <Globe2 className="size-3.5" aria-hidden /> : <Users className="size-3.5" aria-hidden />}
          <span className="sr-only">{m.visibility === 'PUBLIC' ? '모두에게 공개' : '함께한 원우만'}</span>
          {formatRelative(m.createdAt, now)}
        </span>
      </header>

      <div className="relative bg-surface-2">
        <div
          className="flex snap-x snap-mandatory overflow-x-auto [scrollbar-width:none]"
          onScroll={(e) => {
            const el = e.currentTarget
            setIndex(Math.round(el.scrollLeft / Math.max(1, el.clientWidth)))
          }}
        >
          {m.photos.map((p, i) => (
            <button
              key={p.key}
              type="button"
              onClick={() => setViewing(i)}
              className="max-h-[80dvh] w-full shrink-0 snap-center"
              style={{ aspectRatio: ratio }}
              aria-label={`사진 ${i + 1} 크게 보기`}
            >
              {/* 자르지 않고 다 보인다 — 인생네컷처럼 세로로 긴 사진도 얼굴이 잘리지 않게 */}
              <img
                src={absoluteUrl(p.url)}
                alt=""
                loading="lazy"
                decoding="async"
                onLoad={(e) => {
                  if (i !== 0) return
                  const { naturalWidth: w, naturalHeight: h } = e.currentTarget
                  if (w > 0 && h > 0) setRatio(Math.min(4 / 3, Math.max(9 / 16, w / h)))
                }}
                className="size-full object-contain"
              />
            </button>
          ))}
        </div>
        {m.photos.length > 1 && (
          <div className="pointer-events-none absolute inset-x-0 bottom-2.5 flex justify-center gap-1.5" aria-hidden>
            {m.photos.map((p, i) => (
              <span key={p.key} className={`size-1.5 rounded-full ${i === index ? 'bg-white' : 'bg-white/50'}`} />
            ))}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-2 px-4 pt-3 pb-3.5">
        {m.caption && <p className="text-[15px] leading-relaxed whitespace-pre-wrap text-ink">{m.caption}</p>}
        <div className="flex items-center gap-2">
          {showMeetup && (
            <Link
              to={`/meetups/${m.meetupId}`}
              className="inline-flex min-w-0 items-center gap-1.5 text-sm font-semibold text-ink-3 hover:text-ink"
            >
              <TrainFront className="size-4 shrink-0" aria-hidden />
              <span className="truncate">{m.meetupTitle}</span>
              <span className="shrink-0 font-medium">· {formatDate(m.meetAt)}</span>
            </Link>
          )}
          <span className="ml-auto flex shrink-0 gap-0.5">
            {m.canDelete && (
              <button
                type="button"
                onClick={del}
                disabled={remove.isPending}
                aria-label="추억 지우기"
                className="hit flex size-9 items-center justify-center rounded-lg text-ink-3 hover:bg-danger-soft hover:text-danger-ink"
              >
                <Trash2 className="size-4" aria-hidden />
              </button>
            )}
            {!m.mine && onReport && (
              <button
                type="button"
                onClick={() => onReport(m)}
                aria-label="추억 신고"
                className="hit flex size-9 items-center justify-center rounded-lg text-ink-3 hover:bg-surface-2 hover:text-ink"
              >
                <Flag className="size-4" aria-hidden />
              </button>
            )}
          </span>
        </div>
      </div>

      {viewing !== null && <PhotoViewer urls={urls} start={viewing} onClose={() => setViewing(null)} />}
    </article>
  )
}
