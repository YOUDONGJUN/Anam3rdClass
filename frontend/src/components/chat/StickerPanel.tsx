import { STICKERS, stickerOf, type Sticker } from '../../lib/stickers'

function Cell({ s, selected, onPick }: { s: Sticker; selected: boolean; onPick: (key: string) => void }) {
  return (
    <button
      type="button"
      onClick={() => onPick(s.key)}
      aria-label={selected ? `${s.label} (한 번 더 누르면 보내요)` : s.label}
      aria-pressed={selected}
      // touch-manipulation: 빠르게 두 번 눌러도 폰이 화면 확대로 받아들이지 않게
      className={`aspect-square touch-manipulation rounded-xl p-1 transition-transform active:scale-95 ${
        selected ? 'bg-accent/15 ring-2 ring-accent' : 'hover:bg-surface-2'
      }`}
    >
      <img src={s.src} alt="" loading="lazy" draggable={false} className="size-full select-none" />
    </button>
  )
}

/**
 * 이모티콘함 (키보드 자리에 열린다). 누르면 입력창 위에 미리 보이고, 같은 것을 한 번 더 누르면(두 번 터치) 바로 보낸다.
 * 맨 위 줄은 이 기기에서 최근 쓴 것
 */
export function StickerPanel({
  selected,
  recent,
  onPick,
}: {
  selected: string | null
  recent: string[]
  onPick: (key: string) => void
}) {
  const recentStickers = recent.map(stickerOf).filter((s): s is Sticker => s !== null)
  return (
    <div className="h-[min(18rem,42dvh)] shrink-0 overflow-y-auto overscroll-contain border-t border-line bg-surface px-2 pt-1 pb-[calc(8px+env(safe-area-inset-bottom))]">
      {recentStickers.length > 0 && (
        <section role="group" aria-label="최근 쓴 이모티콘">
          <h3 className="px-1.5 pt-1 pb-1 text-[12px] font-bold text-ink-3">최근</h3>
          <div className="grid grid-cols-4 gap-1 sm:grid-cols-6">
            {recentStickers.map((s) => (
              <Cell key={s.key} s={s} selected={selected === s.key} onPick={onPick} />
            ))}
          </div>
        </section>
      )}
      <section role="group" aria-label="KU 호랑이 이모티콘">
        <h3 className="px-1.5 pt-1 pb-1 text-[12px] font-bold text-ink-3">KU 호랑이</h3>
        <div className="grid grid-cols-4 gap-1 sm:grid-cols-6">
          {STICKERS.map((s) => (
            <Cell key={s.key} s={s} selected={selected === s.key} onPick={onPick} />
          ))}
        </div>
      </section>
    </div>
  )
}
