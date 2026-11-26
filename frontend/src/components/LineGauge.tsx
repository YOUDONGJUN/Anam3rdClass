import { Fragment } from 'react'
import { Ellipsis } from 'lucide-react'
import type { MeetupStatus, PersonView } from '../api/types'
import { PersonName } from './PersonName'

/*
 * 노선도 게이지 — 이 서비스의 시그니처.
 * 자리 하나 = 역 하나. 참여자가 찰수록 노선이 칠해지고,
 * 최소 인원 자리는 "환승역"처럼 이중 원으로 그려서 거기까지 가면 성사라는 걸 보여준다.
 * 인원 제한이 없으면(max = null) 노선 끝을 점선으로 열어 둔다.
 * 최소 인원이 없으면(min = null) 개설자 자리가 곧 환승역 — 열자마자 출발 확정이다.
 */

interface GaugeProps {
  count: number
  /** null 이면 최소 인원 없음 (무조건 진행) */
  min: number | null
  /** null 이면 인원 제한 없음 */
  max: number | null
  status: MeetupStatus
}

/*
 * 입체감: 빈 선로는 홈이 파인 듯(.track 안쪽 그림자), 칠해진 선로·역은 아래쪽을 살짝 어둡게.
 * 광택·흐림 없이 두께만.
 */
const FILLED_SHADE = 'inset 0 -2px 0 rgb(0 0 0 / 0.22)'
const SEGMENT_SHADE = 'inset 0 -1px 0 rgb(0 0 0 / 0.2)'
const EMPTY_STOP_SHADE = 'inset 0 1px 2px rgb(0 0 0 / 0.12)'

/** 역(점)으로 그리는 최대 자리 수. 넘으면 막대 게이지 */
const MAX_STOPS = 8

function lineColor(status: MeetupStatus, reached: boolean) {
  if (status === 'FAILED' || status === 'CANCELED' || status === 'DONE') return 'var(--ink-3)'
  return reached ? 'var(--ok)' : 'var(--accent)'
}

function OpenEnd({ vertical = false }: { vertical?: boolean }) {
  return vertical ? (
    <span aria-hidden className="h-full w-0 border-l-4 border-dotted border-line-strong" />
  ) : (
    <span aria-hidden className="h-0 min-w-4 flex-1 border-t-4 border-dotted border-line-strong" />
  )
}

/** 역 하나의 모양 (가로·세로 게이지 공통) */
function stopStyle(filled: boolean, transfer: boolean, reached: boolean, fill: string, ring: number) {
  if (transfer) {
    return {
      borderColor: filled || reached ? fill : 'var(--line-strong)',
      backgroundColor: 'var(--surface)',
      // 환승역: 가운데를 칠한 이중 원
      boxShadow: filled ? `inset 0 0 0 ${ring}px var(--surface), inset 0 0 0 20px ${fill}` : EMPTY_STOP_SHADE,
    }
  }
  return filled
    ? { borderColor: fill, backgroundColor: fill, boxShadow: FILLED_SHADE }
    : { borderColor: 'var(--line-strong)', backgroundColor: 'var(--surface)', boxShadow: EMPTY_STOP_SHADE }
}

/**
 * 목록용 가로 게이지.
 * size="lg" 는 "다음 출발" 카드처럼 크게 — 줄 너비를 다 쓰고 역도 크다.
 */
export function LineGauge({ count, min, max, status, size = 'md' }: GaugeProps & { size?: 'md' | 'lg' }) {
  const target = min ?? 1
  const reached = count >= target
  const fill = lineColor(status, reached)
  const unlimited = max === null
  const lg = size === 'lg'
  const rule = min === null ? '최소 인원 없이 무조건 진행' : `${min}명이 모이면 성사`
  const label = unlimited ? `${count}명 참여, ${rule}, 인원 제한 없음` : `정원 ${max}명 중 ${count}명 참여, ${rule}`
  // 그릴 역 개수: 정원, 또는 제한 없음이면 (성사 지점과 현재 인원 중 큰 쪽 + 빈 역 1개)
  const stops = unlimited ? Math.max(target, count) + 1 : max
  const width = lg ? 'w-full' : 'w-full max-w-56'

  // 역은 줄어들지 않으므로 9개부터는 막대로 그린다. 360px 폰의 목록 한 줄(약 100px)에 역 8개까지 들어간다
  if (stops > MAX_STOPS) {
    // 자리가 많으면 역 대신 막대로
    const pct = unlimited ? 88 : Math.min(100, (count / stops) * 100)
    const minPct = unlimited ? Math.min(80, (target / Math.max(count, target)) * 80) : (target / stops) * 100
    return (
      <div role="img" aria-label={label} className={`flex items-center ${width}`}>
        <div className={`track relative flex-1 rounded-full ${lg ? 'h-3' : 'h-2.5'}`}>
          <div className="h-full rounded-full" style={{ width: `${pct}%`, backgroundColor: fill, boxShadow: FILLED_SHADE }} />
          <span
            className={`absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-surface ${lg ? 'size-5 border-4' : 'size-4 border-[3px]'}`}
            style={{ left: `${minPct}%`, borderColor: reached ? fill : 'var(--line-strong)' }}
          />
        </div>
        {unlimited && <span className="ml-1 w-6 border-t-4 border-dotted border-line-strong" aria-hidden />}
      </div>
    )
  }

  return (
    <div role="img" aria-label={label} className={`flex items-center ${width}`}>
      {Array.from({ length: stops }, (_, i) => {
        const filled = i < count
        const transfer = i === target - 1
        return (
          <Fragment key={i}>
            {i > 0 && (
              <span
                className={`-mx-px min-w-0.5 flex-1 rounded-full ${lg ? 'h-1.5' : 'h-1'} ${i < count ? '' : 'track'}`}
                style={i < count ? { backgroundColor: fill, boxShadow: SEGMENT_SHADE } : undefined}
              />
            )}
            <span
              className={`relative shrink-0 rounded-full ${
                transfer ? (lg ? 'size-[22px] border-4' : 'size-4 border-[3px]') : lg ? 'size-3.5 border-2' : 'size-[11px] border-2'
              }`}
              style={stopStyle(filled, transfer, reached, fill, 2)}
            />
          </Fragment>
        )
      })}
      {unlimited && <OpenEnd />}
    </div>
  )
}

interface RouteProps extends GaugeProps {
  participants: PersonView[]
  /** 방금 참여해서 애니메이션할 자리 */
  highlightIndex?: number | null
  /** 연합 3교시처럼 여러 학교가 섞이면 학교 배지를 붙인다 */
  showSchool?: boolean
  /** 다른 사람 옆의 ⋯ 버튼 (신고·차단) */
  onPersonMenu?: (person: PersonView) => void
}

/** 상세용 세로 노선도: 참여자 이름이 역 이름처럼 나열된다 */
export function ParticipantRoute({
  count,
  min,
  max,
  status,
  participants,
  highlightIndex,
  showSchool = false,
  onPersonMenu,
}: RouteProps) {
  const target = min ?? 1
  const reached = count >= target
  const fill = lineColor(status, reached)
  const unlimited = max === null

  // 빈자리: 성사 지점까지 + 2칸만 보여주고 나머지는 접는다 (제한 없음이면 성사 지점까지 + 1칸).
  // 끝나거나 무산·취소된 3교시는 더 탈 수 없으니 빈자리를 그리지 않는다
  const open = status === 'RECRUITING' || status === 'CONFIRMED'
  const toMin = Math.max(target - count, 0)
  const visibleEmpty = !open ? 0 : unlimited ? toMin + 1 : Math.max(0, Math.min(max - count, toMin + 2))
  const hiddenEmpty = !open || unlimited ? 0 : max - count - visibleEmpty
  const stops = count + visibleEmpty

  return (
    <ol className="relative">
      {Array.from({ length: stops }, (_, i) => {
        const person = participants[i]
        const filled = i < count
        const transfer = i === target - 1
        const isLast = i === stops - 1 && hiddenEmpty === 0 && !unlimited
        const nextFilled = i + 1 < count
        return (
          <li key={i} className="relative flex min-h-14 items-center gap-4 pl-1">
            {/* 노선 — 역 중심(왼쪽 여백 4px + 역 칸 32px의 절반 = 20px)에 맞춘다 */}
            {!isLast && (
              <span
                aria-hidden
                className={`absolute top-1/2 left-5 h-full w-1.5 -translate-x-1/2 rounded-full ${nextFilled ? '' : 'track'}`}
                style={nextFilled ? { backgroundColor: fill, boxShadow: 'inset -1px 0 0 rgb(0 0 0 / 0.18)' } : undefined}
              />
            )}
            {/* 역 */}
            <span className="relative z-10 flex w-8 shrink-0 justify-center">
              <span
                className={`rounded-full ${transfer ? 'size-7 border-4' : 'size-5 border-[3px]'} ${
                  i === highlightIndex ? 'station-arrive' : ''
                } ${transfer && reached && i === highlightIndex ? 'transfer-glow' : ''}`}
                style={stopStyle(filled, transfer, reached, fill, 3)}
              />
            </span>
            {/* 역 이름 */}
            <div className="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-x-3 gap-y-1 py-2">
              {person ? (
                <span className="flex min-w-0 flex-1 items-center gap-2 text-[15px] text-ink">
                  {/* 좁은 화면: 이름·기수·배지·개설은 다음 줄로 넘기고, ⋯ 버튼은 늘 오른쪽 끝 */}
                  <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                    <PersonName person={person} showSchool={showSchool} wrap />
                    {person.host && (
                      <span className="shrink-0 rounded border border-line-strong px-1.5 py-0.5 text-[11px] font-bold text-ink-2">
                        개설
                      </span>
                    )}
                  </span>
                  {onPersonMenu && !person.me && person.ref !== null && (
                    <button
                      type="button"
                      aria-label={`${person.name} 신고·차단`}
                      onClick={() => onPersonMenu(person)}
                      className="hit -my-2 ml-auto flex size-9 shrink-0 items-center justify-center rounded-lg text-ink-3 hover:bg-surface-2 hover:text-ink"
                    >
                      <Ellipsis className="size-4" aria-hidden />
                    </button>
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
                  {min === null ? '무조건 진행' : reached ? '성사 지점 도착' : `${min}명이면 성사`}
                </span>
              )}
            </div>
          </li>
        )
      })}
      {unlimited && open && (
        <li className="relative flex min-h-12 items-center gap-4 pl-1">
          <span className="flex h-12 w-8 shrink-0 justify-center">
            <OpenEnd vertical />
          </span>
          <span className="text-sm text-ink-3">인원 제한 없음 · 누구나 더 탈 수 있어요</span>
        </li>
      )}
      {hiddenEmpty > 0 && (
        <li className="relative flex min-h-12 items-center gap-4 pl-1">
          <span className="flex w-8 shrink-0 flex-col items-center gap-1" aria-hidden>
            <span className="size-1.5 rounded-full bg-line-strong" />
            <span className="size-1.5 rounded-full bg-line-strong" />
          </span>
          <span className="text-sm text-ink-3">
            빈자리 {hiddenEmpty}개 더 · 정원 {max}명
          </span>
        </li>
      )}
    </ol>
  )
}
