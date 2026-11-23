import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ChevronLeft, Pencil, Pin, PinOff, Plus, Trash2 } from 'lucide-react'
import { useDeleteNotice, useNotice, useNotices, usePinNotice, useSaveNotice } from '../api/queries'
import type { AuthorView } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AdminTag } from '../components/PersonName'
import { Button, EmptyState, ErrorState, Field, FormError, Spinner } from '../components/ui'
import { buttonClass, cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { affiliation } from '../lib/person'
import { formatRelative } from '../lib/time'

function Author({ author }: { author: AuthorView }) {
  if (author.official) {
    return (
      <span className="inline-flex items-center gap-1.5 text-ink-2">
        <span className="font-semibold text-ink">{author.name}</span>
        <AdminTag />
        {author.me && ' (나)'}
      </span>
    )
  }
  return (
    <span className="text-ink-2">
      {author.name} · {affiliation(author.cohortLabel, author.departmentLabel)}
      {author.me && ' (나)'}
    </span>
  )
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
  const now = useNow(60_000)

  return (
    <div>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">공지</h1>
          <p className="mt-1 text-[15px] text-ink-2">누구나 쓸 수 있어요. 모임 소식, 수요조사, 분실물 무엇이든.</p>
        </div>
        <Link to="/notices/new" className={buttonClass('primary', 'md')}>
          <Plus className="size-4" strokeWidth={2.6} aria-hidden />
          글쓰기
        </Link>
      </div>

      {isPending ? (
        <Spinner />
      ) : isError ? (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <div className={cardClass}>
          <EmptyState title="아직 공지가 없어요" body="첫 글을 남겨 보세요." />
        </div>
      ) : (
        <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
          {data.map((n) => (
            <li key={n.id} className="relative px-5 py-4 transition-colors focus-within:bg-surface-2 hover:bg-surface-2/70">
              <div className="flex items-start gap-2">
                {n.pinned && <Pin className="mt-1 size-4 shrink-0 fill-accent text-accent-ink" aria-label="상단 고정" />}
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
              <p className="mt-2 flex items-center gap-2 text-[13px] text-ink-3">
                <Author author={n.author} />
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
  const { member } = useAuth()
  const { data: n, isPending, isError, error, refetch } = useNotice(id)
  const del = useDeleteNotice()
  const pin = usePinNotice()
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow(60_000)

  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />

  const isAdmin = member?.role === 'ADMIN'

  return (
    <article className="max-w-2xl">
      <BackLink to="/notices" label="공지 목록" />
      <header className="border-b border-line pb-5">
        <h1 className="text-[26px] leading-tight font-extrabold tracking-[-0.03em] text-ink">
          {n.pinned && <Pin className="mr-2 inline size-5 fill-accent align-[-2px] text-accent-ink" aria-label="상단 고정" />}
          {n.title}
        </h1>
        <p className="mt-3 flex items-center gap-2 text-sm text-ink-3">
          <Author author={n.author} />
          <span>·</span>
          <time dateTime={n.createdAt}>{formatRelative(n.createdAt, now)}</time>
          {n.updatedAt !== n.createdAt && <span>· 수정됨</span>}
        </p>
      </header>
      <div className="py-6 text-base leading-[1.75] whitespace-pre-wrap text-ink">{n.content}</div>

      {(n.canEdit || isAdmin) && (
        <div className="flex flex-wrap gap-2 border-t border-line pt-5">
          {isAdmin && (
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
    </article>
  )
}

function NoticeForm({ id, initial }: { id?: number; initial: { title: string; content: string } }) {
  const save = useSaveNotice(id)
  const navigate = useNavigate()
  const toast = useToast()
  const [title, setTitle] = useState(initial.title)
  const [content, setContent] = useState(initial.content)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const d = await save.mutateAsync({ title, content })
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
      <FormError message={error} />
      <div className="flex gap-2.5">
        <Button variant="secondary" size="lg" onClick={() => navigate(-1)}>
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
  const { data, isPending, isError, error, refetch } = useNotice(id)
  if (isPending) return <Spinner />
  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />
  if (!data.canEdit) return <ErrorState message="글쓴이만 수정할 수 있어요" />
  return (
    <div className="max-w-2xl">
      <BackLink to={`/notices/${id}`} label="공지로" />
      <h1 className="mb-8 text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">공지 수정</h1>
      <NoticeForm id={id} initial={{ title: data.title, content: data.content }} />
    </div>
  )
}
