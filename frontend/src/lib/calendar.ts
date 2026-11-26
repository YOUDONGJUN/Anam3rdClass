// 3교시를 캘린더에 넣기. 웹은 .ics 파일(1시간 전 알림 포함) 또는 구글 캘린더, 앱은 구글 캘린더 링크.
// 끝나는 시간은 정하지 않는 서비스라 모임 시간부터 2시간으로 넣는다.
import { COMPANY, SERVICE } from './credit'

export interface CalendarEvent {
  /** 일정 제목 앞에 붙는 이름 ("[신촌3교시] …"). 없으면 공식 이름 */
  brand?: string
  id: number
  title: string
  place: string
  /** ISO */
  meetAt: string
  /** 3교시 상세 주소 */
  url: string
}

const LENGTH_MS = 2 * 60 * 60_000

/** 20260303T130000Z */
export function icsTime(date: Date): string {
  return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
}

/** 캘린더 글자: 역슬래시 · 쉼표 · 세미콜론 · 줄바꿈을 이스케이프 */
export function icsText(text: string): string {
  return text.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n')
}

/** 한 줄이 75바이트를 넘으면 접는다 (다음 줄은 공백으로 시작). 한글을 중간에서 자르지 않는다 */
export function foldIcsLine(line: string): string {
  const encoder = new TextEncoder()
  const parts: string[] = []
  let current = ''
  let bytes = 0
  for (const ch of line) {
    const size = encoder.encode(ch).length
    const limit = parts.length === 0 ? 75 : 74 // 이어지는 줄은 앞 공백 1바이트
    if (bytes + size > limit) {
      parts.push(current)
      current = ''
      bytes = 0
    }
    current += ch
    bytes += size
  }
  parts.push(current)
  return parts.join('\r\n ')
}

/** .ics 파일 내용 (1시간 전 알림 포함) */
export function buildIcs(e: CalendarEvent, now: Date = new Date()): string {
  const start = new Date(e.meetAt)
  const end = new Date(start.getTime() + LENGTH_MS)
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    `PRODID:-//${COMPANY}//${SERVICE}//KO`,
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:meetup-${e.id}@anam3`,
    `DTSTAMP:${icsTime(now)}`,
    `DTSTART:${icsTime(start)}`,
    `DTEND:${icsTime(end)}`,
    `SUMMARY:${icsText(`[${e.brand ?? SERVICE}] ${e.title}`)}`,
    `LOCATION:${icsText(e.place)}`,
    `DESCRIPTION:${icsText(e.url)}`,
    `URL:${e.url}`,
    'BEGIN:VALARM',
    'TRIGGER:-PT1H',
    'ACTION:DISPLAY',
    `DESCRIPTION:${icsText(`1시간 뒤 3교시 · ${e.title}`)}`,
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR',
  ]
  return lines.map(foldIcsLine).join('\r\n') + '\r\n'
}

/** 구글 캘린더 일정 만들기 링크 (알림은 구글 캘린더 기본 알림을 따른다) */
export function googleCalendarUrl(e: CalendarEvent): string {
  const start = new Date(e.meetAt)
  const end = new Date(start.getTime() + LENGTH_MS)
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: `[${e.brand ?? SERVICE}] ${e.title}`,
    dates: `${icsTime(start)}/${icsTime(end)}`,
    location: e.place,
    details: e.url,
  })
  return `https://calendar.google.com/calendar/render?${params.toString()}`
}

/** .ics 파일 내려받기 (웹). 폰은 받은 파일을 누르면 캘린더 앱으로 열린다 */
export function downloadIcs(e: CalendarEvent) {
  const blob = new Blob([buildIcs(e)], { type: 'text/calendar;charset=utf-8' })
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = `anam3-${e.id}.ics`
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}
