import { useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import {
  Check,
  ChevronDown,
  ChevronLeft,
  CircleAlert,
  Globe2,
  Lock,
  Minus,
  Plus,
  School,
  SlidersHorizontal,
  X,
  type LucideIcon,
} from 'lucide-react'
import { ApiError } from '../api/client'
import { useCreateMeetup, useMeetup, useMyMeetups, usePopularPlaces, useUpdateMeetup } from '../api/queries'
import type { GeoPoint, MeetupAudience, MeetupCategory, MeetupDetail } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { LineGauge } from '../components/LineGauge'
import { OverlapNote } from '../components/OverlapNote'
import { Button, ErrorState, Field, FormError, Spinner } from '../components/ui'
import { cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import {
  addPurposes,
  CATEGORY,
  CATEGORY_ORDER,
  findOverlap,
  fitPeople,
  isReachedWith,
  type JoinedState,
  MAX_AHEAD_DAYS,
  MAX_CATEGORIES,
  MAX_PURPOSES,
  openChatError,
  optionsSummary,
  PURPOSE_MAX_LENGTH,
  PURPOSE_PRESETS,
  overlapMessage,
  peopleError,
  peopleRule,
  toggleCategory,
  type PeopleValues,
} from '../lib/meetup'
import { confirmLeave, readDraft, useLeaveGuard, writeDraft } from '../lib/leaveGuard'
import { mergePlaces } from '../lib/places'
import { PlacePicker } from '../components/PlacePicker'
import { placePresets } from '../lib/schools'
import { ceilTo10Minutes, formatDateTime, formatLeadTime, fromKstInputValue, kstAt, toKstInputValue } from '../lib/time'

interface DeadlineChoice {
  value: string
  label: string
  minutes: number
}

const DEADLINES: DeadlineChoice[] = [
  { value: '10', label: '10분 전', minutes: 10 },
  { value: '30', label: '30분 전', minutes: 30 },
  { value: '60', label: '1시간 전', minutes: 60 },
  { value: '0', label: '모일 때까지', minutes: 0 },
]

/** 수정할 때: 지금 마감이 위 선택지에 없으면(예전 "3시간 전" 등) 그 마감도 고를 수 있게 둔다 */
function deadlineChoices(leadMinutes?: number): DeadlineChoice[] {
  if (leadMinutes === undefined || DEADLINES.some((d) => d.minutes === leadMinutes)) return DEADLINES
  return [...DEADLINES, { value: 'keep', label: formatLeadTime(leadMinutes), minutes: leadMinutes }]
}

interface FormValues {
  title: string
  categories: MeetupCategory[]
  purposes: string[]
  meetAt: string
  deadline: string
  place: string
  /** 장소 검색에서 고른 위치 (직접 쓴 장소는 null) */
  location: GeoPoint | null
  /** 장소의 지역 (서울 성북구). 위치가 있을 때만 */
  region: string | null
  minPeople: number
  /** 최소 인원 없음 — 몇 명이 오든 진행 (최소 인원 무시) */
  noMinimum: boolean
  maxPeople: number
  /** 인원 제한 없음 (최대 인원 무시) */
  unlimited: boolean
  description: string
  openChatUrl: string
  /** 누구에게 여는지: 우리 학교 · 우리 기수 · 연합 */
  audience: MeetupAudience
  /** 중간 합류 OK */
  lateJoin: boolean
  /** 참여비 있음 */
  hasFee: boolean
  /** 참여비(원) 입력값 */
  fee: string
  feePrepaid: boolean
}

/** 제출 직전 시간 검사 (서버도 한 번 더 검사한다) */
function validateTimes(meetAt: Date | null, deadlineAt: Date | null): string | null {
  if (!meetAt || !deadlineAt) return '모임 시간을 정해 주세요'
  const nowMs = Date.now()
  if (meetAt.getTime() <= nowMs) return '모임 시간은 지금 이후로 정해 주세요'
  if (meetAt.getTime() > nowMs + MAX_AHEAD_DAYS * 86_400_000) return '3교시는 한 달 이내 일정만 열 수 있어요'
  if (deadlineAt.getTime() <= nowMs) return '모집 마감이 이미 지났어요. 마감을 늦춰 주세요'
  return null
}

function timePresets(now: Date) {
  const list = [
    { label: '오늘 수업 끝나고', date: kstAt(now, 0, 22) },
    { label: '1시간 뒤', date: ceilTo10Minutes(new Date(now.getTime() + 60 * 60_000)) },
    { label: '내일 저녁', date: kstAt(now, 1, 19) },
    { label: '내일 수업 끝나고', date: kstAt(now, 1, 22) },
  ]
  return list.filter((p) => p.date.getTime() > now.getTime() + 20 * 60_000)
}

function Stepper({
  label,
  value,
  min,
  max,
  onChange,
  disabled = false,
}: {
  label: string
  value: number
  min: number
  max: number
  onChange: (v: number) => void
  disabled?: boolean
}) {
  return (
    <div
      aria-disabled={disabled || undefined}
      className={`flex items-center justify-between gap-3 rounded-xl border border-line-strong bg-surface px-2 py-1.5 transition-opacity ${disabled ? 'pointer-events-none opacity-40' : ''}`}
    >
      <span className="pl-2 text-[15px] font-semibold text-ink">{label}</span>
      <div className="flex items-center gap-1">
        <button
          type="button"
          aria-label={`${label} 줄이기`}
          className="flex size-10 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 disabled:opacity-30"
          onClick={() => onChange(value - 1)}
          disabled={disabled || value <= min}
        >
          <Minus className="size-4" aria-hidden />
        </button>
        <output className="tabular w-10 text-center text-lg font-extrabold text-ink" aria-live="polite">
          {disabled ? '–' : value}
        </output>
        <button
          type="button"
          aria-label={`${label} 늘리기`}
          className="flex size-10 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 disabled:opacity-30"
          onClick={() => onChange(value + 1)}
          disabled={disabled || value >= max}
        >
          <Plus className="size-4" aria-hidden />
        </button>
      </div>
    </div>
  )
}

/** 스테퍼 아래 "없음" 스위치 */
function NoLimitToggle({
  checked,
  onChange,
  label,
  sub,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label: string
  sub: string
}) {
  return (
    <label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-xl px-3 py-2 text-[15px] font-semibold text-ink hover:bg-surface-2 has-focus-visible:ring-2 has-focus-visible:ring-accent">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="size-5 shrink-0 accent-[var(--accent)]"
      />
      <span className="flex flex-col leading-snug">
        {label}
        <span className="text-[13px] font-medium text-ink-3">{sub}</span>
      </span>
    </label>
  )
}

function Chip({
  active,
  onClick,
  children,
  disabled,
  label,
  radio = false,
}: {
  active: boolean
  onClick: () => void
  children: ReactNode
  disabled?: boolean
  label?: string
  /** 하나만 고르는 묶음(role="radiogroup") 안이면 라디오로 읽힌다 */
  radio?: boolean
}) {
  return (
    <button
      type="button"
      role={radio ? 'radio' : undefined}
      aria-checked={radio ? active : undefined}
      aria-pressed={radio ? undefined : active}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={`inline-flex min-h-10 items-center gap-1.5 rounded-xl border px-3.5 py-2 text-sm font-semibold transition-colors disabled:opacity-35 ${
        active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink-2 hover:border-ink-3 hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

function CategoryPicker({ value, onChange }: { value: MeetupCategory[]; onChange: (v: MeetupCategory[]) => void }) {
  return (
    <fieldset>
      <legend className="mb-1 text-[15px] font-semibold text-ink">무엇을 마실까요</legend>
      <p className="mb-3 text-sm text-ink-3">{MAX_CATEGORIES}개까지 골라요</p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {CATEGORY_ORDER.map((c) => {
          const meta = CATEGORY[c]
          const active = value.includes(c)
          // 3개를 골랐으면 나머지는 잠깐 막는다 ('아무거나'는 혼자라 언제나 고를 수 있다)
          const full = !active && c !== 'ANYTHING' && value.filter((x) => x !== 'ANYTHING').length >= MAX_CATEGORIES
          return (
            <label
              key={c}
              className={`relative flex min-h-12 items-center gap-2.5 rounded-xl border px-3 py-2 text-[15px] font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
                full ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'
              } ${
                active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:border-ink-3'
              }`}
            >
              <input
                type="checkbox"
                name="categories"
                value={c}
                checked={active}
                disabled={full}
                onChange={() => onChange(toggleCategory(value, c))}
                className="sr-only"
              />
              <span
                className="flex size-7 shrink-0 items-center justify-center rounded-full"
                style={{ backgroundColor: meta.color, color: meta.ink }}
              >
                <meta.Icon className="size-4" strokeWidth={2.4} aria-hidden />
              </span>
              {/* '하이볼·칵테일'이 '·'에서 끊기지 않게 잇는다. 좁은 폰에서는 고른 표시(✓) 대신 색으로 알린다 */}
              <span className="min-w-0 flex-1 text-[14px] sm:text-[15px]">{meta.label.replaceAll('·', '\u2060·\u2060')}</span>
              {active && <Check className="hidden size-4 shrink-0 sm:block" strokeWidth={3} aria-hidden />}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}

function PurposePicker({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const [draft, setDraft] = useState('')
  const full = value.length >= MAX_PURPOSES
  const custom = value.filter((p) => !(PURPOSE_PRESETS as readonly string[]).includes(p))

  function addDraft() {
    if (!draft.trim()) return
    onChange(addPurposes(value, draft))
    setDraft('')
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    // 한글 조합 중 Enter 는 글자 확정용이라 무시한다
    if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
      e.preventDefault()
      addDraft()
    }
  }

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-1 flex items-baseline gap-1.5 text-[15px] font-semibold text-ink">
        무슨 자리인가요
        <span className="text-xs font-medium text-ink-3">선택 · {value.length}/{MAX_PURPOSES}</span>
      </legend>
      <div className="flex flex-wrap gap-2">
        {PURPOSE_PRESETS.map((p) => {
          const active = value.includes(p)
          return (
            <Chip
              key={p}
              active={active}
              disabled={!active && full}
              onClick={() => onChange(active ? value.filter((x) => x !== p) : [...value, p])}
            >
              {p}
            </Chip>
          )
        })}
        {custom.map((p) => (
          <Chip key={p} active label={`${p} 빼기`} onClick={() => onChange(value.filter((x) => x !== p))}>
            {p}
            <X className="size-3.5" strokeWidth={2.6} aria-hidden />
          </Chip>
        ))}
      </div>
      <div className="flex gap-2">
        <input
          aria-label="모임 목적 직접 쓰기"
          className={`${inputClass} min-w-0 flex-1`}
          maxLength={PURPOSE_MAX_LENGTH * MAX_PURPOSES + 2}
          placeholder={full ? '3개까지 고를 수 있어요' : '직접 쓰기 (예: 졸업 축하, 논문 수다)'}
          value={draft}
          disabled={full}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <Button variant="secondary" onClick={addDraft} disabled={full || !draft.trim()} className="shrink-0">
          추가
        </Button>
      </div>
    </fieldset>
  )
}

/** 대상 선택지에 쓰는 개설자 학교·기수 */
interface HostTag {
  schoolName: string
  cohortLabel: string
}

function MeetupForm({
  initial,
  editing,
  current,
  host,
  draftKey,
}: {
  initial: FormValues & { id?: number; leadMinutes?: number; meetAtIso?: string }
  editing: boolean
  /** 새로 열 때 쓰던 내용을 임시저장할 곳 (sessionStorage 키) */
  draftKey?: string
  /** 수정할 때: 지금 참여 인원 · 성사 여부 */
  current?: JoinedState
  /** 수정할 때: 개설자 (대학원장이 고쳐도 대상은 개설자 학교·기수 기준) */
  host?: HostTag
}) {
  const navigate = useNavigate()
  const toast = useToast()
  const { member } = useAuth()
  // 장소 추천: 우리 학교에서 많이 쓴 곳 먼저, 남는 자리는 학교 근처 기본 장소
  const popular = usePopularPlaces()
  const presetPlaces = placePresets(member?.school.code)
  const places = mergePlaces(popular.data ?? [], presetPlaces)
  // 열 때는 꼭 정할 것만 보이고 나머지(인원·대상·참여 방식·선택 항목)는 접어 둔다. 고칠 때는 다 펼친다
  const [showOptions, setShowOptions] = useState(editing || initial.description !== '' || initial.openChatUrl !== '')
  const audienceOf: HostTag | null =
    host ?? (member ? { schoolName: member.school.shortName, cohortLabel: member.cohortLabel } : null)
  const choices = useMemo(() => deadlineChoices(initial.leadMinutes), [initial.leadMinutes])
  const create = useCreateMeetup()
  const update = useUpdateMeetup(initial.id ?? 0)
  const [v, setV] = useState<FormValues>(initial)
  const [error, setError] = useState<string | null>(null)
  /** 오류가 난 칸: 그 칸 아래에 알려 주고 그 칸으로 데려간다 (없으면 제출 버튼 위에) */
  const [errorField, setErrorField] = useState<ErrorField | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const now = useMemo(() => new Date(), [])
  const presets = useMemo(() => timePresets(now), [now])

  const set = <K extends keyof FormValues>(key: K, value: FormValues[K]) => setV((prev) => ({ ...prev, [key]: value }))
  // 쓰던 내용이 있으면 나가기 전에 묻는다. 새로 여는 중이면 임시저장도 한다 (웹 브라우저 뒤로는 막을 수 없어서)
  const dirty = JSON.stringify(v) !== JSON.stringify(initial)
  const [saved, setSaved] = useState(false)
  useLeaveGuard(dirty && !saved)
  useEffect(() => {
    if (!editing && draftKey) writeDraft(draftKey, dirty && !saved ? v : null)
  }, [editing, draftKey, dirty, saved, v])
  /** 인원 입력은 바꿀 때마다 최소 ≤ 최대가 되게 맞춘다 */
  const setPeople = (patch: Partial<PeopleValues>) => setV((prev) => fitPeople({ ...prev, ...patch }))

  // 수정에서 모임 시간을 건드리지 않았으면 원래 시각 그대로 (입력 칸은 분 단위라 초가 잘린다)
  const meetAt =
    initial.meetAtIso && v.meetAt === initial.meetAt ? new Date(initial.meetAtIso) : fromKstInputValue(v.meetAt)
  const deadlineMinutes = (choices.find((d) => d.value === v.deadline) ?? DEADLINES[0]).minutes
  const deadlineAt = meetAt ? new Date(meetAt.getTime() - deadlineMinutes * 60_000) : null
  const pending = create.isPending || update.isPending
  const minPeople = v.noMinimum ? null : v.minPeople
  const maxPeople = v.unlimited ? null : v.maxPeople
  const joined = current?.joined ?? 1
  const reachedNow = isReachedWith(minPeople, joined)
  // 시간을 고르는 순간 내 다른 3교시와 겹치는지 알려 준다 (고칠 때는 시간을 바꿨을 때만, 이 3교시 자신은 빼고)
  const mine = useMyMeetups()
  const timeTouched = !editing || v.meetAt !== initial.meetAt
  const clash = timeTouched && meetAt ? findOverlap(mine.data ?? [], meetAt, initial.id ?? null) : null

  /** 틀린 칸으로 화면을 옮기고 그 칸에 커서를 둔다 (접혀 있던 옵션은 먼저 펼친다) */
  function fail(message: string, field: ErrorField) {
    setError(message)
    setErrorField(field)
    requestAnimationFrame(() => {
      const box = formRef.current?.querySelector<HTMLElement>(`[data-field="${field}"]`)
      if (!box) return
      box.scrollIntoView({ block: 'center', behavior: 'smooth' })
      box
        .querySelector<HTMLElement>('input:not([type="hidden"]):not(:disabled), textarea, button:not(:disabled)')
        ?.focus({ preventScroll: true })
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setErrorField(null)
    if (!v.title.trim()) return fail('제목을 입력해 주세요', 'title')
    if (v.title.trim().length < 2) return fail('제목은 2자 이상 적어 주세요', 'title')
    if (v.categories.length === 0) return fail('무엇을 마실지 하나 이상 골라 주세요', 'categories')

    // 수정에서는 시간을 바꿨을 때만 검사한다 (마감이 지난 성사 3교시도 다른 칸은 고칠 수 있게)
    const scheduleChanged = !editing || v.meetAt !== initial.meetAt || v.deadline !== initial.deadline
    if (scheduleChanged) {
      const timeError = validateTimes(meetAt, deadlineAt)
      if (timeError) return fail(timeError, 'time')
    }
    if (!meetAt || !deadlineAt) return fail('모임 시간을 정해 주세요', 'time')
    if (clash) return fail(overlapMessage(clash), 'time')
    if (!v.place.trim()) return fail('장소를 입력해 주세요', 'place')
    const peopleProblem = peopleError(minPeople, maxPeople, current)
    if (peopleProblem) {
      setShowOptions(true)
      return fail(peopleProblem, 'people')
    }
    const fee = v.hasFee ? Number(v.fee) : null
    if (fee !== null && !(fee >= 1000 && fee <= 1_000_000)) {
      setShowOptions(true)
      return fail('참여비는 1,000원 ~ 100만 원으로 적어 주세요', 'fee')
    }
    const chatProblem = openChatError(v.openChatUrl)
    if (chatProblem) {
      setShowOptions(true)
      return fail(chatProblem, 'openChat')
    }

    try {
      if (editing && initial.id) {
        const d = await update.mutateAsync({
          title: v.title,
          description: v.description,
          place: v.place,
          location: v.location ?? null,
          region: v.location ? (v.region ?? null) : null,
          categories: v.categories,
          purposes: v.purposes,
          openChatUrl: v.openChatUrl,
          people: { minPeople, maxPeople },
          schedule: scheduleChanged ? { meetAt: meetAt.toISOString(), deadlineAt: deadlineAt.toISOString() } : null,
          audience: v.audience,
          options: { lateJoin: v.lateJoin, fee, feePrepaid: fee !== null && v.feePrepaid },
        })
        setSaved(true)
        toast.show('수정했어요', 'ok')
        // 상세에서 왔으면 그 상세로 돌아간다 (새로 쌓으면 뒤로가기에 같은 상세가 두 번 남는다)
        if ((window.history.state?.idx ?? 0) > 0) navigate(-1)
        else navigate(`/meetups/${d.id}`, { replace: true })
        return
      }
      const d = await create.mutateAsync({
        title: v.title,
        description: v.description,
        place: v.place,
        location: v.location ?? null,
        region: v.location ? (v.region ?? null) : null,
        categories: v.categories,
        purposes: v.purposes,
        meetAt: meetAt.toISOString(),
        deadlineAt: deadlineAt.toISOString(),
        minPeople,
        maxPeople,
        openChatUrl: v.openChatUrl,
        audience: v.audience,
        lateJoin: v.lateJoin,
        fee,
        feePrepaid: fee !== null && v.feePrepaid,
      })
      setSaved(true)
      if (draftKey) writeDraft(draftKey, null)
      toast.show(
        v.audience === 'COHORT'
          ? '3교시를 열었어요! 우리 기수 단톡방에 공유해 보세요'
          : v.audience === 'ALL'
            ? '연합 3교시를 열었어요! 연합 단톡방에 공유해 보세요'
            : '3교시를 열었어요! 단톡방에 공유해 보세요',
        'ok',
      )
      navigate(`/meetups/${d.id}`, { replace: true })
    } catch (err) {
      // 그사이 다른 3교시에 참여해서 시간이 겹치게 됐으면 시간 칸으로 데려간다
      if (err instanceof ApiError && err.code === 'TIME_OVERLAP') return fail(err.message, 'time')
      setShowOptions(true) // 접어 둔 칸의 문제일 수 있어서 펼쳐 보여 준다
      setError(err instanceof Error ? err.message : '저장하지 못했어요')
    }
  }

  return (
    <form ref={formRef} onSubmit={submit} className="flex flex-col gap-8" noValidate>
      <div data-field="title">
        <Field label="제목" error={errorField === 'title' ? error : null}>
          {({ id, describedBy, invalid }) => (
            <input
              id={id}
              aria-describedby={describedBy}
              aria-invalid={invalid || undefined}
              className={inputClass}
              maxLength={40}
              enterKeyHint="next"
              onKeyDown={blockEnterSubmit}
              placeholder="예: 수업 끝나고 치맥 한잔?"
              value={v.title}
              onChange={(e) => set('title', e.target.value)}
            />
          )}
        </Field>
      </div>

      <div data-field="categories" className="flex flex-col gap-2">
        <CategoryPicker value={v.categories} onChange={(c) => set('categories', c)} />
        {errorField === 'categories' && <FieldAlert message={error} />}
      </div>

      <fieldset data-field="time" className="flex flex-col gap-3">
        <legend className="mb-3 text-[15px] font-semibold text-ink">언제 모일까요</legend>
        <div className="flex flex-wrap gap-2">
          {presets.map((p) => {
            const value = toKstInputValue(p.date)
            return (
              <Chip key={p.label} active={v.meetAt === value} onClick={() => set('meetAt', value)}>
                {p.label}
                <span className="tabular opacity-70">{value.slice(11)}</span>
              </Chip>
            )
          })}
        </div>
        <input
          type="datetime-local"
          aria-label="모임 날짜와 시간 직접 고르기"
          className={inputClass}
          value={v.meetAt}
          min={toKstInputValue(now)}
          max={toKstInputValue(new Date(now.getTime() + MAX_AHEAD_DAYS * 86_400_000))}
          onChange={(e) => set('meetAt', e.target.value)}
        />

        <p className="mt-3 text-[15px] font-semibold text-ink">모집은 언제 마감할까요</p>
        <div className="flex flex-wrap gap-2">
          {choices.map((d) => {
            const disabled = meetAt ? meetAt.getTime() - d.minutes * 60_000 <= now.getTime() : false
            return (
              <Chip key={d.value} active={v.deadline === d.value} disabled={disabled} onClick={() => set('deadline', d.value)}>
                {d.label}
              </Chip>
            )
          })}
        </div>
        {meetAt && deadlineAt && (
          <p className="text-sm text-ink-3">
            {formatDateTime(meetAt)} 모임 · <b className="font-semibold text-ink-2">{formatDateTime(deadlineAt)}</b>
            {reachedNow ? '에 모집 마감' : '까지 안 모이면 조용히 사라져요'}
          </p>
        )}
        {/* 겹치면 시간을 고르는 순간 알려 준다. 그 밖의 시간 오류는 저장할 때 */}
        {clash && <OverlapNote meetup={clash} />}
        {errorField === 'time' && !(clash && error === overlapMessage(clash)) && <FieldAlert message={error} />}
      </fieldset>

      <div data-field="place">
        <Field
          label="어디서"
          hint="찾아서 고르면 가까운 순에 나와요"
          error={errorField === 'place' ? error : null}
        >
          {({ id, describedBy, invalid }) => (
            <PlacePicker
              id={id}
              describedBy={describedBy}
              invalid={invalid}
              placeholder={`예: ${presetPlaces[0] ?? '역'} 앞 치킨집`}
              value={{ place: v.place, location: v.location ?? null, region: v.region ?? null }}
              onChange={(next) => setV((prev) => ({ ...prev, ...next }))}
              suggestions={places}
            />
          )}
        </Field>
      </div>

      {!showOptions && (
        <button
          type="button"
          onClick={() => setShowOptions(true)}
          aria-expanded={false}
          className={`${cardClass} flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-surface-2`}
        >
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink-2">
            <SlidersHorizontal className="size-5" aria-hidden />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-[15px] font-bold text-ink">옵션 더 보기</span>
            <span className="block truncate text-[13px] text-ink-3">
              {optionsSummary({
                minPeople,
                maxPeople,
                audience: v.audience,
                lateJoin: v.lateJoin,
                fee: v.hasFee && v.fee ? Number(v.fee) : null,
              })}
            </span>
          </span>
          <ChevronDown className="size-5 shrink-0 text-ink-3" aria-hidden />
        </button>
      )}

      {showOptions && (
        <>
          <fieldset data-field="people" className="flex flex-col gap-3">
            <legend className="mb-3 text-[15px] font-semibold text-ink">몇 명이 모이면 좋을까요 (나 포함)</legend>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1">
                <Stepper
                  label="최소"
                  value={v.minPeople}
                  min={2}
                  max={current?.reached ? Math.max(2, Math.min(20, joined)) : 20}
                  disabled={v.noMinimum}
                  onChange={(n) => setPeople({ minPeople: n })}
                />
                <NoLimitToggle
                  checked={v.noMinimum}
                  onChange={(on) => setPeople({ noMinimum: on })}
                  label="최소 인원 없음"
                  sub="몇 명이 오든 진행해요"
                />
              </div>
              <div className="flex flex-col gap-1">
                <Stepper
                  label="최대"
                  value={v.maxPeople}
                  min={Math.max(2, v.noMinimum ? 2 : v.minPeople, current ? joined : 0)}
                  max={30}
                  disabled={v.unlimited}
                  onChange={(n) => setPeople({ maxPeople: n })}
                />
                <NoLimitToggle
                  checked={v.unlimited}
                  onChange={(on) => setPeople({ unlimited: on })}
                  label="최대 인원 없음"
                  sub="오는 사람 다 받아요"
                />
              </div>
            </div>
            <div className={`${cardClass} flex flex-col gap-2.5 px-4 py-4`}>
              <LineGauge count={joined} min={minPeople} max={maxPeople} status={reachedNow ? 'CONFIRMED' : 'RECRUITING'} />
              <p className="text-sm text-ink-2">
                <b className="text-ink">{peopleRule(minPeople, maxPeople)}</b>
                {current && <span className="tabular"> · 지금 {joined}명</span>}
              </p>
              {current ? (
                current.reached ? (
                  <p className="text-sm leading-relaxed text-ink-3">이미 성사돼서 최소 인원은 {joined}명까지만 올릴 수 있어요</p>
                ) : (
                  reachedNow && (
                    <p className="text-sm leading-relaxed text-ink-3">저장하면 바로 성사 · 참여자끼리 실명·단톡방 공개</p>
                  )
                )
              ) : (
                v.noMinimum && (
                  <p className="text-sm leading-relaxed text-ink-3">열자마자 확정 · 참여자끼리 바로 실명·단톡방 공개</p>
                )
              )}
            </div>
            {errorField === 'people' && <FieldAlert message={error} />}
          </fieldset>

          {audienceOf && (
            <fieldset>
              <legend className="mb-2 text-[15px] font-semibold text-ink">누구와 함께할까요</legend>
              <div className="grid gap-2 sm:grid-cols-3">
                {(
                  [
                    { value: 'SCHOOL', Icon: School, label: '우리 학교', sub: `${audienceOf.schoolName} 원우 누구나` },
                    {
                      value: 'COHORT',
                      Icon: Lock,
                      label: '우리 기수만',
                      sub: `${audienceOf.schoolName} ${audienceOf.cohortLabel}만 보여요`,
                    },
                    { value: 'ALL', Icon: Globe2, label: '연합', sub: '연합 학교 누구나 · 연합 게시판에' },
                  ] satisfies { value: MeetupAudience; Icon: LucideIcon; label: string; sub: string }[]
                ).map(({ value, Icon, label, sub }) => {
                  const active = v.audience === value
                  return (
                    <label
                      key={value}
                      className={`flex min-h-16 cursor-pointer items-center gap-3 rounded-xl border px-3.5 py-3 transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent sm:flex-col sm:items-start sm:gap-1.5 ${
                        active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:border-ink-3'
                      }`}
                    >
                      <input
                        type="radio"
                        name="audience"
                        value={value}
                        checked={active}
                        onChange={() => set('audience', value)}
                        className="sr-only"
                      />
                      <Icon className={`size-5 shrink-0 ${active ? 'text-accent' : 'text-ink-3'}`} aria-hidden />
                      <span className="flex flex-col leading-snug">
                        <span className="text-[15px] font-bold">{label}</span>
                        <span className={`text-[13px] ${active ? 'text-on-board-2' : 'text-ink-3'}`}>{sub}</span>
                      </span>
                    </label>
                  )
                })}
              </div>
              {editing && <p className="mt-2 px-1 text-[13px] text-ink-3">이미 참여한 원우가 빠지게 좁힐 수는 없어요</p>}
            </fieldset>
          )}

          <fieldset data-field="fee" className="flex flex-col gap-3">
            <legend className="mb-2 text-[15px] font-semibold text-ink">참여 방식</legend>
            <NoLimitToggle
              checked={v.lateJoin}
              onChange={(on) => set('lateJoin', on)}
              label="중간 합류 OK"
              sub="늦게 와도 돼요 · 성사 뒤에도 시작 전까지 참여"
            />
            <div className={`${cardClass} flex flex-col gap-3 px-4 py-4`}>
              <div className="flex items-center justify-between gap-3">
                <span className="text-[15px] font-semibold text-ink">참여비</span>
                <div className="flex gap-1.5" role="radiogroup" aria-label="참여비">
                  <Chip radio active={!v.hasFee} onClick={() => set('hasFee', false)}>
                    없음
                  </Chip>
                  <Chip radio active={v.hasFee} onClick={() => set('hasFee', true)}>
                    있음
                  </Chip>
                </div>
              </div>
              {v.hasFee && (
                <>
                  <div className="relative">
                    <input
                      aria-label="참여비 금액"
                      type="number"
                      inputMode="numeric"
                      min={1000}
                      max={1000000}
                      step={1000}
                      className={`${inputClass} tabular pr-10 font-semibold`}
                      placeholder="예: 10000"
                      value={v.fee}
                      onChange={(e) => set('fee', e.target.value)}
                    />
                    <span className="pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-ink-3">원</span>
                  </div>
                  <NoLimitToggle
                    checked={v.feePrepaid}
                    onChange={(on) => set('feePrepaid', on)}
                    label="선입금"
                    sub="참여할 때 미리 보내요 · 계좌는 단톡방에서"
                  />
                </>
              )}
            </div>
            {errorField === 'fee' && <FieldAlert message={error} />}
          </fieldset>

          {/* 선택 항목은 아래에 모은다 */}
          <div className="flex flex-col gap-8 border-t border-line pt-8">
            <p className="-mb-5 text-sm font-bold text-ink-3">선택 · 비워도 돼요</p>
            <PurposePicker value={v.purposes} onChange={(p) => set('purposes', p)} />

            <Field label="한마디" optional>
              {({ id }) => (
                <textarea
                  id={id}
                  rows={3}
                  maxLength={500}
                  className={`${inputClass} h-auto py-3 leading-relaxed`}
                  placeholder="예: 늦게 와도 괜찮아요. 1차만 가볍게!"
                  value={v.description}
                  onChange={(e) => set('description', e.target.value)}
                />
              )}
            </Field>

            <div data-field="openChat">
              <Field
                label="카카오톡 오픈채팅 링크"
                optional
                hint="성사되면 참여자에게만 보여요 · 나중에 넣어도 돼요"
                error={errorField === 'openChat' ? error : null}
              >
                {({ id, describedBy }) => (
                  <input
                    id={id}
                    type="url"
                    inputMode="url"
                    aria-describedby={describedBy}
                    className={inputClass}
                    placeholder="https://open.kakao.com/o/…"
                    value={v.openChatUrl}
                    onChange={(e) => set('openChatUrl', e.target.value.trim())}
                  />
                )}
              </Field>
            </div>
          </div>
        </>
      )}

      {/* 칸에 딸린 오류는 그 칸에서 보여 준다. 여기에는 서버에서 온 오류만 */}
      <FormError message={errorField ? null : error} />

      <div className="flex gap-2.5">
        <Button variant="secondary" size="lg" onClick={() => confirmLeave() && goBack(navigate)} className="flex-1 sm:flex-none">
          돌아가기
        </Button>
        <Button type="submit" size="lg" loading={pending} className="flex-[2] sm:flex-none sm:px-10">
          {editing ? '수정 완료' : '3교시 열기'}
        </Button>
      </div>
    </form>
  )
}

type ErrorField = 'title' | 'categories' | 'time' | 'place' | 'people' | 'fee' | 'openChat'

/** 칸 바로 아래의 오류 한 줄 */
function FieldAlert({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p role="alert" className="flex items-start gap-1.5 text-sm leading-relaxed font-semibold text-danger-ink">
      <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
      {message}
    </p>
  )
}

function defaults(audience: MeetupAudience): FormValues {
  const now = new Date()
  const first = timePresets(now)[0]?.date ?? ceilTo10Minutes(new Date(now.getTime() + 2 * 60 * 60_000))
  return {
    title: '',
    categories: ['BEER'],
    purposes: [],
    meetAt: toKstInputValue(first),
    deadline: first.getTime() - 10 * 60_000 > now.getTime() ? '10' : '0',
    place: '',
    location: null,
    region: null,
    minPeople: 3,
    noMinimum: false,
    maxPeople: 6,
    unlimited: false,
    description: '',
    openChatUrl: '',
    audience,
    lateJoin: false,
    hasFee: false,
    fee: '',
    feePrepaid: false,
  }
}

function fromDetail(d: MeetupDetail): FormValues & { id: number; leadMinutes: number; meetAtIso: string } {
  const leadMinutes = Math.round((new Date(d.meetAt).getTime() - new Date(d.deadlineAt).getTime()) / 60_000)
  // 최대 인원 없음이던 3교시는 최대를 켤 때 지금 인원보다 적지 않게 시작한다
  return {
    id: d.id,
    leadMinutes,
    meetAtIso: d.meetAt,
    title: d.title,
    categories: d.categories,
    purposes: d.purposes,
    meetAt: toKstInputValue(new Date(d.meetAt)),
    deadline: DEADLINES.find((c) => c.minutes === leadMinutes)?.value ?? 'keep',
    place: d.place,
    location: d.location ?? null,
    region: d.region ?? null,
    minPeople: d.minPeople ?? 2,
    noMinimum: d.minPeople === null,
    maxPeople: d.maxPeople ?? Math.max(6, d.minPeople ?? 2, d.participantCount),
    unlimited: d.maxPeople === null,
    description: d.description,
    openChatUrl: d.openChatUrl ?? '',
    audience: d.audience,
    lateJoin: d.lateJoin,
    hasFee: d.fee !== null,
    fee: d.fee === null ? '' : String(d.fee),
    feePrepaid: d.feePrepaid,
  }
}

/** 지난 3교시를 그대로 다시 열기: 내용은 가져오고 시간은 새로 (열 때와 같은 기본 시간) */
function copyFrom(d: MeetupDetail, base: FormValues): FormValues {
  return {
    ...base,
    title: d.title,
    categories: d.categories,
    purposes: d.purposes,
    place: d.place,
    location: d.location ?? null,
    region: d.region ?? null,
    minPeople: d.minPeople ?? 2,
    noMinimum: d.minPeople === null,
    maxPeople: d.maxPeople ?? Math.max(6, d.minPeople ?? 2),
    unlimited: d.maxPeople === null,
    description: d.description,
    openChatUrl: d.hosting ? (d.openChatUrl ?? '') : '',
    audience: d.audience,
    lateJoin: d.lateJoin,
    hasFee: d.fee !== null,
    fee: d.fee === null ? '' : String(d.fee),
    feePrepaid: d.feePrepaid,
  }
}

/** 제목·장소 칸에서 Enter 로 폼이 바로 제출되지 않게 (한글 조합 중 Enter 는 그대로) */
function blockEnterSubmit(e: KeyboardEvent<HTMLInputElement>) {
  if (e.key === 'Enter' && !e.nativeEvent.isComposing) e.preventDefault()
}

/** 앞 화면으로. 이 화면으로 바로 들어왔으면(앞 화면이 없으면) 3교시 목록으로 */
function goBack(navigate: ReturnType<typeof useNavigate>) {
  if ((window.history.state?.idx ?? 0) > 0) navigate(-1)
  else navigate('/', { replace: true })
}

function Header({ title, sub }: { title: string; sub: string }) {
  const navigate = useNavigate()
  return (
    <div className="mb-8">
      <button
        type="button"
        onClick={() => confirmLeave() && goBack(navigate)}
        className="mb-4 -ml-2 inline-flex h-10 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
      >
        <ChevronLeft className="size-5" aria-hidden />
        뒤로
      </button>
      <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">{title}</h1>
      <p className="mt-1.5 text-[15px] text-ink-2">{sub}</p>
    </div>
  )
}

export function MeetupCreatePage() {
  const [params] = useSearchParams()
  const from = Number(params.get('from'))
  if (Number.isInteger(from) && from > 0) return <CopyMeetupPage from={from} />
  return <NewMeetupPage audience={params.get('audience') === 'ALL' ? 'ALL' : 'SCHOOL'} />
}

const DRAFT_KEY = 'anam3.meetupDraft'

function NewMeetupPage({ audience }: { audience: MeetupAudience }) {
  const [fresh, setFresh] = useState(0)
  const [draft] = useState(() => readDraft<FormValues>(DRAFT_KEY))
  const restored = draft !== null && fresh === 0
  const [initial, setInitial] = useState(() => draft ?? defaults(audience))
  return (
    <div className="max-w-2xl">
      <Header title="3교시 열기" sub="안 모이면 조용히 사라져요. 부담 없이!" />
      {restored && (
        <p className="mb-6 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2">
          쓰던 내용을 불러왔어요.
          <button
            type="button"
            className="hit font-semibold text-accent-ink underline underline-offset-2"
            onClick={() => {
              writeDraft(DRAFT_KEY, null)
              setInitial(defaults(audience))
              setFresh((n) => n + 1)
            }}
          >
            새로 쓰기
          </button>
        </p>
      )}
      <MeetupForm key={fresh} initial={initial} editing={false} draftKey={DRAFT_KEY} />
    </div>
  )
}

/** 지난 3교시 다시 열기 (/meetups/new?from=12). 못 불러오면 빈 양식으로 */
function CopyMeetupPage({ from }: { from: number }) {
  const { data, isPending } = useMeetup(from)
  if (isPending) return <Spinner />
  if (!data) return <NewMeetupPage audience="SCHOOL" />
  return (
    <div className="max-w-2xl">
      <Header title="다시 열기" sub="지난 3교시 내용을 가져왔어요. 시간만 정하면 돼요" />
      <MeetupForm initial={copyFrom(data, defaults(data.audience))} editing={false} />
    </div>
  )
}

export function MeetupEditPage() {
  const id = Number(useParams().id)
  const { data, isPending, error, refetch } = useMeetup(id)
  if (isPending) return <Spinner />
  // 처음 불러오기가 실패했을 때만 (고치는 중 다시 불러오기가 한 번 실패해도 쓰던 내용은 그대로 둔다)
  if (!data) return <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
  if (!data.canEdit) return <ErrorState message="이 3교시는 수정할 수 없어요" />
  return (
    <div className="max-w-2xl">
      <Header title="3교시 수정" sub="바꾸면 참여한 원우에게 바로 보여요" />
      <MeetupForm
        initial={fromDetail(data)}
        editing
        current={{ joined: data.participantCount, reached: isReachedWith(data.minPeople, data.participantCount) }}
        host={{ schoolName: data.school.shortName, cohortLabel: data.host.cohortLabel }}
      />
    </div>
  )
}
