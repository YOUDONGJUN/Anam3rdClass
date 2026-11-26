import { describe, expect, it } from 'vitest'
import { splitLinks } from './linkify'

describe('공지 본문 링크', () => {
  it('주소만 링크로 나누고 뒤에 붙은 문장부호는 뺀다', () => {
    expect(splitLinks('설문: https://forms.gle/abc. 꼭 해 주세요')).toEqual([
      { text: '설문: ' },
      { text: 'https://forms.gle/abc', url: 'https://forms.gle/abc' },
      { text: '. 꼭 해 주세요' },
    ])
    expect(splitLinks('(https://open.kakao.com/o/x)')).toEqual([
      { text: '(' },
      { text: 'https://open.kakao.com/o/x', url: 'https://open.kakao.com/o/x' },
      { text: ')' },
    ])
  })

  it('주소가 없으면 그대로', () => {
    expect(splitLinks('내일 7시 정문')).toEqual([{ text: '내일 7시 정문' }])
  })
})
