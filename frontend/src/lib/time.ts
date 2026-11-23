// 시간은 서버와 UTC(ISO 문자열)로 주고받고, 화면에는 항상 한국 시간(KST)으로 보여준다.
// 사용자의 기기 시간대 설정과 관계없이 KST 로 계산한다.

const KST_OFFSET_MS = 9 * 60 * 60 * 1000
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

interface KstParts {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  weekday: number
}

/** UTC+9 는 서머타임이 없어서 단순히 9시간을 더해 계산해도 정확하다 */
export function kstParts(date: Date): KstParts {
  const shifted = new Date(date.getTime() + KST_OFFSET_MS)
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
    weekday: shifted.getUTCDay(),
  }
}

const pad = (n: number) => String(n).padStart(2, '0')

/** KST 기준 n일 뒤 hh:mm 시각 */
export function kstAt(now: Date, dayOffset: number, hour: number, minute = 0): Date {
  const p = kstParts(now)
  return new Date(Date.UTC(p.year, p.month - 1, p.day + dayOffset, hour, minute) - KST_OFFSET_MS)
}

/** "2026-11-24" — 날짜별로 묶을 때 쓰는 키 */
export function kstDayKey(iso: string | Date): string {
  const p = kstParts(new Date(iso))
  return `${p.year}-${pad(p.month)}-${pad(p.day)}`
}

function dayDiff(target: Date, now: Date): number {
  const a = kstParts(target)
  const b = kstParts(now)
  return Math.round((Date.UTC(a.year, a.month - 1, a.day) - Date.UTC(b.year, b.month - 1, b.day)) / 86_400_000)
}

/** "22:00" */
export function formatTime(iso: string | Date): string {
  const p = kstParts(new Date(iso))
  return `${pad(p.hour)}:${pad(p.minute)}`
}

/** "11월 26일 (수)" */
export function formatDate(iso: string | Date): string {
  const p = kstParts(new Date(iso))
  return `${p.month}월 ${p.day}일 (${WEEKDAYS[p.weekday]})`
}

/** "오늘" / "내일" / "모레" / "어제" / "11월 26일 (수)" */
export function formatDayLabel(iso: string | Date, now: Date = new Date()): string {
  const diff = dayDiff(new Date(iso), now)
  if (diff === 0) return '오늘'
  if (diff === 1) return '내일'
  if (diff === 2) return '모레'
  if (diff === -1) return '어제'
  return formatDate(iso)
}

/** "오늘 22:00" / "11월 26일 (수) 22:00" */
export function formatDateTime(iso: string | Date, now: Date = new Date()): string {
  return `${formatDayLabel(iso, now)} ${formatTime(iso)}`
}

/** 남은 시간: "2일 3시간" / "1시간 20분" / "35분" / "1분 미만" */
export function formatRemaining(iso: string | Date, now: Date = new Date()): string {
  const ms = new Date(iso).getTime() - now.getTime()
  if (ms <= 0) return '지남'
  const minutes = Math.floor(ms / 60_000)
  if (minutes < 1) return '1분 미만'
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const mins = minutes % 60
  if (days > 0) return hours > 0 ? `${days}일 ${hours}시간` : `${days}일`
  if (hours > 0) return mins > 0 ? `${hours}시간 ${mins}분` : `${hours}시간`
  return `${mins}분`
}

/** 글 작성 시각: "방금" / "5분 전" / "3시간 전" / "어제" / "11월 2일" */
export function formatRelative(iso: string | Date, now: Date = new Date()): string {
  const date = new Date(iso)
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 60 * 12 && dayDiff(date, now) === 0) return `${Math.floor(minutes / 60)}시간 전`
  const diff = dayDiff(date, now)
  if (diff === 0) return `오늘 ${formatTime(date)}`
  if (diff === -1) return '어제'
  const p = kstParts(date)
  const n = kstParts(now)
  return p.year === n.year ? `${p.month}월 ${p.day}일` : `${p.year}. ${p.month}. ${p.day}.`
}

/** <input type="datetime-local"> 값 (KST) */
export function toKstInputValue(date: Date): string {
  const p = kstParts(date)
  return `${p.year}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}`
}

/** datetime-local 값(KST로 해석) → Date. 형식이 틀리면 null */
export function fromKstInputValue(value: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null
  const date = new Date(`${value}:00+09:00`)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 다음 10분 단위로 올림 */
export function ceilTo10Minutes(date: Date): Date {
  const step = 10 * 60_000
  return new Date(Math.ceil(date.getTime() / step) * step)
}
