import { useMemo, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { Check, ChevronLeft, Globe2, Lock, Minus, Plus, School, X, type LucideIcon } from 'lucide-react'
import { useCreateMeetup, useMeetup, useUpdateMeetup } from '../api/queries'
import type { MeetupAudience, MeetupCategory, MeetupDetail } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { LineGauge } from '../components/LineGauge'
import { Button, ErrorState, Field, FormError, Spinner } from '../components/ui'
import { cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import {
  addPurposes,
  CATEGORY,
  CATEGORY_ORDER,
  fitPeople,
  MAX_PURPOSES,
  PURPOSE_MAX_LENGTH,
  PURPOSE_PRESETS,
  peopleError,
  peopleRule,
  toggleCategory,
  type PeopleValues,
} from '../lib/meetup'
import { placePresets } from '../lib/schools'
import { ceilTo10Minutes, formatDateTime, fromKstInputValue, kstAt, toKstInputValue } from '../lib/time'

const DEADLINES = [
  { value: '30', label: '30분 전', minutes: 30 },
  { value: '60', label: '1시간 전', minutes: 60 },
  { value: '180', label: '3시간 전', minutes: 180 },
  { value: '0', label: '모일 때까지', minutes: 0 },
] as const

type DeadlineValue = (typeof DEADLINES)[number]['value']

interface FormValues {
  title: string
  categories: MeetupCategory[]
  purposes: string[]
  meetAt: string
  deadline: DeadlineValue
  place: string
  minPeople: number
  /** 최소 인원 없음 — 몇 명이 오든 진행 (최소 인원 무시) */
  noMinimum: boolean
  maxPeople: number
  /** 인원 제한 없음 (최대 인원 무시) */
  unlimited: boolean
  description: string
  openChatUrl: string
  /** 누구에게 여는지: 우리 학교 · 우리 기수 · 연합 (열 때만 정한다) */
  audience: MeetupAudience
}

/** 제출 직전 시간 검사 (서버도 한 번 더 검사한다) */
function validateTimes(meetAt: Date | null, deadlineAt: Date | null): string | null {
  if (!meetAt || !deadlineAt) return '모임 시간을 정해 주세요'
  const nowMs = Date.now()
  if (meetAt.getTime() <= nowMs) return '모임 시간은 지금 이후로 정해 주세요'
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
}: {
  active: boolean
  onClick: () => void
  children: ReactNode
  disabled?: boolean
  label?: string
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
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
      <p className="mb-3 text-sm text-ink-3">여러 개 골라도 돼요. 소맥이면 소주랑 맥주 둘 다!</p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {CATEGORY_ORDER.map((c) => {
          const meta = CATEGORY[c]
          const active = value.includes(c)
          return (
            <label
              key={c}
              className={`relative flex min-h-12 cursor-pointer items-center gap-2.5 rounded-xl border px-3 py-2 text-[15px] font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
                active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:border-ink-3'
              }`}
            >
              <input
                type="checkbox"
                name="categories"
                value={c}
                checked={active}
                onChange={() => onChange(toggleCategory(value, c))}
                className="sr-only"
              />
              <span
                className="flex size-7 shrink-0 items-center justify-center rounded-full"
                style={{ backgroundColor: meta.color, color: meta.ink }}
              >
                <meta.Icon className="size-4" strokeWidth={2.4} aria-hidden />
              </span>
              <span className="min-w-0 flex-1">{meta.label}</span>
              {active && <Check className="size-4 shrink-0" strokeWidth={3} aria-hidden />}
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

function MeetupForm({ initial, editing }: { initial: FormValues & { id?: number }; editing: boolean }) {
  const navigate = useNavigate()
  const toast = useToast()
  const { member } = useAuth()
  const places = placePresets(member?.school.code)
  const create = useCreateMeetup()
  const update = useUpdateMeetup(initial.id ?? 0)
  const [v, setV] = useState<FormValues>(initial)
  const [error, setError] = useState<string | null>(null)
  const now = useMemo(() => new Date(), [])
  const presets = useMemo(() => timePresets(now), [now])

  const set = <K extends keyof FormValues>(key: K, value: FormValues[K]) => setV((prev) => ({ ...prev, [key]: value }))
  /** 인원 입력은 바꿀 때마다 최소 ≤ 최대가 되게 맞춘다 */
  const setPeople = (patch: Partial<PeopleValues>) => setV((prev) => fitPeople({ ...prev, ...patch }))

  const meetAt = fromKstInputValue(v.meetAt)
  const deadlineMinutes = DEADLINES.find((d) => d.value === v.deadline)!.minutes
  const deadlineAt = meetAt ? new Date(meetAt.getTime() - deadlineMinutes * 60_000) : null
  const pending = create.isPending || update.isPending
  const minPeople = v.noMinimum ? null : v.minPeople
  const maxPeople = v.unlimited ? null : v.maxPeople

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!v.title.trim()) return setError('제목을 입력해 주세요')
    if (v.categories.length === 0) return setError('무엇을 마실지 하나 이상 골라 주세요')
    if (!v.place.trim()) return setError('장소를 입력해 주세요')

    try {
      if (editing && initial.id) {
        const d = await update.mutateAsync({
          title: v.title,
          description: v.description,
          place: v.place,
          categories: v.categories,
          purposes: v.purposes,
          openChatUrl: v.openChatUrl,
        })
        toast.show('수정했어요', 'ok')
        navigate(`/meetups/${d.id}`, { replace: true })
        return
      }
      const timeError = validateTimes(meetAt, deadlineAt)
      if (timeError || !meetAt || !deadlineAt) return setError(timeError ?? '모임 시간을 정해 주세요')
      const peopleProblem = peopleError(minPeople, maxPeople)
      if (peopleProblem) return setError(peopleProblem)
      const d = await create.mutateAsync({
        title: v.title,
        description: v.description,
        place: v.place,
        categories: v.categories,
        purposes: v.purposes,
        meetAt: meetAt.toISOString(),
        deadlineAt: deadlineAt.toISOString(),
        minPeople,
        maxPeople,
        openChatUrl: v.openChatUrl,
        audience: v.audience,
      })
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
      setError(err instanceof Error ? err.message : '저장하지 못했어요')
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-8" noValidate>
      <Field label="제목">
        {({ id }) => (
          <input
            id={id}
            className={inputClass}
            maxLength={40}
            placeholder="예: 수업 끝나고 치맥 한잔?"
            value={v.title}
            onChange={(e) => set('title', e.target.value)}
          />
        )}
      </Field>

      <CategoryPicker value={v.categories} onChange={(c) => set('categories', c)} />

      <PurposePicker value={v.purposes} onChange={(p) => set('purposes', p)} />

      {!editing && (
        <fieldset className="flex flex-col gap-3">
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
            onChange={(e) => set('meetAt', e.target.value)}
          />

          <p className="mt-3 text-[15px] font-semibold text-ink">모집은 언제 마감할까요</p>
          <div className="flex flex-wrap gap-2">
            {DEADLINES.map((d) => {
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
              {v.noMinimum ? '에 모집을 마감해요' : '까지 최소 인원이 안 모이면 조용히 사라져요'}
            </p>
          )}
        </fieldset>
      )}

      <Field label="어디서" hint="칩은 빠른 입력용이에요. 학교 앞 가게 이름을 그대로 적어도 되고, 다른 동네도 괜찮아요.">
        {({ id, describedBy }) => (
          <div className="flex flex-col gap-3">
            <input
              id={id}
              aria-describedby={describedBy}
              className={inputClass}
              maxLength={60}
              placeholder={`예: ${places[0] ?? '역'} 앞 치킨집`}
              value={v.place}
              onChange={(e) => set('place', e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              {places.map((p) => (
                <Chip key={p} active={v.place === p} onClick={() => set('place', p)}>
                  {p}
                </Chip>
              ))}
            </div>
          </div>
        )}
      </Field>

      {!editing && (
        <fieldset className="flex flex-col gap-3">
          <legend className="mb-3 text-[15px] font-semibold text-ink">몇 명이 모이면 좋을까요 (나 포함)</legend>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1">
              <Stepper
                label="최소"
                value={v.minPeople}
                min={2}
                max={20}
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
                min={v.noMinimum ? 2 : Math.max(2, v.minPeople)}
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
            <LineGauge count={1} min={minPeople} max={maxPeople} status={minPeople === null ? 'CONFIRMED' : 'RECRUITING'} />
            <p className="text-sm text-ink-2">
              <b className="text-ink">{peopleRule(minPeople, maxPeople)}</b>
            </p>
            {v.noMinimum && (
              <p className="text-sm leading-relaxed text-ink-3">
                열자마자 ‘확정’으로 보여요. 참여한 사람끼리는 바로 이름과 오픈채팅이 보여요.
              </p>
            )}
          </div>
        </fieldset>
      )}

      {!editing && member && (
        <fieldset>
          <legend className="mb-2 text-[15px] font-semibold text-ink">누구와 함께할까요</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {(
              [
                { value: 'SCHOOL', Icon: School, label: '우리 학교', sub: `${member.school.shortName} 원우 누구나` },
                {
                  value: 'COHORT',
                  Icon: Lock,
                  label: '우리 기수만',
                  sub: `${member.school.shortName} ${member.cohortLabel}만 보여요`,
                },
                { value: 'ALL', Icon: Globe2, label: '연합', sub: '다섯 학교 누구나 · 연합 게시판에' },
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
          <p className="mt-2 px-1 text-[13px] text-ink-3">열고 나면 바꿀 수 없어요.</p>
        </fieldset>
      )}

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

      <Field
        label="카카오톡 오픈채팅 링크"
        optional
        hint="성사되면 참여자에게만 보여요. 카카오톡 › 채팅 › 오픈채팅 › 만들기 후 링크를 복사해 붙여 넣어 주세요. 나중에 넣어도 돼요."
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

      <FormError message={error} />

      <div className="flex gap-2.5">
        <Button variant="secondary" size="lg" onClick={() => navigate(-1)} className="flex-1 sm:flex-none">
          돌아가기
        </Button>
        <Button type="submit" size="lg" loading={pending} className="flex-[2] sm:flex-none sm:px-10">
          {editing ? '수정 완료' : '3교시 열기'}
        </Button>
      </div>
    </form>
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
    deadline: first.getTime() - 30 * 60_000 > now.getTime() ? '30' : '0',
    place: '',
    minPeople: 3,
    noMinimum: false,
    maxPeople: 6,
    unlimited: false,
    description: '',
    openChatUrl: '',
    audience,
  }
}

function fromDetail(d: MeetupDetail): FormValues & { id: number } {
  return {
    id: d.id,
    title: d.title,
    categories: d.categories,
    purposes: d.purposes,
    meetAt: toKstInputValue(new Date(d.meetAt)),
    deadline: '0',
    place: d.place,
    minPeople: d.minPeople ?? 2,
    noMinimum: d.minPeople === null,
    maxPeople: d.maxPeople ?? Math.max(6, d.minPeople ?? 2),
    unlimited: d.maxPeople === null,
    description: d.description,
    openChatUrl: d.openChatUrl ?? '',
    audience: d.audience,
  }
}

function Header({ title, sub }: { title: string; sub: string }) {
  const navigate = useNavigate()
  return (
    <div className="mb-8">
      <button
        type="button"
        onClick={() => navigate(-1)}
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
  const [initial] = useState(() => defaults(params.get('audience') === 'ALL' ? 'ALL' : 'SCHOOL'))
  return (
    <div className="max-w-2xl">
      <Header title="3교시 열기" sub="이름은 가려진 채로 열려요. 인원이 안 모이면 조용히 사라지니 부담 갖지 마세요." />
      <MeetupForm initial={initial} editing={false} />
    </div>
  )
}

export function MeetupEditPage() {
  const id = Number(useParams().id)
  const { data, isPending, isError, error, refetch } = useMeetup(id)
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  if (!data.canEdit) return <ErrorState message="이 3교시는 수정할 수 없어요" />
  return (
    <div className="max-w-2xl">
      <Header title="3교시 수정" sub="시간과 인원은 바꿀 수 없어요. 바꿔야 하면 취소하고 새로 열어 주세요." />
      <MeetupForm initial={fromDetail(data)} editing />
    </div>
  )
}
