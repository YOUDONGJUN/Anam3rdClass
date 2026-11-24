import { describe, expect, it } from 'vitest'
import {
  ceilTo10Minutes,
  formatDateTime,
  formatDayLabel,
  formatLeadTime,
  formatRelative,
  formatRemaining,
  formatTime,
  fromKstInputValue,
  kstAt,
  kstDayKey,
  toKstInputValue,
} from './time'

// 2026-11-23 (월) 20:00 KST
const NOW = new Date('2026-11-23T11:00:00Z')

describe('KST 표시', () => {
  it('UTC 를 한국 시간으로 보여준다', () => {
    expect(formatTime('2026-11-23T13:00:00Z')).toBe('22:00')
    expect(kstDayKey('2026-11-23T15:30:00Z')).toBe('2026-11-24') // KST 자정 넘김
  })

  it('오늘/내일/모레/날짜', () => {
    expect(formatDayLabel('2026-11-23T13:00:00Z', NOW)).toBe('오늘')
    expect(formatDayLabel('2026-11-24T10:00:00Z', NOW)).toBe('내일')
    expect(formatDayLabel('2026-11-25T10:00:00Z', NOW)).toBe('모레')
    expect(formatDayLabel('2026-11-26T10:00:00Z', NOW)).toBe('11월 26일 (목)')
    expect(formatDateTime('2026-11-23T13:00:00Z', NOW)).toBe('오늘 22:00')
  })

  it('남은 시간', () => {
    expect(formatRemaining('2026-11-23T12:20:00Z', NOW)).toBe('1시간 20분')
    expect(formatRemaining('2026-11-23T11:35:00Z', NOW)).toBe('35분')
    expect(formatRemaining('2026-11-25T14:00:00Z', NOW)).toBe('2일 3시간')
    expect(formatRemaining('2026-11-23T10:00:00Z', NOW)).toBe('지남')
  })

  it('작성 시각', () => {
    expect(formatRelative('2026-11-23T10:58:30Z', NOW)).toBe('1분 전')
    expect(formatRelative('2026-11-23T08:00:00Z', NOW)).toBe('3시간 전')
    expect(formatRelative('2026-11-22T08:00:00Z', NOW)).toBe('어제')
    expect(formatRelative('2026-11-02T08:00:00Z', NOW)).toBe('11월 2일')
  })
})

describe('입력값 변환', () => {
  it('datetime-local 은 기기 시간대와 무관하게 KST 로 해석한다', () => {
    expect(fromKstInputValue('2026-11-23T22:00')?.toISOString()).toBe('2026-11-23T13:00:00.000Z')
    expect(toKstInputValue(new Date('2026-11-23T13:00:00Z'))).toBe('2026-11-23T22:00')
    expect(fromKstInputValue('잘못된 값')).toBeNull()
  })

  it('KST 기준 n일 뒤 특정 시각', () => {
    expect(kstAt(NOW, 0, 22).toISOString()).toBe('2026-11-23T13:00:00.000Z')
    expect(kstAt(NOW, 1, 19).toISOString()).toBe('2026-11-24T10:00:00.000Z')
  })

  it('10분 단위 올림', () => {
    expect(ceilTo10Minutes(new Date('2026-11-23T11:03:00Z')).toISOString()).toBe('2026-11-23T11:10:00.000Z')
  })
})

describe('formatLeadTime', () => {
  it('마감이 모임 몇 분 전인지', () => {
    expect(formatLeadTime(0)).toBe('모일 때까지')
    expect(formatLeadTime(10)).toBe('10분 전')
    expect(formatLeadTime(60)).toBe('1시간 전')
    expect(formatLeadTime(180)).toBe('3시간 전')
    expect(formatLeadTime(90)).toBe('1시간 30분 전')
  })
})
