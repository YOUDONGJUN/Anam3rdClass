import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useMemoryFeed } from '../api/queries'
import type { Memory } from '../api/types'
import { MemoryCard } from '../components/MemoryCard'
import { ReportDialog, type ReportTargetInfo } from '../components/ReportDialog'
import { Button, EmptyState, ErrorState, Spinner } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useNow } from '../lib/hooks'

/**
 * 추억: 함께한 3교시의 사진과, 볼 수 있는 3교시에서 공개한 사진을 최근 순으로.
 * 올리기는 모였던 3교시의 상세 화면에서 한다 (어느 3교시인지 분명하게)
 */
export function MemoriesPage() {
  const feed = useMemoryFeed()
  const now = useNow(60_000)
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)
  const more = useRef<HTMLDivElement>(null)
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = feed

  // 끝까지 내리면 더 불러온다
  useEffect(() => {
    const el = more.current
    if (!el || !hasNextPage) return
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting) && !isFetchingNextPage) void fetchNextPage()
    })
    io.observe(el)
    return () => io.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const items = feed.data?.pages.flatMap((p) => p.items) ?? []
  const report = (m: Memory) =>
    setReporting({ type: 'MEETUP', id: m.meetupId, label: `‘${m.meetupTitle}’의 추억 사진` })

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-[28px] leading-tight font-extrabold tracking-[-0.03em] text-ink">추억</h1>
        <p className="mt-1 text-[15px] text-ink-2">함께한 3교시의 사진이 모여요. 공개한 추억은 다른 원우도 봐요.</p>
      </div>

      {feed.isPending ? (
        <Spinner />
      ) : feed.isError ? (
        <ErrorState message={feed.error.message} onRetry={() => feed.refetch()} />
      ) : items.length === 0 ? (
        <div className={cardClass}>
          <EmptyState
            title="아직 추억이 없어요"
            body="3교시가 모인 뒤 상세 화면에서 인생네컷이나 단체 사진을 올려 보세요. 함께한 원우에게 알림이 가요."
            action={
              <Link to="/my-meetups" className={buttonClass('secondary')}>
                내 3교시 보기
              </Link>
            }
          />
        </div>
      ) : (
        <div className="flex flex-col gap-5">
          {items.map((m) => (
            <MemoryCard key={m.id} memory={m} now={now} onReport={report} />
          ))}
          <div ref={more} />
          {hasNextPage && (
            <Button variant="secondary" loading={isFetchingNextPage} onClick={() => fetchNextPage()} className="self-center">
              더 보기
            </Button>
          )}
        </div>
      )}

      {reporting && <ReportDialog key={reporting.id} target={reporting} onClose={() => setReporting(null)} />}
    </div>
  )
}
