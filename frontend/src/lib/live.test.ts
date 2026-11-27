import { describe, expect, it } from 'vitest'
import { parseSseBlock, splitSse } from './live'

describe('실시간 연결 읽기', () => {
  it('톡방 새 메시지', () => {
    expect(parseSseBlock('event:chat\ndata:{"meetupId":3,"messageId":10}')).toEqual({
      type: 'chat',
      meetupId: 3,
      messageId: 10,
      deleted: false,
    })
  })

  it('지운 메시지와 읽음', () => {
    expect(parseSseBlock('event: chat\ndata: {"meetupId":3,"messageId":10,"deleted":true}')).toMatchObject({ deleted: true })
    expect(parseSseBlock('event:chat-read\ndata:{"meetupId":4}')).toEqual({ type: 'chat-read', meetupId: 4 })
    expect(parseSseBlock('event:chat-reads\ndata:{"meetupId":4}')).toEqual({ type: 'chat-reads', meetupId: 4 })
  })

  it('주석(ping)과 모르는 이름은 건너뛴다', () => {
    expect(parseSseBlock(':ping')).toBeNull()
    expect(parseSseBlock('event:ready\ndata:{}')).toBeNull()
    expect(parseSseBlock('event:chat\ndata:not-json')).toBeNull()
  })

  it('빈 줄로 나누고 덜 온 뒷부분은 남긴다', () => {
    const { blocks, rest } = splitSse('event:chat\ndata:{"meetupId":1}\n\n:ping\n\nevent:ch')
    expect(blocks).toEqual(['event:chat\ndata:{"meetupId":1}', ':ping'])
    expect(rest).toBe('event:ch')
  })

  it('\\r\\n 줄바꿈도 읽는다', () => {
    const { blocks } = splitSse('event:chat\r\ndata:{"meetupId":1,"messageId":2}\r\n\r\n')
    expect(parseSseBlock(blocks[0])).toMatchObject({ meetupId: 1, messageId: 2 })
  })
})
