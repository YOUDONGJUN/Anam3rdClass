import { useRef, type MouseEvent, type PointerEvent } from 'react'
import { AlertCircle, EyeOff, ImageOff, LoaderCircle, RotateCw } from 'lucide-react'
import { absoluteUrl } from '../../api/client'
import type { PendingMessage } from '../../api/chat'
import type { ChatMessage, ChatQuote, ReactionKind } from '../../api/types'
import { coarsePointer, REACTIONS, replyLabel } from '../../lib/chat'
import { affiliation } from '../../lib/person'
import { stickerOf } from '../../lib/stickers'
import { formatTime } from '../../lib/time'
import { RoleTag } from '../PersonName'
import { SchoolBadge } from '../SchoolBadge'

const MINE = 'bg-accent text-on-accent rounded-2xl rounded-br-md'
const THEIRS = 'bg-surface text-ink border border-line rounded-2xl rounded-bl-md'
/** 이만큼 누르고 있으면 메뉴 */
const LONG_PRESS_MS = 450

/**
 * 길게 누르기 (폰): 손가락을 대고 있으면 메뉴를 연다. 움직이면(스크롤) 취소.
 * 안드로이드는 길게 누르면 contextmenu 도 오고, PC 는 오른쪽 클릭이 contextmenu 다.
 * 길게 누른 뒤 손을 떼며 오는 click 은 삼킨다 (사진이 열리지 않게)
 */
function useLongPress(onLong: () => void) {
  const timer = useRef<number | null>(null)
  const start = useRef<{ x: number; y: number } | null>(null)
  const fired = useRef(false)
  const clear = () => {
    if (timer.current !== null) window.clearTimeout(timer.current)
    timer.current = null
  }
  return {
    onPointerDown: (e: PointerEvent) => {
      if (e.pointerType === 'mouse') return
      fired.current = false
      start.current = { x: e.clientX, y: e.clientY }
      clear()
      timer.current = window.setTimeout(() => {
        fired.current = true
        navigator.vibrate?.(10)
        onLong()
      }, LONG_PRESS_MS)
    },
    onPointerMove: (e: PointerEvent) => {
      if (start.current && Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 10) clear()
    },
    onPointerUp: clear,
    onPointerCancel: clear,
    onPointerLeave: clear,
    onContextMenu: (e: MouseEvent) => {
      e.preventDefault()
      if (!fired.current) onLong()
      fired.current = true
    },
    onClickCapture: (e: MouseEvent) => {
      if (!fired.current) return
      fired.current = false
      e.preventDefault()
      e.stopPropagation()
    },
  }
}

/** 답장의 원래 메시지 (누르면 그 메시지로) */
export function QuoteBox({ q, mine, onClick }: { q: ChatQuote; mine: boolean; onClick?: () => void }) {
  const body = (
    <>
      <span className="block truncate text-[12px] font-bold text-ink-2">{replyLabel(q)}</span>
      <span className="block truncate text-[13px] text-ink-3">{q.preview}</span>
    </>
  )
  // 내 답장은 오른쪽, 원우 답장은 왼쪽에 강조 줄 (말풍선 꼬리 쪽)
  const className = `block max-w-full min-w-0 rounded-xl border-accent bg-surface-2 py-1.5 text-left ${
    mine ? 'border-r-[3px] pr-3 pl-3' : 'border-l-[3px] pr-3 pl-3'
  }`
  return onClick ? (
    <button type="button" onClick={onClick} className={className} aria-label={`${replyLabel(q)}: ${q.preview} — 원래 메시지로`}>
      {body}
    </button>
  ) : (
    <div className={className}>{body}</div>
  )
}

/** 메시지 아래 공감 (누르면 내 공감을 넣거나 뺀다) */
function Reactions({
  m,
  disabled,
  onReact,
}: {
  m: ChatMessage
  disabled: boolean
  onReact: (reaction: ReactionKind) => void
}) {
  if (m.reactions.length === 0) return null
  return (
    <div className={`mt-1 flex flex-wrap gap-1 ${m.mine ? 'justify-end' : 'justify-start'}`}>
      {m.reactions.map((r) => {
        const info = REACTIONS.find((x) => x.reaction === r.reaction)
        return (
          <button
            key={r.reaction}
            type="button"
            disabled={disabled}
            onClick={() => onReact(r.reaction)}
            aria-pressed={r.mine}
            aria-label={`${info?.label ?? r.reaction} ${r.count}명${r.mine ? ', 나도 누름' : ''}`}
            className={`tabular inline-flex h-7 items-center gap-1 rounded-full border px-2 text-[12px] font-semibold ${
              r.mine ? 'border-accent bg-accent/10 text-accent-ink' : 'border-line bg-surface text-ink-2'
            }`}
          >
            <span aria-hidden>{info?.emoji}</span>
            {r.count}
          </button>
        )
      })}
    </div>
  )
}

export function ChatBubble({
  m,
  federated,
  first,
  last,
  unread,
  flash,
  canReact,
  onMenu,
  onPhoto,
  onQuote,
  onReact,
}: {
  m: ChatMessage
  /** 연합 3교시: 학교 배지 + 학과 · 같은 학교 3교시: 기수 · 학과 */
  federated: boolean
  first: boolean
  last: boolean
  /** 이 메시지를 아직 안 읽은 사람 수 (카톡의 숫자 1) */
  unread: number
  /** 공지·답장에서 이 메시지로 왔다 — 잠깐 테두리 */
  flash: boolean
  canReact: boolean
  /** 메시지 메뉴 (폰: 길게 누르기 · PC: 누르기 · 오른쪽 클릭) */
  onMenu: () => void
  onPhoto: (index: number) => void
  onQuote: (id: number) => void
  onReact: (reaction: ReactionKind) => void
}) {
  const visible = m.photos.filter((p) => !p.expired)
  const muted = m.deleted || m.hidden
  const sticker = muted ? null : stickerOf(m.sticker)
  // 이 화면이 모르는 이모티콘 (새로 생긴 것) — 글로 알린다
  const unknownSticker = !muted && Boolean(m.sticker) && !sticker
  // 지운 메시지 · 가린 메시지는 메뉴가 없다
  const hasMenu = !muted
  const press = useLongPress(() => {
    if (hasMenu) onMenu()
  })
  /** PC 는 눌러서, 폰은 길게 눌러서 메뉴 (화면 읽기 프로그램·키보드로 누르면 detail 이 0) */
  const tap = (e: MouseEvent) => {
    if (hasMenu && (!coarsePointer || e.detail === 0)) onMenu()
  }
  return (
    <div data-mid={m.id} className={`flex flex-col ${m.mine ? 'items-end' : 'items-start'} ${first ? 'mt-3' : 'mt-1'}`}>
      {first && !m.mine && (
        <p className="mb-1 flex max-w-full items-center gap-1.5 px-1 text-[13px] text-ink-2">
          <span className="truncate font-bold text-ink">{m.sender.name}</span>
          {m.sender.masked && <EyeOff className="size-3 shrink-0 opacity-60" aria-label="이름 가려짐" />}
          {m.sender.owner && <RoleTag />}
          {m.sender.host && <span className="shrink-0 rounded bg-surface-2 px-1 text-[11px] font-bold text-ink-2">연 사람</span>}
          {federated && <SchoolBadge school={m.sender.school} />}
          <span className="shrink-0 text-ink-3">{affiliation(m.sender.cohortLabel, m.sender.departmentLabel)}</span>
        </p>
      )}
      <div className={`flex max-w-[85%] items-end gap-1.5 sm:max-w-[70%] ${m.mine ? 'flex-row-reverse' : ''}`}>
        <div
          {...press}
          className={`flex min-w-0 flex-col gap-1 rounded-2xl transition-shadow select-none [-webkit-touch-callout:none] ${
            m.mine ? 'items-end' : 'items-start'
          } ${flash ? 'ring-2 ring-accent ring-offset-2 ring-offset-bg' : ''}`}
        >
          {m.reply && <QuoteBox q={m.reply} mine={m.mine} onClick={() => onQuote(m.reply!.id)} />}
          {!muted && m.photos.length > 0 && (
            <div className={`grid gap-1 overflow-hidden rounded-2xl ${m.photos.length > 1 ? 'w-60 grid-cols-2' : 'w-60'}`}>
              {m.photos.map((p, i) =>
                p.expired || !p.url ? (
                  <div
                    key={p.key}
                    className="flex aspect-square flex-col items-center justify-center gap-1 bg-surface-2 text-center text-[11px] text-ink-3"
                  >
                    <ImageOff className="size-5" aria-hidden />
                    보관 기간이 지난 사진
                  </div>
                ) : (
                  <button
                    key={p.key}
                    type="button"
                    onClick={() => onPhoto(visible.indexOf(p))}
                    aria-label={`사진 ${i + 1} 크게 보기`}
                    className={m.photos.length > 1 ? 'aspect-square' : 'aspect-[4/5]'}
                  >
                    <img
                      src={absoluteUrl(p.url)}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      draggable={false}
                      className="size-full bg-surface-2 object-cover"
                    />
                  </button>
                ),
              )}
            </div>
          )}
          {sticker && (
            <button
              type="button"
              onClick={tap}
              aria-haspopup="dialog"
              aria-label={`이모티콘: ${sticker.label}`}
              className="block"
            >
              <img src={sticker.src} alt="" width={128} height={128} draggable={false} className="size-32 select-none" />
            </button>
          )}
          {(m.body || muted || unknownSticker) && (
            <button
              type="button"
              onClick={tap}
              aria-haspopup={hasMenu ? 'dialog' : undefined}
              className={`px-3.5 py-2 text-left text-[15px] leading-relaxed break-words whitespace-pre-wrap ${m.mine ? MINE : THEIRS} ${
                muted ? 'italic opacity-60' : ''
              }`}
            >
              {m.deleted ? '지운 메시지예요' : m.hidden ? '차단한 원우의 메시지예요' : (m.body ?? '(이모티콘)')}
            </button>
          )}
          {!m.body && !muted && !m.sticker && (
            <button type="button" onClick={onMenu} className="sr-only">
              메시지 메뉴
            </button>
          )}
        </div>
        {(last || unread > 0) && (
          <div className={`flex shrink-0 flex-col pb-0.5 ${m.mine ? 'items-end' : 'items-start'}`}>
            {unread > 0 && (
              <span className="tabular text-[11px] leading-tight font-bold text-accent-ink" aria-label={`안 읽은 원우 ${unread}명`}>
                {unread}
              </span>
            )}
            {last && (
              <time dateTime={m.createdAt} className="tabular text-[11px] text-ink-3">
                {formatTime(m.createdAt)}
              </time>
            )}
          </div>
        )}
      </div>
      {!muted && <Reactions m={m} disabled={!canReact} onReact={onReact} />}
    </div>
  )
}

/** 아직 서버에 닿지 않은 내 메시지: 보내는 중(흐리게) · 실패(다시 보내기 · 보내지 않기) */
export function PendingBubble({ p, onRetry, onDiscard }: { p: PendingMessage; onRetry: () => void; onDiscard: () => void }) {
  const sticker = stickerOf(p.sticker)
  const failed = p.status === 'failed'
  return (
    <div className="mt-1 flex flex-col items-end" data-pending={p.status}>
      <div className="flex max-w-[85%] flex-row-reverse items-end gap-1.5 sm:max-w-[70%]">
        <div className={`flex min-w-0 flex-col items-end gap-1 ${failed ? '' : 'opacity-60'}`}>
          {p.reply && <QuoteBox q={p.reply} mine />}
          {p.previews.length > 0 && (
            <div className={`grid gap-1 overflow-hidden rounded-2xl ${p.previews.length > 1 ? 'w-60 grid-cols-2' : 'w-60'}`}>
              {p.previews.map((src) => (
                <img
                  key={src}
                  src={src}
                  alt=""
                  className={`${p.previews.length > 1 ? 'aspect-square' : 'aspect-[4/5]'} size-full bg-surface-2 object-cover`}
                />
              ))}
            </div>
          )}
          {sticker && (
            <img src={sticker.src} alt={`이모티콘: ${sticker.label}`} width={128} height={128} className="size-32 select-none" />
          )}
          {p.body && (
            <p className={`px-3.5 py-2 text-[15px] leading-relaxed break-words whitespace-pre-wrap ${MINE}`}>{p.body}</p>
          )}
        </div>
        <div className="flex shrink-0 flex-col items-end pb-1">
          {failed ? (
            <AlertCircle className="size-4 text-danger-ink" aria-label="보내지 못했어요" />
          ) : (
            <LoaderCircle className="size-3.5 animate-spin text-ink-3" aria-label="보내는 중" />
          )}
        </div>
      </div>
      {failed && (
        <div className="mt-1 flex gap-1">
          <button
            type="button"
            onClick={onRetry}
            className="inline-flex h-8 items-center gap-1 rounded-lg px-2.5 text-[13px] font-semibold text-accent-ink hover:bg-surface-2"
          >
            <RotateCw className="size-3.5" aria-hidden />
            다시 보내기
          </button>
          <button
            type="button"
            onClick={onDiscard}
            className="h-8 rounded-lg px-2.5 text-[13px] font-semibold text-ink-3 hover:bg-surface-2"
          >
            보내지 않기
          </button>
        </div>
      )}
    </div>
  )
}
