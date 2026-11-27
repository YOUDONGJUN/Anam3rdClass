import type { ReactNode } from 'react'
import { ArrowUpDown, Check, ChevronDown, LocateFixed, MapPin, Search, SlidersHorizontal, X } from 'lucide-react'
import type { MeetupCategory, MeetupSummary } from '../api/types'
import {
  RADIUS_CHOICES,
  SIZE_BANDS,
  type FeedFilter,
  regionOptions,
  schoolOptions,
  sheetFilterCount,
} from '../lib/feedFilter'
import { FEED_SORTS, type FeedSort, sortLabel } from '../lib/feedSort'
import type { MyLocation } from '../lib/geo'
import { CATEGORY, CATEGORY_ORDER } from '../lib/meetup'
import { BottomSheet } from './BottomSheet'
import { buttonClass, chipClass } from './styles'

/** 목록 거르기: 찾기 칸 + 가로로 미는 칩 (참여 가능만 · 언제 · 마실 종류) */
export function FeedFilterBar({ value, onChange, past }: { value: FeedFilter; onChange: (f: FeedFilter) => void; past: boolean }) {
  const set = (patch: Partial<FeedFilter>) => onChange({ ...value, ...patch })
  const toggleCategory = (c: MeetupCategory) =>
    set({ categories: value.categories.includes(c) ? value.categories.filter((x) => x !== c) : [...value.categories, c] })
  return (
    <>
      <label className="relative block">
        <span className="sr-only">3교시 찾기</span>
        <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
        <input
          type="search"
          enterKeyHint="search"
          value={value.q}
          onChange={(e) => set({ q: e.target.value })}
          placeholder="제목·장소·지역·목적으로 찾기"
          className="h-11 w-full rounded-xl border border-line-strong bg-surface pr-10 pl-10 text-[15px] text-ink placeholder:text-ink-3 focus:border-ink-3 focus:outline-none"
        />
        {value.q && (
          <button
            type="button"
            aria-label="찾는 말 지우기"
            onClick={() => set({ q: '' })}
            className="hit absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-ink-3 hover:text-ink"
          >
            <X className="size-4" aria-hidden />
          </button>
        )}
      </label>
      <div className="-mx-4 flex gap-1.5 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0" role="group" aria-label="거르기">
        {!past && (
          <>
            <button type="button" aria-pressed={value.joinable} className={chipClass(value.joinable)} onClick={() => set({ joinable: !value.joinable })}>
              참여 가능만
            </button>
            {(
              [
                ['today', '오늘'],
                ['tomorrow', '내일'],
                ['week', '이번 주'],
              ] as const
            ).map(([when, label]) => (
              <button
                key={when}
                type="button"
                aria-pressed={value.when === when}
                className={chipClass(value.when === when)}
                onClick={() => set({ when: value.when === when ? 'all' : when })}
              >
                {label}
              </button>
            ))}
            <span className="mx-0.5 w-px shrink-0 self-stretch bg-line" aria-hidden />
          </>
        )}
        {CATEGORY_ORDER.map((c) => {
          const meta = CATEGORY[c]
          const active = value.categories.includes(c)
          return (
            <button key={c} type="button" aria-pressed={active} className={chipClass(active)} onClick={() => toggleCategory(c)}>
              <span className="size-2.5 rounded-full" style={{ backgroundColor: meta.color }} aria-hidden />
              {meta.label}
            </button>
          )
        })}
      </div>
    </>
  )
}

/** 정렬 버튼 · 몇 개 · 거르기 버튼 한 줄 */
export function FeedToolbar({
  sort,
  showSort,
  count,
  filterCount,
  onOpenSort,
  onOpenFilter,
}: {
  sort: FeedSort
  showSort: boolean
  /** 지금 보이는 3교시 수 (불러오는 중이면 null) */
  count: number | null
  filterCount: number
  onOpenSort: () => void
  onOpenFilter: () => void
}) {
  const toolButton = (active: boolean) =>
    `hit inline-flex h-9 min-w-0 items-center gap-1.5 rounded-xl border px-3 text-sm font-bold transition-colors ${
      active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:bg-surface-2'
    }`
  return (
    <div className="flex items-center gap-2">
      {showSort && (
        <button type="button" className={toolButton(false)} onClick={onOpenSort} aria-haspopup="dialog">
          <ArrowUpDown className="size-4 shrink-0 text-ink-3" aria-hidden />
          <span className="truncate">{sortLabel(sort)}</span>
          <ChevronDown className="size-4 shrink-0 text-ink-3" aria-hidden />
        </button>
      )}
      <span className="ml-auto shrink-0 text-sm text-ink-3 tabular" aria-live="polite">
        {count === null ? '' : `${count}개`}
      </span>
      <button
        type="button"
        className={`${toolButton(filterCount > 0)} shrink-0`}
        onClick={onOpenFilter}
        aria-haspopup="dialog"
        aria-label={filterCount > 0 ? `거르기, ${filterCount}개 조건` : '거르기'}
      >
        <SlidersHorizontal className="size-4 shrink-0" aria-hidden />
        거르기
        {filterCount > 0 && (
          <span className="tabular -mr-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1.5 text-xs font-extrabold text-on-accent">
            {filterCount}
          </span>
        )}
      </button>
    </div>
  )
}

/** 거리 기준 한 줄: 내 위치 / 우리 학교 기준, 내 위치로 바꾸기 */
export function OriginNote({
  geo,
  campusName,
  hasCampus,
  onUseMyLocation,
}: {
  geo: MyLocation
  campusName: string
  hasCampus: boolean
  onUseMyLocation: () => void
}) {
  const fallback = hasCampus ? `${campusName} 기준 거리예요` : '거리를 알 수 없어요'
  const text =
    geo.point !== null
      ? '내 위치 기준 거리예요'
      : geo.status === 'locating'
        ? '내 위치를 찾는 중이에요'
        : geo.status === 'denied'
          ? `위치를 쓸 수 없어서 ${hasCampus ? `${campusName} 기준이에요` : '거리를 알 수 없어요'}`
          : fallback
  const canAsk = geo.point === null && geo.status !== 'locating' && geo.status !== 'unavailable'
  return (
    <div className="flex min-h-9 flex-wrap items-center gap-x-2 gap-y-1 text-[13px] text-ink-3">
      <span className="flex min-w-0 items-center gap-1 break-keep">
        <MapPin className="size-3.5 shrink-0" aria-hidden />
        {text}
      </span>
      {canAsk && (
        <button type="button" onClick={onUseMyLocation} className="hit inline-flex items-center gap-1 font-bold whitespace-nowrap text-accent-ink">
          <LocateFixed className="size-3.5" aria-hidden />
          {geo.status === 'denied' ? '다시 시도' : '내 위치로 보기'}
        </button>
      )}
    </div>
  )
}

/** 정렬 고르기 창 */
export function SortSheet({ value, onChange, onClose }: { value: FeedSort; onChange: (s: FeedSort) => void; onClose: () => void }) {
  return (
    <BottomSheet title="정렬" onClose={onClose}>
      <SortOptions value={value} onChange={onChange} />
    </BottomSheet>
  )
}

function SortOptions({ value, onChange }: { value: FeedSort; onChange: (s: FeedSort) => void }) {
  return (
    <ul className="-mx-2 flex flex-col" role="radiogroup" aria-label="정렬">
      {FEED_SORTS.map((s) => {
        const active = s.value === value
        return (
          <li key={s.value}>
            <button
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => onChange(s.value)}
              className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-surface-2 ${active ? 'bg-surface-2' : ''}`}
            >
              <span className="min-w-0 flex-1">
                <span className={`block text-[15px] ${active ? 'font-extrabold text-ink' : 'font-semibold text-ink'}`}>{s.label}</span>
                <span className="mt-0.5 block text-[13px] leading-snug break-keep text-ink-3">{s.hint}</span>
              </span>
              <span
                className={`flex size-6 shrink-0 items-center justify-center rounded-full ${active ? 'bg-board text-on-board' : 'border border-line-strong'}`}
                aria-hidden
              >
                {active && <Check className="size-4" strokeWidth={3} />}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function SheetSection({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <fieldset className="flex flex-col gap-2.5 border-b border-line pb-5 last:border-b-0 last:pb-0">
      <legend className="mb-2.5 text-[15px] font-bold text-ink">{title}</legend>
      <div className="flex flex-wrap gap-2">{children}</div>
      {note && <p className="text-[13px] leading-relaxed break-keep text-ink-3">{note}</p>}
    </fieldset>
  )
}

/** 거르기 창: 거리 · 지역 · 학교(연합) · 모임 크기 · 참여 방식. 누르는 대로 바로 목록에 반영된다 */
export function FilterSheet({
  value,
  onChange,
  onClose,
  list,
  federated,
  resultCount,
  originNote,
  onRadius,
}: {
  value: FeedFilter
  onChange: (f: FeedFilter) => void
  onClose: () => void
  /** 거르기 전 목록 (지역·학교 칩을 만든다) */
  list: readonly MeetupSummary[]
  federated: boolean
  /** 거른 뒤 개수 */
  resultCount: number
  /** 거리 기준 한 줄 */
  originNote: ReactNode
  /** 거리를 고르면 (내 위치를 받으러 간다) */
  onRadius: (km: FeedFilter['radiusKm']) => void
}) {
  const set = (patch: Partial<FeedFilter>) => onChange({ ...value, ...patch })
  const toggle = <T,>(arr: readonly T[], v: T) => (arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v])
  const regions = regionOptions(list, value.regions)
  const schools = federated ? schoolOptions(list) : []
  const located = list.filter((m) => m.location).length
  const clear = () =>
    onChange({ ...value, radiusKm: null, regions: [], schools: [], sizes: [], noFee: false, lateJoin: false })

  return (
    <BottomSheet
      title="거르기"
      onClose={onClose}
      footer={
        <div className="flex gap-2">
          <button
            type="button"
            className={buttonClass('secondary', 'md', 'shrink-0')}
            onClick={clear}
            disabled={sheetFilterCount(value) === 0}
          >
            초기화
          </button>
          <button type="button" className={buttonClass('primary', 'md', 'flex-1')} onClick={onClose}>
            {resultCount}개 보기
          </button>
        </div>
      }
    >
      <div className="flex flex-col gap-5">
        <SheetSection
          title="거리"
          note={
            list.length > 0 && located < list.length
              ? '검색해서 고른 장소만 거리를 알 수 있어요'
              : undefined
          }
        >
          <button type="button" aria-pressed={value.radiusKm === null} className={chipClass(value.radiusKm === null)} onClick={() => onRadius(null)}>
            전체
          </button>
          {RADIUS_CHOICES.map((km) => (
            <button key={km} type="button" aria-pressed={value.radiusKm === km} className={chipClass(value.radiusKm === km)} onClick={() => onRadius(km)}>
              {km}km 안
            </button>
          ))}
          <div className="w-full">{originNote}</div>
        </SheetSection>

        <SheetSection title="지역" note={regions.length === 0 ? '아직 지역을 아는 3교시가 없어요' : undefined}>
          {regions.map(({ region, count }) => {
            const active = value.regions.includes(region)
            return (
              <button key={region} type="button" aria-pressed={active} className={chipClass(active)} onClick={() => set({ regions: toggle(value.regions, region) })}>
                {region}
                <span className={`tabular text-xs ${active ? 'opacity-80' : 'text-ink-3'}`}>{count}</span>
              </button>
            )
          })}
        </SheetSection>

        {federated && schools.length > 0 && (
          <SheetSection title="연 사람 학교">
            {schools.map(({ school, count }) => {
              const active = value.schools.includes(school.id)
              return (
                <button
                  key={school.id}
                  type="button"
                  aria-pressed={active}
                  className={chipClass(active)}
                  onClick={() => set({ schools: toggle(value.schools, school.id) })}
                >
                  {school.shortName}
                  <span className={`tabular text-xs ${active ? 'opacity-80' : 'text-ink-3'}`}>{count}</span>
                </button>
              )
            })}
          </SheetSection>
        )}

        <SheetSection title="모임 크기" note="최대 인원 기준, 제한 없음은 10명 이상">
          {SIZE_BANDS.map((b) => {
            const active = value.sizes.includes(b.value)
            return (
              <button key={b.value} type="button" aria-pressed={active} className={chipClass(active)} onClick={() => set({ sizes: toggle(value.sizes, b.value) })}>
                {b.label}
              </button>
            )
          })}
        </SheetSection>

        <SheetSection title="참여 방식">
          <button type="button" aria-pressed={value.noFee} className={chipClass(value.noFee)} onClick={() => set({ noFee: !value.noFee })}>
            참여비 없음
          </button>
          <button type="button" aria-pressed={value.lateJoin} className={chipClass(value.lateJoin)} onClick={() => set({ lateJoin: !value.lateJoin })}>
            중간 합류 OK
          </button>
        </SheetSection>
      </div>
    </BottomSheet>
  )
}
