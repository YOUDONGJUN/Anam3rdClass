/**
 * 역 출발 안내판처럼 한 글자씩 접힌 판에 시각을 보여준다: [1][9]:[3][0]
 * 모양은 index.css 의 .flap
 */
export function DepartureBoard({ time, label }: { time: string; label?: string }) {
  return (
    <span role="img" aria-label={label ?? `${time} 출발`} className="inline-flex items-center gap-1 pb-[3px]">
      {time.split('').map((ch, i) =>
        ch === ':' ? (
          <span key={i} aria-hidden className="px-px text-2xl font-extrabold text-ink-3">
            :
          </span>
        ) : (
          <span
            key={i}
            aria-hidden
            className="flap tabular relative flex h-11 w-[33px] items-center justify-center rounded-lg text-[29px] leading-none font-extrabold text-white"
          >
            {ch}
          </span>
        ),
      )}
    </span>
  )
}
