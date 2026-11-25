import type { FeedbackRating, FeedbackStatsGroup } from '../api/types'

/** 만족도 세 칸 (이 순서로 보여준다) */
export const RATINGS: { value: FeedbackRating; label: string; color: string }[] = [
  { value: 'GOOD', label: '좋았어요', color: 'var(--sat-good)' },
  { value: 'OK', label: '괜찮았어요', color: 'var(--sat-ok)' },
  { value: 'BAD', label: '아쉬웠어요', color: 'var(--sat-bad)' },
]

/** 반올림한 백분율. 알 수 없거나 분모가 0 이면 null */
export function percent(part: number | null, whole: number | null): number | null {
  if (part === null || whole === null || whole <= 0) return null
  return Math.round((part / whole) * 100)
}

export interface RatingShare {
  value: FeedbackRating
  label: string
  color: string
  count: number
  percent: number
}

/** 만족도 막대 조각 (0명인 칸은 뺀다). 숫자를 감춘 묶음이거나 만족도 답이 없으면 null */
export function ratingShares(g: Pick<FeedbackStatsGroup, 'enough' | 'good' | 'ok' | 'bad'>): RatingShare[] | null {
  if (!g.enough || g.good === null || g.ok === null || g.bad === null) return null
  const counts: Record<FeedbackRating, number> = { GOOD: g.good, OK: g.ok, BAD: g.bad }
  const total = g.good + g.ok + g.bad
  if (total === 0) return null
  return RATINGS.filter((r) => counts[r.value] > 0).map((r) => ({
    ...r,
    count: counts[r.value],
    percent: Math.round((counts[r.value] / total) * 100),
  }))
}

/** 후기 받는 기간이 얼마나 남았는지: "6일 남음" / "오늘까지" (끝났으면 빈 글자) */
export function feedbackDeadline(until: string, now: Date): string {
  const ms = new Date(until).getTime() - now.getTime()
  if (ms <= 0) return ''
  const days = Math.floor(ms / 86_400_000)
  return days === 0 ? '오늘까지' : `${days}일 남음`
}
