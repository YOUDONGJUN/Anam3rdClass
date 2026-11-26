import { Link, useSearchParams } from 'react-router'
import { ChevronRight, Copy, ReceiptText } from 'lucide-react'
import { useMyMeetups, useMySettlements } from '../api/queries'
import type { MeetupSummary } from '../api/types'
import { BoardHero, BoardSheet, HeroTabs } from '../components/BoardHero'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { EmptyState, ErrorState } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useNow } from '../lib/hooks'
import { isLive } from '../lib/meetup'
import { formatWon } from '../lib/settlement'
import { formatDayLabel, formatTime } from '../lib/time'

type Tab = 'upcoming' | 'past'

const time = (m: MeetupSummary) => new Date(m.meetAt).getTime()

/** 노선 위의 역 하나 — 성사는 환승역(이중 원), 모집 중은 강조색 역, 지난 건 회색 */
function Stop({ m }: { m: MeetupSummary }) {
  if (m.status === 'CONFIRMED')
    return (
      <span
        aria-hidden
        className="relative mt-0.5 size-5 rounded-full border-4 border-ok bg-surface shadow-[inset_0_0_0_2px_var(--surface),inset_0_0_0_10px_var(--ok)]"
      />
    )
  if (m.status === 'RECRUITING')
    return <span aria-hidden className="relative mt-1 size-4 rounded-full bg-accent shadow-[inset_0_-2px_0_rgb(0_0_0/0.22)]" />
  return <span aria-hidden className="relative mt-1 size-3.5 rounded-full border-[3px] border-line-strong bg-surface" />
}

function stopTone(m: MeetupSummary) {
  if (m.status === 'CONFIRMED') return 'text-ok-ink'
  if (m.status === 'RECRUITING') return 'text-accent-ink'
  return 'text-ink-3'
}

/** 내가 보낼 정산: 누르면 그 3교시에서 송금하고 [보냈어요]를 누른다 */
function SettlementDues() {
  const { data } = useMySettlements()
  const now = useNow()
  if (!data || data.length === 0) return null
  return (
    <section className={`${cardClass} mb-6 overflow-hidden`} aria-labelledby="dues-title">
      <h2 id="dues-title" className="flex items-center gap-2 border-b border-line px-5 py-3.5 text-[15px] font-extrabold text-ink">
        <ReceiptText className="size-5 text-accent-ink" aria-hidden />
        보낼 정산
        <span className="tabular rounded-full bg-danger px-2 py-0.5 text-[12px] font-bold text-white">{data.length}</span>
      </h2>
      <ul className="divide-y divide-line">
        {data.map((d) => (
          <li key={d.meetupId}>
            <Link to={`/meetups/${d.meetupId}`} className="flex items-center gap-3 px-5 py-3.5 hover:bg-surface-2">
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[15px] font-bold text-ink">{d.title}</span>
                <span className="mt-0.5 block truncate text-[13px] text-ink-3">
                  <b className="font-semibold text-ink-2">{d.payer.name}</b> 님에게 · {formatDayLabel(d.meetAt, now)} 3교시
                </span>
              </span>
              <span className="tabular shrink-0 text-[15px] font-extrabold text-ink">{formatWon(d.amount)}</span>
              <ChevronRight className="size-4 shrink-0 text-ink-3" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** 내가 열고 참여한 3교시 — 내 일정이 한 줄 노선처럼 이어진다 */
export function MyMeetupsPage() {
  const { data, isPending, isError, error, refetch } = useMyMeetups()
  const now = useNow()
  // 고른 탭은 주소(?tab=past)에 남긴다 — 상세에 갔다가 뒤로 와도 보던 탭 그대로
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('tab') === 'past' ? 'past' : 'upcoming'
  const setTab = (next: Tab) => setParams(next === 'past' ? { tab: 'past' } : {}, { replace: true })

  const all = data ?? []
  // 무산됐어도 모임 시간 전이면 다가오는 쪽에 둔다 — 연 사람이 지금 인원으로 되살릴 수 있어서
  const ahead = (m: MeetupSummary) => isLive(m.status) || (m.status === 'FAILED' && time(m) > now.getTime())
  const upcoming = all.filter(ahead).sort((a, b) => time(a) - time(b))
  const past = all.filter((m) => !ahead(m)).sort((a, b) => time(b) - time(a))
  const list = tab === 'upcoming' ? upcoming : past
  const counted = (label: string, n: number) => (data ? `${label} ${n}` : label)

  return (
    <div>
      <BoardHero title="내 3교시" subtitle="내가 열고 참여한 3교시가 여기 모여요.">
        <HeroTabs<Tab>
          label="내 3교시 보기"
          value={tab}
          onChange={setTab}
          tabs={[
            { value: 'upcoming', label: counted('다가오는', upcoming.length) },
            { value: 'past', label: counted('지난', past.length) },
          ]}
        />
      </BoardHero>

      <BoardSheet>
        <SettlementDues />

        <h2 className="mb-3.5 flex items-baseline justify-between gap-3 px-1">
          <span className="text-[17px] font-extrabold text-ink">내 노선</span>
          <span className="text-[13px] text-ink-3">{tab === 'upcoming' ? '다가오는 순서대로' : '최근 순서대로'}</span>
        </h2>

        {isPending ? (
          <div className={`${cardClass} divide-y divide-line`}>
            <MeetupRowSkeleton />
            <MeetupRowSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error.message} onRetry={() => refetch()} />
        ) : list.length === 0 ? (
          <div className={cardClass}>
            {tab === 'upcoming' ? (
              <EmptyState
                title="다가오는 3교시가 없어요"
                body="내가 열고 참여한 3교시가 여기 모여요"
                action={
                  <Link to="/" className={buttonClass('secondary')}>
                    열린 3교시 보기
                  </Link>
                }
              />
            ) : (
              <EmptyState title="아직 지난 3교시가 없어요" body="함께한 3교시가 여기 쌓여요" />
            )}
          </div>
        ) : (
          <ol className="flex flex-col">
            {list.map((m, i) => {
              const last = i === list.length - 1
              const rail = tab === 'upcoming' ? 'bg-[color-mix(in_oklab,var(--accent)_50%,var(--bg))]' : 'bg-line-strong'
              return (
                <li key={m.id} className="relative flex gap-3 pb-5">
                  <div className="relative flex w-6 shrink-0 justify-center">
                    {!last ? (
                      <span aria-hidden className={`absolute top-4 -bottom-1 left-1/2 w-1 -translate-x-1/2 rounded-full ${rail}`} />
                    ) : (
                      tab === 'upcoming' && (
                        <span aria-hidden className="absolute top-4 -bottom-3 left-1/2 w-0 -translate-x-1/2 border-l-4 border-dotted border-line-strong" />
                      )
                    )}
                    <Stop m={m} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className={`tabular mt-0.5 mb-2 text-[13px] font-extrabold ${stopTone(m)}`}>
                      {formatDayLabel(m.meetAt, now)} · {formatTime(m.meetAt)}
                    </p>
                    <div className={`${cardClass} overflow-hidden`}>
                      <MeetupRow meetup={m} now={now} hideTime backLabel="내 3교시" />
                    </div>
                    {/* 지난 3교시: 내용 그대로 다시 열기 (시간만 새로) */}
                    {tab === 'past' && (
                      <Link
                        to={`/meetups/new?from=${m.id}`}
                        className="mt-1.5 inline-flex h-9 items-center gap-1.5 rounded-lg px-2 text-sm font-semibold text-accent-ink hover:bg-surface-2"
                      >
                        <Copy className="size-4" aria-hidden />
                        다시 열기
                      </Link>
                    )}
                  </div>
                </li>
              )
            })}
            {tab === 'upcoming' && (
              <li className="flex items-center gap-3">
                <span className="flex w-6 shrink-0 justify-center" aria-hidden>
                  <span className="size-3 rounded-full border-[3px] border-line-strong bg-bg" />
                </span>
                <Link to="/" className="inline-flex items-center gap-1 text-[15px] font-bold text-accent-ink">
                  열린 3교시 더 보기
                  <ChevronRight className="size-4" aria-hidden />
                </Link>
              </li>
            )}
          </ol>
        )}
      </BoardSheet>
    </div>
  )
}
