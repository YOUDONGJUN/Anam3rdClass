// 3교시 종류/목적/상태의 화면 표시 규칙.
// 종류 색은 서울 지하철 노선 색에서 가져왔다 (소주=2호선 초록, 맥주=3호선 주황 …).
// 여러 종류를 고르면 환승역 표지판처럼 노선 배지가 나란히 붙는다.
import { Beer, Coffee, GlassWater, Martini, Milk, Sparkles, Wine, type LucideIcon } from 'lucide-react'
import type { MeetupAudience, MeetupCategory, MeetupStatus, MeetupSummary } from '../api/types'
import { formatDateTime } from './time'

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

/** 화면에 늘 같은 순서로 보이게 정렬 */
export function sortCategories(list: readonly MeetupCategory[]): MeetupCategory[] {
  return CATEGORY_ORDER.filter((c) => list.includes(c))
}

/** "소주 · 맥주" */
export function categoryLabel(list: readonly MeetupCategory[]) {
  return sortCategories(list)
    .map((c) => CATEGORY[c].label)
    .join(' · ')
}

/**
 * 종류 고르기/빼기. 여러 개를 고를 수 있지만 "아무거나"는 혼자만:
 * 아무거나를 고르면 나머지가 빠지고, 다른 걸 고르면 아무거나가 빠진다.
 */
export function toggleCategory(list: readonly MeetupCategory[], c: MeetupCategory): MeetupCategory[] {
  if (list.includes(c)) return list.filter((x) => x !== c)
  if (c === 'ANYTHING') return ['ANYTHING']
  return sortCategories([...list.filter((x) => x !== 'ANYTHING'), c])
}

/** 모임 목적 빠른 선택. 목록에 없으면 직접 적는다 */
export const PURPOSE_PRESETS = ['친목', '잡담', '학업', '팀플 뒤풀이', '진로·커리어', '고민 상담'] as const
export const MAX_PURPOSES = 3
export const PURPOSE_MAX_LENGTH = 12

/**
 * 직접 적은 목적을 태그로 나눈다. "친목, 잡담" 처럼 쉼표로 여러 개를 한 번에 적어도 된다.
 * 이미 고른 것과 겹치면 빼고, 최대 개수까지만 붙인다.
 */
export function addPurposes(current: readonly string[], typed: string): string[] {
  const next = [...current]
  for (const raw of typed.split(',')) {
    const tag = raw.trim().slice(0, PURPOSE_MAX_LENGTH)
    if (!tag || next.includes(tag)) continue
    if (next.length >= MAX_PURPOSES) break
    next.push(tag)
  }
  return next
}

/**
 * 내가 열었거나 참여한 3교시끼리는 모임 시간이 이만큼 떨어져야 한다 (서버 MeetupService.OVERLAP).
 * 같은 시간대에 두 곳을 잡아 한쪽 자리를 비우지 않게. 19시 1차 · 21시 2차는 된다
 */
export const OVERLAP_HOURS = 2

type MineForOverlap = Pick<MeetupSummary, 'id' | 'title' | 'meetAt' | 'status' | 'hosting'>

/** 모임 시간이 앞뒤 2시간 안인 내 3교시 (취소·무산 제외, exceptId 는 뺀다). 가장 이른 것 하나, 없으면 null */
export function findOverlap<T extends MineForOverlap>(
  mine: readonly T[],
  meetAt: Date,
  exceptId?: number | null,
): T | null {
  const limit = OVERLAP_HOURS * 3_600_000
  const near = mine
    .filter((m) => m.id !== exceptId && m.status !== 'CANCELED' && m.status !== 'FAILED')
    .filter((m) => Math.abs(new Date(m.meetAt).getTime() - meetAt.getTime()) < limit)
    .sort((a, b) => new Date(a.meetAt).getTime() - new Date(b.meetAt).getTime())
  return near[0] ?? null
}

/** "앞뒤 2시간 안에 이미 참여한 3교시가 있어요: '치맥 번개' 오늘 21:00" */
export function overlapMessage(m: MineForOverlap, now: Date = new Date()): string {
  return `앞뒤 ${OVERLAP_HOURS}시간 안에 이미 ${m.hosting ? '연' : '참여한'} 3교시가 있어요: '${m.title}' ${formatDateTime(m.meetAt, now)}`
}

export type Tone = 'accent' | 'ok' | 'muted' | 'danger'

export function statusView(
  m: Pick<MeetupSummary, 'status' | 'joinable' | 'minPeople' | 'maxPeople' | 'participantCount'>,
): { label: string; tone: Tone } {
  switch (m.status) {
    case 'RECRUITING':
      return { label: '모집중', tone: 'accent' }
    case 'CONFIRMED': {
      // 최소 인원이 없는 3교시는 열자마자 "확정". 못 들어가는 까닭을 붙인다 — 정원이 찼으면 만석, 마감이 지났으면 마감
      const word = m.minPeople === null ? '확정' : '성사'
      if (m.joinable) return { label: word, tone: 'ok' }
      const full = m.maxPeople !== null && m.participantCount >= m.maxPeople
      return { label: `${word} · ${full ? '만석' : '마감'}`, tone: 'ok' }
    }
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
  if (status === 'CANCELED') return '취소된 3교시'
  if (status === 'FAILED') return '인원이 모이지 않았어요'
  if (status === 'DONE') return count > 1 ? `${count}명이 함께했어요` : '함께한 사람이 없었어요'
  if (min !== null && count < min) return `성사까지 ${min - count}명`
  if (max !== null && count >= max) return '정원이 다 찼어요'
  if (min === null) return max === null ? '무조건 진행 · 누구나 와요' : `무조건 진행 · ${max - count}자리 남음`
  return max === null ? '성사! 인원 제한 없어요' : `성사! ${max - count}자리 남음`
}

/** 인원 규칙 한 줄: "3명이 모이면 성사 · 최대 6명" */
export function peopleRule(min: number | null, max: number | null) {
  const head = min === null ? '최소 인원 없이 무조건 진행' : `나 포함 ${min}명이 모이면 성사`
  const tail = max === null ? '인원 제한 없음' : `최대 ${max}명`
  return `${head} · ${tail}`
}

export interface PeopleValues {
  minPeople: number
  /** 최소 인원 없음 (minPeople 무시) */
  noMinimum: boolean
  maxPeople: number
  /** 인원 제한 없음 (maxPeople 무시) */
  unlimited: boolean
}

/**
 * 인원 입력을 바꿀 때마다 최대 인원이 최소 인원보다 작아지지 않게 맞춘다.
 * 예: "최소 인원 없음"을 켠 채 최대를 2명으로 줄였다가 다시 끄면 최소(3명)에 맞춰 최대를 올린다.
 */
export function fitPeople<T extends PeopleValues>(v: T): T {
  if (v.noMinimum || v.unlimited || v.maxPeople >= v.minPeople) return v
  return { ...v, maxPeople: v.minPeople }
}

/** 수정할 때 이미 모인 상태 */
export interface JoinedState {
  /** 지금 참여 인원 (개설자 포함) */
  joined: number
  /** 이미 성사됐는지 */
  reached: boolean
}

/** 성사 여부: 최소 인원이 없거나 최소 인원만큼 모였다 (서버 Meetup.isReached 와 같다) */
export function isReachedWith(min: number | null, joined: number) {
  return min === null || joined >= min
}

/**
 * 제출 직전 인원 검사 (서버도 한 번 더 검사한다). 문제가 없으면 null.
 * 수정할 때(current)는 이미 참여한 사람보다 최대를 줄일 수 없고, 성사된 뒤에는 최소를 지금 인원보다 올릴 수 없다.
 */
export function peopleError(min: number | null, max: number | null, current?: JoinedState): string | null {
  if (min !== null && max !== null && max < min) return '최대 인원은 최소 인원보다 같거나 많아야 해요'
  if (current) {
    const { joined, reached } = current
    if (max !== null && max < joined) return `이미 ${joined}명이 참여했어요. 최대 인원을 ${joined}명보다 적게 줄일 수 없어요`
    if (reached && min !== null && min > joined) return `이미 성사된 3교시예요. 최소 인원을 지금 인원(${joined}명)보다 늘릴 수 없어요`
  }
  return null
}

/** "3/6명" 또는 제한 없음이면 "3명" */
export function headcount(m: Pick<MeetupSummary, 'participantCount' | 'maxPeople'>) {
  return m.maxPeople === null ? `${m.participantCount}명` : `${m.participantCount}/${m.maxPeople}명`
}

export function isLive(status: MeetupStatus) {
  return status === 'RECRUITING' || status === 'CONFIRMED'
}

/** 중간 합류 OK 는 아직 모이는 중일 때만 의미가 있다 (지난·무산·취소된 3교시에는 붙이지 않는다) */
export function showsLateJoin(m: Pick<MeetupSummary, 'lateJoin' | 'status'>) {
  return m.lateJoin && isLive(m.status)
}

/** 내가 참여한 3교시 표시: 모이는 중이면 "참여 중", 모였으면 "참여함", 무산·취소면 붙이지 않는다 */
export function joinedLabel(status: MeetupStatus): string | null {
  if (isLive(status)) return '참여 중'
  if (status === 'DONE') return '참여함'
  return null
}

/** 이름이 가려져 있을 때 탑승 인원 위 안내 (이미 성사·완료된 3교시에 "성사되면"이라 하지 않는다) */
export function hiddenNamesNote(status: MeetupStatus): string {
  switch (status) {
    case 'RECRUITING':
      return '성사되면 함께하는 사람끼리만 실명이 보여요.'
    case 'CONFIRMED':
      return '참여한 사람끼리만 실명이 보여요.'
    case 'DONE':
      return '함께한 사람끼리만 실명이 보여요.'
    case 'FAILED':
    case 'CANCELED':
      return '성사되지 않은 3교시라 이름은 계속 가려져요.'
  }
}

/** 참여비: 10000 → "1만 원", 15000 → "15,000원" */
export function formatFee(fee: number): string {
  return fee % 10000 === 0 ? `${fee / 10000}만 원` : `${fee.toLocaleString('ko-KR')}원`
}

/** "참여비 1만 원 · 선입금" */
export function feeLabel(fee: number, prepaid: boolean): string {
  return `참여비 ${formatFee(fee)}${prepaid ? ' · 선입금' : ''}`
}

/** 최대 인원 상한 (서버 MeetupService.MAX_SEATS) */
export const MAX_SEATS = 30

/** 정원 늘리기: 기다리는 사람 수만큼 (적어도 1명, 최대 30명까지). 더 늘릴 수 없으면 0 */
export function seatsToAdd(max: number, waitCount: number): number {
  return Math.max(0, Math.min(MAX_SEATS - max, Math.max(1, waitCount)))
}

export const AUDIENCE_LABEL: Record<MeetupAudience, string> = {
  SCHOOL: '우리 학교',
  COHORT: '우리 기수만',
  ALL: '연합',
}

/** 3교시 열기에서 접어 둔 옵션 한 줄: "3~6명 · 우리 학교 · 참여비 없음" */
export function optionsSummary(o: {
  minPeople: number | null
  maxPeople: number | null
  audience: MeetupAudience
  lateJoin: boolean
  fee: number | null
}): string {
  const { minPeople: min, maxPeople: max } = o
  const people =
    min === null ? (max === null ? '인원 무관' : `최대 ${max}명`) : max === null ? `${min}명부터` : min === max ? `${min}명` : `${min}~${max}명`
  const parts = [people, AUDIENCE_LABEL[o.audience]]
  if (o.lateJoin) parts.push('중간 합류 OK')
  parts.push(o.fee === null ? '참여비 없음' : `참여비 ${o.fee.toLocaleString('ko-KR')}원`)
  return parts.join(' · ')
}
