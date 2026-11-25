import { useState } from 'react'
import { ChevronRight, EyeOff } from 'lucide-react'
import { useFeedbackStats } from '../api/queries'
import type { FeedbackStats, FeedbackStatsGroup } from '../api/types'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, ErrorState, Segmented, Spinner } from '../components/ui'
import { cardClass } from '../components/styles'
import { percent, ratingShares, type RatingShare } from '../lib/feedback'

type Period = '30' | 'all'

const show = (n: number | null) => (n === null ? '—' : `${n}%`)

/** 만족도 막대: 조각 사이 2px 틈, 양 끝만 둥글게. 숫자는 범례·표에 글자로 같이 있다 */
function ShareBar({ shares, className, label }: { shares: RatingShare[]; className: string; label: string }) {
  return (
    <span role="img" aria-label={label} className={`flex gap-0.5 ${className}`}>
      {shares.map((s) => (
        <span
          key={s.value}
          title={`${s.label} ${s.count}명 (${s.percent}%)`}
          className="first:rounded-l-[4px] last:rounded-r-[4px]"
          style={{ width: `${s.percent}%`, background: s.color }}
        />
      ))}
    </span>
  )
}

function shareLabel(shares: RatingShare[]) {
  return shares.map((s) => `${s.label} ${s.percent}%`).join(', ')
}

function Kpis({ data, period }: { data: FeedbackStats; period: Period }) {
  const t = data.total
  const items = [
    {
      label: '열린 3교시',
      value: t.meetups.toLocaleString('ko-KR'),
      sub: period === '30' ? '최근 30일 · 모임 시간 기준' : '모임 시간이 지난 것 전체',
    },
    { label: '성사율', value: show(percent(t.reached, t.meetups)), sub: `${t.meetups}개 중 ${t.reached}개 성사` },
    {
      label: '실제로 모임',
      value: t.enough ? show(percent(t.metMeetups, t.metKnown)) : '응답 적음',
      sub: t.enough
        ? `후기 있는 성사 ${t.metKnown}개 중 ${t.metMeetups}개${data.partial ? ' · 응답 적은 칸 빼고' : ''}`
        : `후기가 ${data.minAnswers}개 이상 모이면 보여요`,
    },
    {
      label: '후기 응답률',
      value: show(percent(t.answers, t.participants)),
      sub: `참여자 ${t.participants}명 중 ${t.answers}명`,
    },
  ]
  return (
    <section aria-label="한눈에" className={`${cardClass} grid grid-cols-2 lg:grid-cols-4 lg:divide-x lg:divide-line`}>
      {items.map((k) => (
        <div key={k.label} className="px-5 py-4">
          <p className="text-sm text-ink-3">{k.label}</p>
          <p className="tabular mt-0.5 text-2xl font-extrabold text-ink">{k.value}</p>
          <p className="mt-0.5 text-[13px] text-ink-3">{k.sub}</p>
        </div>
      ))}
    </section>
  )
}

function Satisfaction({ data }: { data: FeedbackStats }) {
  const shares = ratingShares(data.total)
  const rated = (data.total.good ?? 0) + (data.total.ok ?? 0) + (data.total.bad ?? 0)
  return (
    <section aria-labelledby="sat-title" className={`${cardClass} min-w-0 flex-[2_1_22rem] px-5 py-5`}>
      <h3 id="sat-title" className="text-base font-extrabold text-ink">
        만족도 · 다섯 학교와 연합
      </h3>
      {shares ? (
        <>
          <p className="mt-1 text-[13px] text-ink-3">
            ‘모였어요’라고 답한 {rated}명 기준{data.partial && ' · 응답 적은 칸은 빼고'}
          </p>
          <ShareBar shares={shares} className="mt-4 h-7" label={shareLabel(shares)} />
          <ul className="mt-3.5 flex flex-wrap gap-x-5 gap-y-2">
            {shares.map((s) => (
              <li key={s.value} className="flex items-center gap-2 text-sm text-ink-2">
                <span aria-hidden className="size-3 rounded-[3px]" style={{ background: s.color }} />
                {s.label}
                <b className="tabular text-ink">{s.percent}%</b>
                <span className="tabular text-ink-3">{s.count}명</span>
              </li>
            ))}
          </ul>
        </>
      ) : (
        <p className="mt-2 text-[15px] text-ink-2">
          {data.total.enough ? '아직 만족도 답이 없어요' : `후기가 ${data.minAnswers}개 이상 모이면 보여요`}
        </p>
      )}
    </section>
  )
}

function SchoolRow({ g }: { g: FeedbackStatsGroup }) {
  const shares = ratingShares(g)
  return (
    <tr className="border-t border-line">
      <th scope="row" className="px-5 py-3.5 text-left font-semibold">
        {g.federation ? (
          <span className="rounded bg-board px-1.5 py-0.5 text-xs font-bold text-on-board">연합</span>
        ) : (
          g.school && <SchoolBadge school={g.school} />
        )}
      </th>
      <td className="tabular px-3 py-3.5 text-right">{g.meetups}</td>
      <td className="tabular px-3 py-3.5 text-right">{show(percent(g.reached, g.meetups))}</td>
      <td className="tabular px-3 py-3.5 text-right">{g.enough ? show(percent(g.metMeetups, g.metKnown)) : '—'}</td>
      <td className="px-3 py-3.5 pl-6">
        {shares ? (
          <span className="flex items-center gap-3">
            <ShareBar shares={shares} className="h-3 w-40 shrink-0" label={shareLabel(shares)} />
            <span className="tabular whitespace-nowrap text-ink-2">
              좋았어요 <b className="text-ink">{shares.find((s) => s.value === 'GOOD')?.percent ?? 0}%</b>
            </span>
          </span>
        ) : g.answers > 0 && !g.enough ? (
          <span className="rounded-lg bg-surface-2 px-2 py-1 text-xs font-bold text-ink-2">응답 적음</span>
        ) : (
          <span className="text-ink-3">—</span>
        )}
      </td>
      <td className="tabular px-5 py-3.5 text-right">{g.answers}</td>
    </tr>
  )
}

/** 대학원장 전용: 3교시 후기 통계. 학교별 숫자만, 응답이 적은 칸은 감춘다 */
export function FeedbackStatsPanel({ onOpenReports }: { onOpenReports: () => void }) {
  const [period, setPeriod] = useState<Period>('30')
  const stats = useFeedbackStats(period === '30' ? 30 : null)

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="px-1 text-[17px] font-extrabold text-ink">3교시 통계</h2>
        <Segmented
          label="기간"
          value={period}
          onChange={setPeriod}
          options={[
            { value: '30', label: '최근 30일' },
            { value: 'all', label: '전체' },
          ]}
        />
      </div>
      <p className="-mt-1 flex items-start gap-2 px-1 text-sm leading-relaxed text-ink-2">
        <EyeOff className="mt-0.5 size-4 shrink-0 text-ink-3" aria-hidden />
        후기는 학교별 숫자로만 보여요. 누가 어떻게 답했는지, 3교시 하나하나의 결과는 대학원장에게도 안 보여요.
      </p>

      {stats.isPending ? (
        <Spinner />
      ) : !stats.data ? (
        <ErrorState message={stats.error?.message ?? '불러오지 못했어요'} onRetry={() => stats.refetch()} />
      ) : (
        <>
          <Kpis data={stats.data} period={period} />

          <div className="flex flex-wrap gap-4">
            <Satisfaction data={stats.data} />
            <section aria-labelledby="fb-report-title" className={`${cardClass} flex min-w-0 flex-[1_1_15rem] flex-col px-5 py-5`}>
              <h3 id="fb-report-title" className="text-base font-extrabold text-ink">
                ‘아쉬웠어요’ 뒤 사람 신고
              </h3>
              <p className="tabular mt-2 text-3xl font-extrabold text-ink">{stats.data.feedbackReports}건</p>
              <p className="mt-1 text-[13px] leading-relaxed text-ink-3">
                후기에서 이어진 신고예요. 처리는 지금처럼 신고 탭에서 해요.
              </p>
              <Button variant="secondary" size="sm" className="mt-4 self-start" onClick={onOpenReports}>
                신고 탭에서 보기
                <ChevronRight className="size-4" aria-hidden />
              </Button>
            </section>
          </div>

          <section aria-labelledby="school-title" className={`${cardClass} overflow-hidden`}>
            <div className="px-5 pt-4 pb-2">
              <h3 id="school-title" className="text-base font-extrabold text-ink">
                학교별
              </h3>
              <p className="mt-1 text-[13px] text-ink-3">
                후기가 {stats.data.minAnswers}개보다 적은 칸은 숫자 대신 ‘응답 적음’으로 보여요
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] border-collapse text-sm text-ink">
                <thead>
                  <tr className="text-left text-[13px] text-ink-3">
                    <th scope="col" className="px-5 py-3 font-bold">학교</th>
                    <th scope="col" className="px-3 py-3 text-right font-bold">열린 3교시</th>
                    <th scope="col" className="px-3 py-3 text-right font-bold">성사율</th>
                    <th scope="col" className="px-3 py-3 text-right font-bold">실제로 모임</th>
                    <th scope="col" className="px-3 py-3 pl-6 font-bold">만족도</th>
                    <th scope="col" className="px-5 py-3 text-right font-bold">후기</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.data.groups.map((g) => (
                    <SchoolRow key={g.federation ? 'federation' : g.school?.id} g={g} />
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
