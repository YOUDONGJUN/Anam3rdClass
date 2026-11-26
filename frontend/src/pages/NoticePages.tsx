import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ChevronLeft, Flag, Globe2, Pencil, Pin, PinOff, Plus, Trash2 } from 'lucide-react'
import { useDeleteNotice, useNotice, useNotices, usePinNotice, useSaveNotice, useSignupOptions } from '../api/queries'
import type { AuthorView, SchoolTag } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { RoleTag } from '../components/PersonName'
import { ReportDialog } from '../components/ReportDialog'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, EmptyState, ErrorState, Field, FormError, Spinner } from '../components/ui'
import { buttonClass, cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { affiliation, isStaff } from '../lib/person'
import { formatRelative } from '../lib/time'
import { LinkifiedText } from '../components/LinkifiedText'
import { confirmLeave, useLeaveGuard } from '../lib/leaveGuard'
import { ApiError } from '../api/client'

function Author({ author, showSchool }: { author: AuthorView; showSchool: boolean }) {
  if (author.official) {
    return (
      <span className="inline-flex items-center gap-1.5 text-ink-2">
        <span className="font-semibold text-ink">{author.name}</span>
        <RoleTag />
        {author.me && ' (나)'}
      </span>
    )
  }
  return (
    <span className="inline-flex min-w-0 flex-wrap items-center gap-1.5 text-ink-2">
      {author.name} · {affiliation(author.cohortLabel, author.departmentLabel)}
      {showSchool && <SchoolBadge school={author.school} />}
      {author.me && ' (나)'}
    </span>
  )
}

/** 다섯 학교 모두에게 보이는 연합 공지 표시 */
function FederatedTag() {
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-md bg-board px-1.5 py-0.5 text-xs font-bold text-on-board">
      <Globe2 className="size-3" strokeWidth={2.6} aria-hidden />
      연합
    </span>
  )
}

/** 내 학교가 아닌 학교 공지 (대학원장에게만 보인다) */
function OtherSchoolTag({ school }: { school: SchoolTag | null }) {
  const { member } = useAuth()
  if (!school || !member || school.id === member.school.id) return null
  return <SchoolBadge school={school} />
}

function BackLink({ to, label }: { to: string; label: string }) {
  return (
    <Link
      to={to}
      className="mb-4 -ml-2 inline-flex h-10 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
    >
      <ChevronLeft className="size-5" aria-hidden />
      {label}
    </Link>
  )
}

export function NoticesPage() {
  const { data, isPending, isError, error, refetch } = useNotices()
  const { member } = useAuth()
  const now = useNow(60_000)
  // 공지는 교수·대학원장만 쓴다
  const canWrite = isStaff(member?.role)

  return (
    <div>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">공지</h1>
          <p className="mt-1 text-[15px] text-ink-2">
            {member?.role === 'OWNER' ? '모든 학교 공지와 연합 공지' : '우리 학교 소식과 연합 공지'}
          </p>
        </div>
        {canWrite && (
          <Link to="/notices/new" className={buttonClass('primary', 'md')}>
            <Plus className="size-4" strokeWidth={2.6} aria-hidden />
            글쓰기
          </Link>
        )}
      </div>

      {isPending ? (
        <Spinner />
      ) : isError ? (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <div className={cardClass}>
          <EmptyState
            title="아직 공지가 없어요"
            body={canWrite ? '첫 공지를 올려 보세요.' : '교수·대학원장이 올린 공지가 여기 보여요.'}
          />
        </div>
      ) : (
        <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
          {data.map((n) => (
            <li key={n.id} className="relative px-5 py-4 transition-colors focus-within:bg-surface-2 hover:bg-surface-2/70">
              <div className="flex items-start gap-2">
                {n.pinned && <Pin className="mt-1 size-4 shrink-0 fill-accent text-accent-ink" aria-label="상단 고정" />}
                {n.federated && <FederatedTag />}
                <OtherSchoolTag school={n.school} />
                <h2 className="min-w-0 flex-1 text-base leading-snug font-bold text-ink">
                  <Link
                    to={`/notices/${n.id}`}
                    className="outline-none after:absolute after:inset-0 after:content-[''] focus-visible:underline"
                  >
                    {n.title}
                  </Link>
                </h2>
              </div>
              <p className="mt-1 line-clamp-1 text-sm text-ink-2">{n.preview}</p>
              <p className="mt-2 flex flex-wrap items-center gap-2 text-[13px] text-ink-3">
                <Author author={n.author} showSchool={n.federated} />
                <span>·</span>
                <time dateTime={n.createdAt}>{formatRelative(n.createdAt, now)}</time>
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function NoticeDetailPage() {
  const id = Number(useParams().id)
  const { data: n, isPending, isError, error, refetch } = useNotice(id)
  const del = useDeleteNotice()
  const pin = usePinNotice()
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow(60_000)
  const [reporting, setReporting] = useState(false)

  if (isPending) return <Spinner />
  if (isError)
    return (
      <ErrorState
        message={error.message}
        onRetry={() => refetch()}
        // 지워진 공지(알림·링크로 들어옴)는 다시 시도해도 소용없다 — 목록으로
        action={
          error instanceof ApiError && error.status === 404 ? (
            <Link to="/notices" className={buttonClass('secondary', 'sm')}>
              공지 목록 보기
            </Link>
          ) : undefined
        }
      />
    )

  return (
    <article className="max-w-2xl">
      <BackLink to="/notices" label="공지 목록" />
      <header className="border-b border-line pb-5">
        {(n.federated || n.school) && (
          <p className="mb-2 flex gap-1.5 empty:hidden">
            {n.federated && <FederatedTag />}
            <OtherSchoolTag school={n.school} />
          </p>
        )}
        <h1 className="text-[26px] leading-tight font-extrabold tracking-[-0.03em] text-balance text-ink">
          {n.pinned && <Pin className="mr-2 inline size-5 fill-accent align-[-2px] text-accent-ink" aria-label="상단 고정" />}
          {n.title}
        </h1>
        <p className="mt-3 flex flex-wrap items-center gap-2 text-sm text-ink-3">
          <Author author={n.author} showSchool={n.federated} />
          <span>·</span>
          <time dateTime={n.createdAt}>{formatRelative(n.createdAt, now)}</time>
          {n.updatedAt !== n.createdAt && <span>· 수정됨</span>}
        </p>
      </header>
      <div className="py-6 text-base leading-[1.75] break-words whitespace-pre-wrap text-ink">
        <LinkifiedText text={n.content} />
      </div>

      {(n.canEdit || n.canPin) && (
        <div className="flex flex-wrap gap-2 border-t border-line pt-5">
          {n.canPin && (
            <Button
              variant="secondary"
              size="sm"
              loading={pin.isPending}
              onClick={() =>
                pin.mutate(
                  { id, pinned: !n.pinned },
                  {
                    onSuccess: (d) => toast.show(d.pinned ? '맨 위에 고정했어요' : '고정을 풀었어요', 'ok'),
                    onError: (e) => toast.show(e.message, 'error'),
                  },
                )
              }
            >
              {n.pinned ? <PinOff className="size-4" aria-hidden /> : <Pin className="size-4" aria-hidden />}
              {n.pinned ? '고정 풀기' : '맨 위 고정'}
            </Button>
          )}
          {n.canEdit && (
            <>
              <Link to={`/notices/${id}/edit`} className={buttonClass('secondary', 'sm')}>
                <Pencil className="size-4" aria-hidden />
                수정
              </Link>
              <Button
                variant="danger"
                size="sm"
                loading={del.isPending}
                onClick={() => {
                  if (!window.confirm('이 글을 지울까요?')) return
                  del.mutate(id, {
                    onSuccess: () => {
                      toast.show('지웠어요')
                      navigate('/notices', { replace: true })
                    },
                    onError: (e) => toast.show(e.message, 'error'),
                  })
                }}
              >
                <Trash2 className="size-4" aria-hidden />
                삭제
              </Button>
            </>
          )}
        </div>
      )}

      {!n.author.me && (
        <button
          type="button"
          onClick={() => setReporting(true)}
          className="mt-6 inline-flex h-10 items-center gap-1.5 rounded-lg px-2 text-sm font-semibold text-ink-3 hover:bg-surface-2 hover:text-ink"
        >
          <Flag className="size-4" aria-hidden />이 글 신고하기
        </button>
      )}
      {reporting && (
        <ReportDialog target={{ type: 'NOTICE', id, label: `공지 ‘${n.title}’` }} onClose={() => setReporting(false)} />
      )}
    </article>
  )
}

/** 대학원장이 고르는 공지 대상: 연합(다섯 학교 모두) 또는 학교 하나 */
type NoticeTarget = 'ALL' | number

function TargetOption({ checked, onSelect, children }: { checked: boolean; onSelect: () => void; children: ReactNode }) {
  return (
    <label
      className={`inline-flex min-h-11 cursor-pointer items-center rounded-full border px-4 text-[15px] font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
        checked ? 'border-accent bg-accent text-on-accent' : 'border-line-strong text-ink-2 hover:bg-surface-2'
      }`}
    >
      <input type="radio" name="notice-target" className="sr-only" checked={checked} onChange={onSelect} />
      {children}
    </label>
  )
}

function NoticeForm({ id, initial }: { id?: number; initial: { title: string; content: string } }) {
  const save = useSaveNotice(id)
  const { member } = useAuth()
  const options = useSignupOptions()
  const navigate = useNavigate()
  const toast = useToast()
  const [title, setTitle] = useState(initial.title)
  const [content, setContent] = useState(initial.content)
  const [target, setTarget] = useState<NoticeTarget>(member?.school.id ?? 'ALL')
  const [error, setError] = useState<string | null>(null)
  // 쓰던 내용이 있으면 나가기 전에 묻는다 (3000자 공지가 뒤로 한 번에 사라지지 않게)
  const [saved, setSaved] = useState(false)
  useLeaveGuard(!saved && (title !== initial.title || content !== initial.content))
  // 보이는 곳은 새로 쓸 때만 정한다: 대학원장은 고르고, 교수는 우리 학교
  const choosable = !id && member?.role === 'OWNER'
  const schools = options.data?.schools ?? []

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const d = await save.mutateAsync({
        title,
        content,
        federated: choosable && target === 'ALL',
        schoolId: choosable && target !== 'ALL' ? target : null,
      })
      setSaved(true)
      toast.show(id ? '수정했어요' : '올렸어요', 'ok')
      navigate(`/notices/${d.id}`, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장하지 못했어요')
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-6" noValidate>
      <Field label="제목">
        {({ id: fid }) => (
          <input id={fid} className={inputClass} maxLength={60} value={title} onChange={(e) => setTitle(e.target.value)} />
        )}
      </Field>
      <Field label="내용" hint={`${content.length} / 3000`}>
        {({ id: fid, describedBy }) => (
          <textarea
            id={fid}
            aria-describedby={describedBy}
            rows={10}
            maxLength={3000}
            className={`${inputClass} h-auto py-3 leading-relaxed`}
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
        )}
      </Field>
      {choosable ? (
        <fieldset className="flex flex-col gap-2.5">
          <legend className="mb-2.5 text-[15px] font-semibold text-ink">누구에게 보일까요</legend>
          <div className="flex flex-wrap gap-2">
            <TargetOption checked={target === 'ALL'} onSelect={() => setTarget('ALL')}>
              <Globe2 className="mr-1.5 size-4" aria-hidden />
              연합 · 모든 학교
            </TargetOption>
            {schools.map((s) => (
              <TargetOption key={s.id} checked={target === s.id} onSelect={() => setTarget(s.id)}>
                {s.shortName}만
              </TargetOption>
            ))}
          </div>
          <p className="text-sm text-ink-3">
            {target === 'ALL' ? '모든 학교 원우에게 보이고 알림이 가요.' : '고른 학교 원우에게만 보이고 알림이 가요.'}
          </p>
        </fieldset>
      ) : (
        !id &&
        member && (
          <p className="rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2">
            {member.school.shortName} 원우에게만 보이고, 앱 알림이 가요.
          </p>
        )
      )}
      <FormError message={error} />
      <div className="flex gap-2.5">
        <Button
          variant="secondary"
          size="lg"
          onClick={() => {
            if (!confirmLeave()) return
            // 바로 들어온 화면(앞 화면 없음)이면 공지 목록으로
            if ((window.history.state?.idx ?? 0) > 0) navigate(-1)
            else navigate(id ? `/notices/${id}` : '/notices', { replace: true })
          }}
        >
          돌아가기
        </Button>
        <Button type="submit" size="lg" loading={save.isPending} disabled={!title.trim() || !content.trim()} className="flex-1 sm:flex-none sm:px-10">
          {id ? '수정 완료' : '올리기'}
        </Button>
      </div>
    </form>
  )
}

export function NoticeCreatePage() {
  const { member } = useAuth()
  if (!isStaff(member?.role)) return <ErrorState message="공지는 교수·대학원장만 쓸 수 있어요" />
  return (
    <div className="max-w-2xl">
      <BackLink to="/notices" label="공지 목록" />
      <h1 className="mb-8 text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">공지 쓰기</h1>
      <NoticeForm initial={{ title: '', content: '' }} />
    </div>
  )
}

export function NoticeEditPage() {
  const id = Number(useParams().id)
  const { data, isPending, error, refetch } = useNotice(id)
  if (isPending) return <Spinner />
  // 처음 불러오기가 실패했을 때만 (쓰는 중 다시 불러오기가 실패해도 폼은 그대로 둔다)
  if (!data) return <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
  if (!data.canEdit) return <ErrorState message="글쓴이만 수정할 수 있어요" />
  return (
    <div className="max-w-2xl">
      <BackLink to={`/notices/${id}`} label="공지로" />
      <h1 className="mb-8 text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">공지 수정</h1>
      <NoticeForm id={id} initial={{ title: data.title, content: data.content }} />
    </div>
  )
}
