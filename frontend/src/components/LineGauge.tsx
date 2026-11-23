import { Fragment } from 'react'
import type { MeetupStatus, PersonView } from '../api/types'
import { PersonName } from './PersonName'

/*
 * 노선도 게이지 — 이 서비스의 시그니처.
 * 자리 하나 = 역 하나. 참여자가 찰수록 노선이 칠해지고,
 * 최소 인원 자리는 "환승역"처럼 이중 원으로 그려서 거기까지 가면 성사라는 걸 보여준다.
 */

interface GaugeProps {
  count: number
  min: number
  max: number
  status: MeetupStatus
}

function palette(status: MeetupStatus, reached: boolean) {
  if (status === 'FAILED' || status === 'CANCELED' || status === 'DONE') {
    return { fill: 'var(--ink-3)', ring: 'var(--ink-3)' }
  }
  return reached ? { fill: 'var(--ok)', ring: 'var(--ok)' } : { fill: 'var(--accent)', ring: 'var(--accent)' }
}

/** 목록용 가로 게이지 */
export function LineGauge({ count, min, max, status }: GaugeProps) {
  const reached = count >= min
  const { fill } = palette(status, reached)
  const label = `정원 ${max}명 중 ${count}명 참여, ${min}명이 모이면 성사`

  if (max > 12) {
    // 자리가 많으면 역 대신 막대로
    const pct = Math.min(100, (count / max) * 100)
    const minPct = (min / max) * 100
    return (
      <div role="img" aria-label={label} className="relative h-2.5 w-full max-w-56 rounded-full bg-line">
        <div className="h-full rounded-full" style={{ width: `${pct}%`, backgroundColor: fill }} />
        <span
          className="absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] bg-surface"
          style={{ left: `${minPct}%`, borderColor: reached ? fill : 'var(--line-strong)' }}
        />
      </div>
    )
  }

  return (
    <div role="img" aria-label={label} className="flex w-full max-w-56 items-center">
      {Array.from({ length: max }, (_, i) => {
        const filled = i < count
        const transfer = i === min - 1
        return (
          <Fragment key={i}>
            {i > 0 && (
              <span
                className="h-[3px] min-w-1.5 flex-1"
                style={{ backgroundColor: i < count ? fill : 'var(--line-strong)' }}
              />
            )}
            <span
              className={`shrink-0 rounded-full ${transfer ? 'size-3.5 border-[3px]' : 'size-2.5 border-2'}`}
              style={{
                borderColor: filled || (transfer && reached) ? fill : 'var(--line-strong)',
                backgroundColor: filled ? (transfer ? 'var(--surface)' : fill) : 'var(--surface)',
                boxShadow: transfer && filled ? `inset 0 0 0 2px ${fill}` : undefined,
              }}
            />
          </Fragment>
        )
      })}
    </div>
  )
}

interface RouteProps extends GaugeProps {
  participants: PersonView[]
  /** 방금 참여해서 애니메이션할 자리 */
  highlightIndex?: number | null
}

/** 상세용 세로 노선도: 참여자 이름이 역 이름처럼 나열된다 */
export function ParticipantRoute({ count, min, max, status, participants, highlightIndex }: RouteProps) {
  const reached = count >= min
  const { fill } = palette(status, reached)

  // 빈자리는 성사 지점까지 + 2칸만 보여주고 나머지는 접는다
  const visibleEmpty = Math.max(0, Math.min(max - count, Math.max(min - count, 0) + 2))
  const hiddenEmpty = max - count - visibleEmpty
  const stops = count + visibleEmpty

  return (
    <ol className="relative">
      {Array.from({ length: stops }, (_, i) => {
        const person = participants[i]
        const filled = i < count
        const transfer = i === min - 1
        const isLast = i === stops - 1 && hiddenEmpty === 0
        const nextFilled = i + 1 < count
        return (
          <li key={i} className="relative flex min-h-14 items-center gap-4 pl-1">
            {/* 노선 */}
            {!isLast && (
              <span
                aria-hidden
                className="absolute top-1/2 left-[17px] h-full w-1 -translate-x-1/2"
                style={{ backgroundColor: nextFilled ? fill : 'var(--line-strong)' }}
              />
            )}
            {/* 역 */}
            <span className="relative z-10 flex w-8 shrink-0 justify-center">
              <span
                className={`rounded-full ${transfer ? 'size-7 border-4' : 'size-5 border-[3px]'} ${
                  i === highlightIndex ? 'station-arrive' : ''
                } ${transfer && reached && i === highlightIndex ? 'transfer-glow' : ''}`}
                style={{
                  borderColor: filled || (transfer && reached) ? fill : 'var(--line-strong)',
                  backgroundColor: filled && !transfer ? fill : 'var(--surface)',
                  boxShadow: transfer && filled ? `inset 0 0 0 3px var(--surface), inset 0 0 0 10px ${fill}` : undefined,
                }}
              />
            </span>
            {/* 역 이름 */}
            <div className="flex min-w-0 flex-1 items-center justify-between gap-3 py-2">
              {person ? (
                <span className="flex min-w-0 items-center gap-2 text-[15px] text-ink">
                  <PersonName person={person} />
                  {person.host && (
                    <span className="shrink-0 rounded border border-line-strong px-1.5 py-0.5 text-[11px] font-bold text-ink-2">
                      개설
                    </span>
                  )}
                </span>
              ) : (
                <span className="text-[15px] text-ink-3">빈자리</span>
              )}
              {transfer && (
                <span
                  className={`shrink-0 rounded-md px-2 py-1 text-xs font-bold ${
                    reached ? 'bg-ok-soft text-ok-ink' : 'bg-accent-soft text-accent-ink'
                  }`}
                >
                  {reached ? '성사 지점 도착' : `${min}명이면 성사`}
                </span>
              )}
            </div>
          </li>
        )
      })}
      {hiddenEmpty > 0 && (
        <li className="relative flex min-h-12 items-center gap-4 pl-1">
          <span className="flex w-8 shrink-0 flex-col items-center gap-1" aria-hidden>
            <span className="size-1.5 rounded-full bg-line-strong" />
            <span className="size-1.5 rounded-full bg-line-strong" />
          </span>
          <span className="text-sm text-ink-3">빈자리 {hiddenEmpty}개 더 · 정원 {max}명</span>
        </li>
      )}
    </ol>
  )
}
