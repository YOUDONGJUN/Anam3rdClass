import { useState, type KeyboardEvent } from 'react'
import { ArrowUpRight, Check, LoaderCircle, MapPin, Search, X } from 'lucide-react'
import { usePlaceSearch } from '../api/queries'
import type { PlaceHit, PlaceSuggestion } from '../api/types'
import { useDebounced } from '../lib/hooks'
import { MAP_PROVIDERS } from '../lib/map'
import type { PlaceValue } from '../lib/places'
import { inputClass } from './styles'

/** 찾기를 시작하는 글자 수 */
const MIN_QUERY = 2

/**
 * 장소 칸: 이름을 쓰면 카카오 장소 검색 결과가 아래에 뜨고, 고르면 위치·지역이 같이 저장된다.
 * 고르지 않고 그대로 써도 된다 (위치 없음 → 가까운 순에서 맨 뒤). 이름을 고치면 고른 위치는 지운다.
 * 장소 검색을 쓸 수 없으면(키 없음 · 카카오 오류) 지도 앱에서 찾는 버튼으로 안내한다.
 */
export function PlacePicker({
  value,
  onChange,
  suggestions = [],
  id,
  describedBy,
  invalid,
  placeholder,
  maxLength = 60,
}: {
  value: PlaceValue
  onChange: (next: PlaceValue) => void
  /** 칸 아래 추천 칩 (많이 쓴 장소 · 학교 근처 기본 장소) */
  suggestions?: readonly PlaceSuggestion[]
  id?: string
  describedBy?: string
  invalid?: boolean
  placeholder?: string
  maxLength?: number
}) {
  // 검색 결과는 이름을 고치는 중에만 보인다 (고르거나 닫으면 숨긴다)
  const [open, setOpen] = useState(false)
  const typed = value.place.trim()
  const settled = useDebounced(typed, 450)
  // Enter·찾기 버튼은 기다리지 않고 바로 찾는다
  const [forced, setForced] = useState<string | null>(null)
  const term = forced === typed ? forced : settled === typed ? settled : ''
  const query = open && term.length >= MIN_QUERY ? term : ''
  const search = usePlaceSearch(query)
  const unavailable = search.data?.available === false || search.isError
  const hits = search.data?.available ? search.data.places : []
  const searching = open && typed.length >= MIN_QUERY && (term !== typed || search.isFetching)

  function type(place: string) {
    onChange({ place, location: null, region: null })
    setOpen(true)
  }

  function pick(hit: PlaceHit | PlaceSuggestion) {
    onChange({ place: hit.name.slice(0, maxLength), location: hit.location, region: hit.region })
    setOpen(false)
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    // Enter 는 폼 제출 대신 찾기 (한글 조합 중 Enter 는 그대로)
    if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
      e.preventDefault()
      if (typed.length >= MIN_QUERY) {
        setForced(typed)
        setOpen(true)
      }
    }
    if (e.key === 'Escape' && open) {
      e.preventDefault()
      setOpen(false)
    }
  }

  const showResults = open && typed.length >= MIN_QUERY

  return (
    <div className="flex flex-col gap-3">
      <div className="relative">
        <input
          id={id}
          enterKeyHint="search"
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          aria-autocomplete="list"
          aria-expanded={showResults}
          className={`${inputClass} pr-12`}
          maxLength={maxLength}
          autoComplete="off"
          placeholder={placeholder}
          value={value.place}
          onChange={(e) => type(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <button
          type="button"
          aria-label="장소 찾기"
          disabled={typed.length < MIN_QUERY}
          onClick={() => {
            setForced(typed)
            setOpen(true)
          }}
          className="absolute top-1/2 right-1.5 flex size-10 -translate-y-1/2 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 disabled:opacity-40"
        >
          {searching ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <Search className="size-5" aria-hidden />}
        </button>
      </div>

      {value.location && !open && (
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] text-ink-3">
          <span className="inline-flex items-center gap-1 font-semibold text-ok-ink">
            <Check className="size-3.5" strokeWidth={3} aria-hidden />
            지도 위치 저장됨
          </span>
          {value.region && <span className="break-keep">{value.region}</span>}
          <a
            href={MAP_PROVIDERS[0].url(value.place, value.location)}
            target="_blank"
            rel="noreferrer"
            className="hit inline-flex items-center gap-0.5 font-semibold whitespace-nowrap text-ink-2 hover:text-ink"
          >
            카카오맵에서 보기
            <ArrowUpRight className="size-3.5" aria-hidden />
          </a>
        </p>
      )}

      {showResults && (
        <div className="overflow-hidden rounded-xl border border-line-strong bg-surface" aria-live="polite">
          <div className="flex items-center justify-between gap-2 border-b border-line py-1 pr-1 pl-3.5">
            <p className="text-[13px] font-semibold text-ink-3">
              {unavailable ? '장소 검색이 잠시 안 돼요' : searching ? '찾는 중…' : `검색 결과 ${hits.length}곳`}
            </p>
            <button
              type="button"
              aria-label="검색 결과 닫기"
              onClick={() => setOpen(false)}
              className="flex size-9 items-center justify-center rounded-lg text-ink-3 hover:bg-surface-2 hover:text-ink"
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
          {unavailable ? (
            <div className="flex flex-col gap-2.5 px-3.5 py-3">
              <p className="text-sm leading-relaxed break-keep text-ink-2">
                이름을 그대로 써도 돼요.
              </p>
              <div className="flex flex-wrap gap-2">
                {MAP_PROVIDERS.map((p) => (
                  <a
                    key={p.label}
                    href={p.url(value.place)}
                    target="_blank"
                    rel="noreferrer"
                    className="hit inline-flex h-8 items-center gap-0.5 rounded-lg border border-line-strong px-2.5 text-[13px] font-semibold text-ink-2 hover:bg-surface-2"
                  >
                    {p.label}에서 찾기
                    <ArrowUpRight className="size-3.5" aria-hidden />
                  </a>
                ))}
              </div>
            </div>
          ) : !searching && hits.length === 0 ? (
            <p className="px-3.5 py-3 text-sm leading-relaxed break-keep text-ink-2">
              찾는 곳이 없어요. 이름을 그대로 써도 돼요.
            </p>
          ) : (
            <ul className="max-h-72 divide-y divide-line overflow-y-auto overscroll-contain">
              {hits.map((hit, i) => (
                <li key={`${hit.name}-${hit.location.lat}-${hit.location.lng}-${i}`}>
                  <button
                    type="button"
                    onClick={() => pick(hit)}
                    className="flex w-full items-start gap-2.5 px-3.5 py-2.5 text-left transition-colors hover:bg-surface-2 focus-visible:bg-surface-2"
                  >
                    <MapPin className="mt-0.5 size-4 shrink-0 text-ink-3" aria-hidden />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline gap-1.5">
                        <span className="truncate text-[15px] font-bold text-ink">{hit.name}</span>
                        {hit.category && <span className="shrink-0 truncate text-xs text-ink-3">{hit.category}</span>}
                      </span>
                      {hit.address && <span className="mt-0.5 block truncate text-[13px] text-ink-3">{hit.address}</span>}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {suggestions.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {suggestions.map((s) => {
            const active = value.place === s.name
            return (
              <button
                key={s.name}
                type="button"
                aria-pressed={active}
                onClick={() => pick(s)}
                className={`inline-flex min-h-10 items-center gap-1.5 rounded-xl border px-3.5 py-2 text-sm font-semibold transition-colors ${
                  active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink-2 hover:border-ink-3 hover:text-ink'
                }`}
              >
                {s.location && <MapPin className="size-3.5 shrink-0 opacity-70" aria-hidden />}
                {s.name}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
