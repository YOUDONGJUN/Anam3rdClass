import { describe, expect, it } from 'vitest'
import type { ChatMessage, PersonView } from '../api/types'
import { mergeMessages } from '../api/chat'
import { groupMessages, previewOf, unreadCount } from './chat'

const person = (name: string, ref: number | null): PersonView => ({
  name,
  cohortLabel: '61기',
  departmentLabel: null,
  school: { id: 1, code: 'KU', name: '고려대학교', shortName: '고려대' },
  me: false,
  host: false,
  owner: false,
  masked: false,
  ref,
  crowns: [],
  withdrawn: false,
})

let seq = 0
const msg = (who: string, at: string, extra: Partial<ChatMessage> = {}): ChatMessage => ({
  id: ++seq,
  kind: 'TEXT',
  sender: person(who, who === '나' ? null : who.length),
  mine: who === '나',
  body: '안녕',
  sticker: null,
  photos: [],
  deleted: false,
  hidden: false,
  createdAt: at,
  ...extra,
})

describe('톡방 메시지 묶기', () => {
  it('같은 사람이 3분 안에 이어 보내면 이름은 처음에만, 시각은 끝에만', () => {
    const rows = groupMessages([
      msg('김고대', '2026-03-02T13:00:00Z'),
      msg('김고대', '2026-03-02T13:01:00Z'),
      msg('김고대', '2026-03-02T13:10:00Z'),
      msg('나', '2026-03-02T13:10:30Z'),
    ])
    expect(rows.map((r) => [r.first, r.last])).toEqual([
      [true, false],
      [false, true],
      [true, true],
      [true, true],
    ])
  })

  it('날이 바뀌면 날짜를 넣고 새로 묶는다 (한국 시간)', () => {
    const rows = groupMessages([
      msg('김고대', '2026-03-02T14:59:00Z'), // 3/2 23:59
      msg('김고대', '2026-03-02T15:00:00Z'), // 3/3 00:00
    ])
    expect(rows[0].dayBreak).toBe('3월 2일 (월)')
    expect(rows[1].dayBreak).toBe('3월 3일 (화)')
    expect(rows[1].first).toBe(true)
    expect(rows[0].last).toBe(true)
  })

  it('미리보기', () => {
    expect(previewOf(msg('김고대', '2026-03-02T13:00:00Z', { deleted: true, body: null }))).toBe('지운 메시지예요')
    expect(
      previewOf(
        msg('김고대', '2026-03-02T13:00:00Z', {
          body: null,
          photos: [
            { key: 'a', url: 'u', expired: false },
            { key: 'b', url: 'u', expired: false },
          ],
        }),
      ),
    ).toBe('사진 2장')
    expect(previewOf(msg('김고대', '2026-03-02T13:00:00Z', { body: null, sticker: 'tiger-going' }))).toBe(
      '(이모티콘) 가는 중',
    )
    expect(previewOf(msg('박호랑', '2026-03-02T13:00:00Z', { kind: 'JOIN', body: null }))).toBe('박호랑님이 들어왔어요')
    expect(previewOf(msg('박호랑', '2026-03-02T13:00:00Z', { kind: 'LEAVE', body: null }))).toBe('박호랑님이 나갔어요')
  })

  it('들어옴/나감 줄은 덩어리를 끊는다', () => {
    const rows = groupMessages([
      msg('김고대', '2026-03-02T13:00:00Z'),
      msg('박호랑', '2026-03-02T13:00:10Z', { kind: 'JOIN', body: null }),
      msg('김고대', '2026-03-02T13:00:20Z'),
    ])
    expect(rows[0].last).toBe(true)
    expect(rows[2].first).toBe(true)
  })

  it('안 읽은 사람 수: 그 메시지보다 앞까지만 읽은 원우 수 (안내 줄 · 지운 메시지는 0)', () => {
    const m = msg('나', '2026-03-02T13:00:00Z')
    expect(unreadCount(m, [m.id - 1, m.id, m.id + 3])).toBe(1)
    expect(unreadCount(m, [])).toBe(0)
    expect(unreadCount({ ...m, deleted: true }, [0, 0])).toBe(0)
    expect(unreadCount({ ...m, kind: 'JOIN' }, [0, 0])).toBe(0)
  })

  it('합칠 때 같은 번호는 새것으로, 번호 순으로', () => {
    const a = msg('김고대', '2026-03-02T13:00:00Z')
    const b = msg('김고대', '2026-03-02T13:01:00Z')
    const merged = mergeMessages([b], [a, { ...b, deleted: true }])
    expect(merged.map((m) => m.id)).toEqual([a.id, b.id])
    expect(merged[1].deleted).toBe(true)
  })
})
