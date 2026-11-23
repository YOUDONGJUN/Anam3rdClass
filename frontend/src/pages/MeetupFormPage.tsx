import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ChevronLeft, Minus, Plus } from 'lucide-react'
import { useCreateMeetup, useMeetup, useUpdateMeetup } from '../api/queries'
import type { MeetupCategory, MeetupDetail } from '../api/types'
import { LineGauge } from '../components/LineGauge'
import { Button, ErrorState, Field, FormError, Spinner } from '../components/ui'
import { cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { CATEGORY, CATEGORY_ORDER } from '../lib/meetup'
import { ceilTo10Minutes, formatDateTime, fromKstInputValue, kstAt, toKstInputValue } from '../lib/time'

const PLACES = ['안암역 2번 출구', '참살이길', '고대 정문 앞', '개운사길', '제기동역']

const DEADLINES = [
  { value: '30', label: '30분 전', minutes: 30 },
  { value: '60', label: '1시간 전', minutes: 60 },
  { value: '180', label: '3시간 전', minutes: 180 },
  { value: '0', label: '모일 때까지', minutes: 0 },
] as const

type DeadlineValue = (typeof DEADLINES)[number]['value']

interface FormValues {
  title: string
  category: MeetupCategory
  meetAt: string
  deadline: DeadlineValue
  place: string
  minPeople: number
  maxPeople: number
  description: string
  openChatUrl: string
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
}: {
  label: string
  value: number
  min: number
  max: number
  onChange: (v: number) => void
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-line-strong bg-surface px-2 py-1.5">
      <span className="pl-2 text-[15px] font-semibold text-ink">{label}</span>
      <div className="flex items-center gap-1">
        <button
          type="button"
          aria-label={`${label} 줄이기`}
          className="flex size-10 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 disabled:opacity-30"
          onClick={() => onChange(value - 1)}
          disabled={value <= min}
        >
          <Minus className="size-4" aria-hidden />
        </button>
        <output className="tabular w-10 text-center text-lg font-extrabold text-ink" aria-live="polite">
          {value}
        </output>
        <button
          type="button"
          aria-label={`${label} 늘리기`}
          className="flex size-10 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2 disabled:opacity-30"
          onClick={() => onChange(value + 1)}
          disabled={value >= max}
        >
          <Plus className="size-4" aria-hidden />
        </button>
      </div>
    </div>
  )
}

function Chip({ active, onClick, children, disabled }: { active: boolean; onClick: () => void; children: ReactNode; disabled?: boolean }) {
  return (
    <button
      type="button"
      aria-pressed={active}
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

function MeetupForm({ initial, editing }: { initial: FormValues & { id?: number }; editing: boolean }) {
  const navigate = useNavigate()
  const toast = useToast()
  const create = useCreateMeetup()
  const update = useUpdateMeetup(initial.id ?? 0)
  const [v, setV] = useState<FormValues>(initial)
  const [error, setError] = useState<string | null>(null)
  const now = useMemo(() => new Date(), [])
  const presets = useMemo(() => timePresets(now), [now])

  const set = <K extends keyof FormValues>(key: K, value: FormValues[K]) => setV((prev) => ({ ...prev, [key]: value }))

  const meetAt = fromKstInputValue(v.meetAt)
  const deadlineMinutes = DEADLINES.find((d) => d.value === v.deadline)!.minutes
  const deadlineAt = meetAt ? new Date(meetAt.getTime() - deadlineMinutes * 60_000) : null
  const pending = create.isPending || update.isPending

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!v.title.trim()) return setError('제목을 입력해 주세요')
    if (!v.place.trim()) return setError('장소를 입력해 주세요')

    try {
      if (editing && initial.id) {
        const d = await update.mutateAsync({
          title: v.title,
          description: v.description,
          place: v.place,
          category: v.category,
          openChatUrl: v.openChatUrl,
        })
        toast.show('수정했어요', 'ok')
        navigate(`/meetups/${d.id}`, { replace: true })
        return
      }
      const timeError = validateTimes(meetAt, deadlineAt)
      if (timeError || !meetAt || !deadlineAt) return setError(timeError ?? '모임 시간을 정해 주세요')
      const d = await create.mutateAsync({
        title: v.title,
        description: v.description,
        place: v.place,
        category: v.category,
        meetAt: meetAt.toISOString(),
        deadlineAt: deadlineAt.toISOString(),
        minPeople: v.minPeople,
        maxPeople: v.maxPeople,
        openChatUrl: v.openChatUrl,
      })
      toast.show('번개를 열었어요! 단톡방에 공유해 보세요', 'ok')
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

      <fieldset>
        <legend className="mb-3 text-[15px] font-semibold text-ink">무엇을 마실까요</legend>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {CATEGORY_ORDER.map((c) => {
            const meta = CATEGORY[c]
            const active = v.category === c
            return (
              <label
                key={c}
                className={`flex min-h-12 cursor-pointer items-center gap-2.5 rounded-xl border px-3 py-2 text-[15px] font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
                  active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:border-ink-3'
                }`}
              >
                <input
                  type="radio"
                  name="category"
                  value={c}
                  checked={active}
                  onChange={() => set('category', c)}
                  className="sr-only"
                />
                <span
                  className="flex size-7 shrink-0 items-center justify-center rounded-full"
                  style={{ backgroundColor: meta.color, color: meta.ink }}
                >
                  <meta.Icon className="size-4" strokeWidth={2.4} aria-hidden />
                </span>
                {meta.label}
              </label>
            )
          })}
        </div>
      </fieldset>

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
              {formatDateTime(meetAt)} 모임 · <b className="font-semibold text-ink-2">{formatDateTime(deadlineAt)}</b>까지 최소
              인원이 안 모이면 조용히 사라져요
            </p>
          )}
        </fieldset>
      )}

      <Field label="어디서">
        {({ id }) => (
          <div className="flex flex-col gap-3">
            <input
              id={id}
              className={inputClass}
              maxLength={60}
              placeholder="예: 안암역 2번 출구 앞 치킨집"
              value={v.place}
              onChange={(e) => set('place', e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              {PLACES.map((p) => (
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
          <div className="grid gap-2.5 sm:grid-cols-2">
            <Stepper
              label="최소"
              value={v.minPeople}
              min={2}
              max={20}
              onChange={(n) => setV((p) => ({ ...p, minPeople: n, maxPeople: Math.max(p.maxPeople, n) }))}
            />
            <Stepper
              label="최대"
              value={v.maxPeople}
              min={Math.max(2, v.minPeople)}
              max={30}
              onChange={(n) => set('maxPeople', n)}
            />
          </div>
          <div className={`${cardClass} flex flex-col gap-2.5 px-4 py-4`}>
            <LineGauge count={1} min={v.minPeople} max={v.maxPeople} status="RECRUITING" />
            <p className="text-sm text-ink-2">
              나 포함 <b className="text-ink">{v.minPeople}명</b>이 모이면 성사, 최대 {v.maxPeople}명까지 받아요.
            </p>
          </div>
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
          {editing ? '수정 완료' : '번개 열기'}
        </Button>
      </div>
    </form>
  )
}

function defaults(): FormValues {
  const now = new Date()
  const first = timePresets(now)[0]?.date ?? ceilTo10Minutes(new Date(now.getTime() + 2 * 60 * 60_000))
  return {
    title: '',
    category: 'BEER',
    meetAt: toKstInputValue(first),
    deadline: first.getTime() - 30 * 60_000 > now.getTime() ? '30' : '0',
    place: '',
    minPeople: 3,
    maxPeople: 6,
    description: '',
    openChatUrl: '',
  }
}

function fromDetail(d: MeetupDetail): FormValues & { id: number } {
  return {
    id: d.id,
    title: d.title,
    category: d.category,
    meetAt: toKstInputValue(new Date(d.meetAt)),
    deadline: '0',
    place: d.place,
    minPeople: d.minPeople,
    maxPeople: d.maxPeople,
    description: d.description,
    openChatUrl: d.openChatUrl ?? '',
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
  const [initial] = useState(defaults)
  return (
    <div className="max-w-2xl">
      <Header title="번개 열기" sub="이름은 가려진 채로 열려요. 인원이 안 모이면 조용히 사라지니 부담 갖지 마세요." />
      <MeetupForm initial={initial} editing={false} />
    </div>
  )
}

export function MeetupEditPage() {
  const id = Number(useParams().id)
  const { data, isPending, isError, error, refetch } = useMeetup(id)
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  if (!data.canEdit) return <ErrorState message="이 번개는 수정할 수 없어요" />
  return (
    <div className="max-w-2xl">
      <Header title="번개 수정" sub="시간과 인원은 바꿀 수 없어요. 바꿔야 하면 취소하고 새로 열어 주세요." />
      <MeetupForm initial={fromDetail(data)} editing />
    </div>
  )
}
