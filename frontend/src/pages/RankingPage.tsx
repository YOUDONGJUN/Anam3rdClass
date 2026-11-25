import { Link, useSearchParams } from 'react-router'
import { EyeOff, Globe2, Info, School, Trophy } from 'lucide-react'
import { useRanking } from '../api/queries'
import type { RankingCategoryView, RankingEntry, RankingPeriod, RankingSchoolRow } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BoardHero, BoardSheet, HeroTabs } from '../components/BoardHero'
import { RoleTag } from '../components/PersonName'
import { SchoolBadge } from '../components/SchoolBadge'
import { ErrorState, Segmented, Spinner } from '../components/ui'
import { cardClass } from '../components/styles'
import { RANKING_CATEGORY, rankingValue, schoolRate } from '../lib/ranking'

type Tab = 'all' | 'school' | 'schools'

/** 등수 동그라미: 1등은 노란 안내판 색, 2·3등은 테두리 */
function RankBadge({ rank }: { rank: number | null }) {
  if (rank === null)
    return (
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-surface-2 text-ink-3" title="랭킹에 안 나오는 중">
        <EyeOff className="size-4" aria-hidden />
        <span className="sr-only">순위 없음</span>
      </span>
    )
  const tone =
    rank === 1
      ? 'bg-exit text-on-exit shadow-[inset_0_-2px_0_rgb(0_0_0/0.16)]'
      : rank <= 3
        ? 'border-2 border-line-strong bg-surface text-ink'
        : 'text-ink-3'
  return (
    <span className={`tabular flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-extrabold ${tone}`}>
      {rank}
      <span className="sr-only">등</span>
    </span>
  )
}

function EntryRow({ e, category, mineRow = false }: { e: RankingEntry; category: RankingCategoryView['category']; mineRow?: boolean }) {
  return (
    <li className={`flex items-center gap-3 px-4 py-2.5 ${e.me ? 'bg-accent-soft/60' : ''} ${mineRow ? 'border-t border-line' : ''}`}>
      <RankBadge rank={e.rank} />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="flex min-w-0 items-center gap-1.5 text-[15px] font-semibold text-ink">
          <span className="truncate">{e.name}</span>
          {e.owner && <RoleTag />}
          {e.masked && <EyeOff className="size-3.5 shrink-0 opacity-60" aria-label="이름 가려짐" />}
          {e.me && <span className="shrink-0 rounded bg-board px-1.5 py-0.5 text-[11px] font-bold text-on-board">나</span>}
        </span>
        <span className="flex min-w-0 items-center gap-1.5 text-[13px] text-ink-3">
          <SchoolBadge school={e.school} />
          {e.departmentLabel && <span className="truncate">{e.departmentLabel}</span>}
        </span>
      </span>
      <span className="tabular shrink-0 text-right text-[15px] font-extrabold text-ink">{rankingValue(category, e)}</span>
    </li>
  )
}

function CategoryCard({ view, hidden }: { view: RankingCategoryView; hidden: boolean }) {
  const meta = RANKING_CATEGORY[view.category]
  const mineInTop = view.top.some((e) => e.me)
  return (
    <section className={`${cardClass} overflow-hidden`} aria-labelledby={`rank-${view.category}`}>
      <header className="flex items-center gap-3 px-4 pt-4 pb-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-board text-accent shadow-[inset_0_-2px_0_rgb(0_0_0/0.25)]">
          <meta.Icon className="size-5" aria-hidden />
        </span>
        <span className="min-w-0">
          <h2 id={`rank-${view.category}`} className="text-[17px] font-extrabold text-ink">
            {meta.title}
          </h2>
          <p className="text-[13px] leading-snug text-ink-3">{meta.hint}</p>
        </span>
      </header>
      {view.top.length === 0 ? (
        <p className="border-t border-line px-4 py-6 text-center text-sm text-ink-3">아직 기록이 없어요. 첫 주인공이 되어 보세요</p>
      ) : (
        <ol className="flex flex-col border-t border-line">
          {view.top.map((e, i) => (
            <EntryRow key={`${i}-${e.name}`} e={e} category={view.category} />
          ))}
        </ol>
      )}
      {view.mine && !mineInTop && (
        <ul aria-label="내 기록">
          <EntryRow e={view.mine} category={view.category} mineRow />
        </ul>
      )}
      {hidden && view.mine && (
        <p className="px-4 pb-3 text-[13px] text-ink-3">랭킹에 안 나오는 중이라 내 기록은 나만 봐요</p>
      )}
    </section>
  )
}

function SchoolBoard({ rows }: { rows: RankingSchoolRow[] }) {
  const best = Math.max(1, ...rows.map(schoolRate))
  return (
    <section className={`${cardClass} overflow-hidden`} aria-labelledby="rank-schools">
      <header className="flex items-center gap-3 px-4 pt-4 pb-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-board text-accent shadow-[inset_0_-2px_0_rgb(0_0_0/0.25)]">
          <Trophy className="size-5" aria-hidden />
        </span>
        <span className="min-w-0">
          <h2 id="rank-schools" className="text-[17px] font-extrabold text-ink">
            학교 대항 참여율
          </h2>
          <p className="text-[13px] leading-snug text-ink-3">한 번이라도 3교시에 탄 원우 ÷ 가입 원우</p>
        </span>
      </header>
      <ol className="flex flex-col border-t border-line">
        {rows.map((r) => {
          const rate = schoolRate(r)
          return (
            <li key={r.school.id} className={`flex items-center gap-3 px-4 py-3 ${r.mine ? 'bg-accent-soft/60' : ''}`}>
              <RankBadge rank={r.rank} />
              <span className="flex min-w-0 flex-1 flex-col gap-1.5">
                <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                  <SchoolBadge school={r.school} />
                  {r.mine && <span className="rounded bg-board px-1.5 py-0.5 text-[11px] font-bold text-on-board">우리 학교</span>}
                  <span className="tabular text-[13px] text-ink-3">
                    {r.members === 0 ? '가입 원우 없음' : `${r.active}/${r.members}명 · 3교시 ${r.meetups}번`}
                  </span>
                </span>
                <span className="relative h-2.5 w-full rounded-full bg-surface-2" aria-hidden>
                  <span
                    className="absolute inset-y-0 left-0 rounded-full bg-accent"
                    style={{ width: `${(rate / best) * 100}%`, minWidth: rate > 0 ? 6 : 0 }}
                  />
                </span>
              </span>
              <span className="tabular w-12 shrink-0 text-right text-[15px] font-extrabold text-ink">{rate}%</span>
            </li>
          )
        })}
      </ol>
    </section>
  )
}

/** 랭킹: 연합 · 우리 학교 · 학교 대항, 이번 달 · 전체 */
export function RankingPage() {
  const { member } = useAuth()
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('tab') === 'school' ? 'school' : params.get('tab') === 'schools' ? 'schools' : 'all'
  const period: RankingPeriod = params.get('period') === 'all' ? 'all' : 'month'
  // 학교 대항은 사람 범위와 상관없이 다섯 학교를 같이 본다
  const { data, isPending, error, refetch } = useRanking(period, tab === 'school' ? 'school' : 'all')
  const schoolName = member?.school.shortName ?? '우리 학교'

  function go(next: { tab?: Tab; period?: RankingPeriod }) {
    const t = next.tab ?? tab
    const p = next.period ?? period
    const q: Record<string, string> = {}
    if (t !== 'all') q.tab = t
    if (p !== 'month') q.period = p
    setParams(q, { replace: true })
  }

  return (
    <div>
      <BoardHero
        eyebrow={period === 'month' ? '이번 달' : '전체 기간'}
        title="랭킹"
        subtitle="성사된 뒤 모임 시간이 지난 3교시만 세요."
      >
        <HeroTabs<Tab>
          label="랭킹 범위"
          value={tab}
          onChange={(t) => go({ tab: t })}
          tabs={[
            { value: 'all', label: '연합', Icon: Globe2 },
            { value: 'school', label: schoolName, Icon: School },
            { value: 'schools', label: '학교 대항', Icon: Trophy },
          ]}
        />
      </BoardHero>

      <BoardSheet>
        <div className="mb-4 flex items-center justify-between gap-4 px-1">
          <h2 className="text-[19px] font-extrabold tracking-[-0.02em] text-ink">
            {tab === 'schools' ? '학교 대항' : tab === 'school' ? `${schoolName} 원우끼리` : '다섯 학교 전체'}
          </h2>
          <Segmented<RankingPeriod>
            label="기간"
            value={period}
            onChange={(p) => go({ period: p })}
            options={[
              { value: 'month', label: '이번 달' },
              { value: 'all', label: '전체' },
            ]}
          />
        </div>

        {isPending ? (
          <Spinner />
        ) : !data ? (
          <ErrorState message={error?.message ?? '불러오지 못했어요'} onRetry={() => refetch()} />
        ) : tab === 'schools' ? (
          <SchoolBoard rows={data.schools} />
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {data.categories.map((c) => (
              <CategoryCard key={c.category} view={c} hidden={member?.rankingHidden ?? false} />
            ))}
          </div>
        )}

        <p className="mt-6 flex items-start gap-2 px-1 text-[13px] leading-relaxed text-ink-3">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            이름은 가려서 보여요 (내 이름만 그대로). 상위 10명과 내 기록만 보여요.{' '}
            <Link to="/me#ranking" className="font-semibold text-accent-ink underline underline-offset-4">
              랭킹에 안 나오게 하기
            </Link>
          </span>
        </p>
      </BoardSheet>
    </div>
  )
}
