// 번개 종류/상태의 화면 표시 규칙.
// 종류 색은 서울 지하철 노선 색에서 가져왔다 (소주=2호선 초록, 맥주=3호선 주황 …).
import { Beer, Coffee, GlassWater, Martini, Milk, Sparkles, Wine, type LucideIcon } from 'lucide-react'
import type { MeetupCategory, MeetupStatus, MeetupSummary } from '../api/types'

export interface CategoryMeta {
  label: string
  /** 노선 배지 배경색 */
  color: string
  /** 배지 위 아이콘 색 */
  ink: string
  Icon: LucideIcon
}

export const CATEGORY: Record<MeetupCategory, CategoryMeta> = {
  SOJU: { label: '소주', color: '#00a84d', ink: '#ffffff', Icon: GlassWater },
  BEER: { label: '맥주', color: '#ef7c1c', ink: '#1a1206', Icon: Beer },
  WINE: { label: '와인', color: '#d6176a', ink: '#ffffff', Icon: Wine },
  MAKGEOLLI: { label: '막걸리', color: '#bdb092', ink: '#1d1a12', Icon: Milk },
  HIGHBALL: { label: '하이볼·칵테일', color: '#00a5de', ink: '#06202b', Icon: Martini },
  ANYTHING: { label: '아무거나', color: '#996cac', ink: '#ffffff', Icon: Sparkles },
  NO_ALCOHOL: { label: '술 없이', color: '#747f00', ink: '#ffffff', Icon: Coffee },
}

export const CATEGORY_ORDER: MeetupCategory[] = [
  'SOJU',
  'BEER',
  'HIGHBALL',
  'WINE',
  'MAKGEOLLI',
  'ANYTHING',
  'NO_ALCOHOL',
]

export type Tone = 'accent' | 'ok' | 'muted' | 'danger'

export function statusView(m: Pick<MeetupSummary, 'status' | 'joinable'>): { label: string; tone: Tone } {
  switch (m.status) {
    case 'RECRUITING':
      return { label: '모집중', tone: 'accent' }
    case 'CONFIRMED':
      return { label: m.joinable ? '성사' : '성사 · 마감', tone: 'ok' }
    case 'FAILED':
      return { label: '무산', tone: 'muted' }
    case 'DONE':
      return { label: '완료', tone: 'muted' }
    case 'CANCELED':
      return { label: '취소됨', tone: 'danger' }
  }
}

/** 인원 게이지 옆 한 줄 설명 */
export function seatsCaption(m: Pick<MeetupSummary, 'status' | 'participantCount' | 'minPeople' | 'maxPeople'>) {
  const { status, participantCount: count, minPeople: min, maxPeople: max } = m
  if (status === 'CANCELED') return '취소된 번개'
  if (status === 'FAILED') return '인원이 모이지 않았어요'
  if (status === 'DONE') return `${count}명이 함께했어요`
  if (count < min) return `성사까지 ${min - count}명`
  if (count >= max) return '정원이 다 찼어요'
  return `성사! ${max - count}자리 남음`
}

export function isLive(status: MeetupStatus) {
  return status === 'RECRUITING' || status === 'CONFIRMED'
}
