// 랭킹 화면 표시 규칙. 세는 기준은 서버 RankingService.
import { Crown, Globe2, Target, Ticket, type LucideIcon } from 'lucide-react'
import type { RankingCategory, RankingEntry, RankingSchoolRow } from '../api/types'

export interface RankingCategoryMeta {
  /** 방장왕 */
  title: string
  /** 한 줄 설명 */
  hint: string
  Icon: LucideIcon
}

export const RANKING_CATEGORY: Record<RankingCategory, RankingCategoryMeta> = {
  HOST: { title: '방장왕', hint: '성사된 3교시를 가장 많이 연 원우', Icon: Crown },
  ATTEND: { title: '출석왕', hint: '성사된 3교시에 가장 많이 탄 원우 (연 것 포함)', Icon: Ticket },
  RATE: { title: '성사율', hint: '연 3교시 중 성사된 비율 · 3번 이상 연 원우만', Icon: Target },
  FEDERATION: { title: '연합왕', hint: '다른 학교 원우와 가장 많이 함께한 원우', Icon: Globe2 },
}

/** "3번" · 성사율은 "67% · 2/3" */
export function rankingValue(category: RankingCategory, e: Pick<RankingEntry, 'value' | 'of'>): string {
  if (category === 'RATE' && e.of) return `${Math.round((e.value / e.of) * 100)}% · ${e.value}/${e.of}`
  return `${e.value}번`
}

/** 학교 대항 참여율 (0~100, 가입 원우가 없으면 0) */
export function schoolRate(row: Pick<RankingSchoolRow, 'members' | 'active'>): number {
  return row.members === 0 ? 0 : Math.round((row.active / row.members) * 100)
}

/** 이름 옆 배지 이름 (이번 달 1등) */
export function crownLabel(category: RankingCategory): string {
  return RANKING_CATEGORY[category].title
}
