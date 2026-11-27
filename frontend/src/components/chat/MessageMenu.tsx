import { useEffect, useRef, type ComponentType } from 'react'
import { Copy, Flag, Megaphone, Reply, Trash2 } from 'lucide-react'
import type { ChatMessage, ReactionKind } from '../../api/types'
import { myReaction, previewOf, REACTIONS } from '../../lib/chat'
import { formatDateTime } from '../../lib/time'

function Item({
  icon: Icon,
  label,
  danger,
  onClick,
}: {
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>
  label: string
  danger?: boolean
  onClick: () => void
}) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className={`flex h-12 w-full items-center gap-3 px-5 text-left text-[15px] font-semibold hover:bg-surface-2 ${
          danger ? 'text-danger-ink' : 'text-ink'
        }`}
      >
        <Icon className="size-5 shrink-0 opacity-80" aria-hidden />
        {label}
      </button>
    </li>
  )
}

/**
 * 메시지를 길게 누르면(PC 는 누르면) 열리는 메뉴: 공감 · 답장 · 복사 · 톡방 공지 · 지우기(내 것) · 신고·차단(원우 것).
 * 고르면 창을 닫고 그 일을 한다. 열려 있는 동안만 그린다 (닫히면 onClose)
 */
export function MessageMenu({
  m,
  canWrite,
  canPin,
  pinnedHere,
  onClose,
  onReact,
  onReply,
  onCopy,
  onPin,
  onDelete,
  onReport,
}: {
  m: ChatMessage
  /** 취소된 3교시는 읽기만 (공감 · 답장 · 공지 없음) */
  canWrite: boolean
  /** 연 사람: 톡방 공지로 올리고 내릴 수 있다 */
  canPin: boolean
  /** 이 메시지가 지금 톡방 공지다 */
  pinnedHere: boolean
  onClose: () => void
  onReact: (reaction: ReactionKind) => void
  onReply: () => void
  onCopy: () => void
  onPin: () => void
  onDelete: () => void
  onReport: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  /**
   * 창 안에서 손가락(마우스)을 댄 적이 있는지. 길게 눌러 연 뒤 손을 떼면 그 자리에 click 이 올 수 있다 —
   * 그것이 창 바깥(닫기)이나 메뉴(지우기 …)를 누른 것이 되지 않게, 창 안에서 새로 누른 것만 받는다
   */
  const armed = useRef(false)
  const head = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
    // 창이 열리면 브라우저가 첫 단추(❤️)를 잡아 테두리가 생긴다 — 이미 누른 것처럼 보이지 않게 맨 위 메시지 줄을 잡는다
    head.current?.focus({ preventScroll: true })
  }, [])

  const close = () => ref.current?.close()
  /** 고르면 창을 닫고 그 일을 한다 */
  const choose = (fn: () => void) => {
    close()
    fn()
  }
  const mine = myReaction(m)

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onPointerDown={() => {
        armed.current = true
      }}
      onClickCapture={(e) => {
        // detail 0 = 키보드·화면 읽기 프로그램으로 누름 (손가락이 아니다)
        if (e.detail > 0 && !armed.current) {
          e.preventDefault()
          e.stopPropagation()
        }
      }}
      onClick={(e) => {
        if (e.target === ref.current) close()
      }}
      aria-label="메시지 메뉴"
      className="sheet-up mx-auto mt-auto mb-0 w-full max-w-sm overflow-hidden rounded-t-[22px] border border-line bg-surface p-0 text-ink backdrop:bg-black/40 sm:mb-auto sm:rounded-3xl"
    >
      <div ref={head} tabIndex={-1} className="px-5 pt-4 pb-3 outline-none focus-visible:outline-none">
        <p className="truncate text-[12px] text-ink-3">
          {m.mine ? '나' : m.sender.name} · {formatDateTime(m.createdAt)}
        </p>
        <p className="mt-0.5 line-clamp-2 text-[14px] break-words text-ink-2">{previewOf(m)}</p>
      </div>
      {canWrite && (
        <div role="group" aria-label="공감" className="flex justify-between gap-1 px-4 pb-3">
          {REACTIONS.map((r) => (
            <button
              key={r.reaction}
              type="button"
              onClick={() => choose(() => onReact(r.reaction))}
              aria-label={mine === r.reaction ? `${r.label} 빼기` : r.label}
              aria-pressed={mine === r.reaction}
              className={`flex size-11 items-center justify-center rounded-full text-[24px] leading-none ${
                mine === r.reaction ? 'bg-accent/15 ring-2 ring-accent' : 'bg-surface-2 hover:bg-line'
              }`}
            >
              <span aria-hidden>{r.emoji}</span>
            </button>
          ))}
        </div>
      )}
      <ul className="border-t border-line py-1">
        {canWrite && <Item icon={Reply} label="답장" onClick={() => choose(onReply)} />}
        {m.body && <Item icon={Copy} label="복사" onClick={() => choose(onCopy)} />}
        {canPin && <Item icon={Megaphone} label={pinnedHere ? '톡방 공지 내리기' : '톡방 공지로 올리기'} onClick={() => choose(onPin)} />}
        {m.mine ? (
          <Item icon={Trash2} label="지우기" danger onClick={() => choose(onDelete)} />
        ) : (
          <Item icon={Flag} label="신고·차단" onClick={() => choose(onReport)} />
        )}
      </ul>
      <div className="h-[env(safe-area-inset-bottom)] shrink-0" aria-hidden />
    </dialog>
  )
}
