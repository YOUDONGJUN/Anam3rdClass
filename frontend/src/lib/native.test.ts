import { describe, expect, it } from 'vitest'
import { appPathFromUrl, isApp } from './native'

describe('앱 링크', () => {
  it('웹(테스트)에서는 앱이 아니다', () => {
    expect(isApp).toBe(false)
  })

  it('우리 서버 주소의 링크만 앱 안 화면으로 연다', () => {
    const origin = 'https://anam3.onrender.com'
    expect(appPathFromUrl('https://anam3.onrender.com/meetups/12', origin)).toBe('/meetups/12')
    expect(appPathFromUrl('https://anam3.onrender.com/notices/3?x=1#top', origin)).toBe('/notices/3?x=1#top')
    expect(appPathFromUrl('https://open.kakao.com/o/abc', origin)).toBeNull()
    expect(appPathFromUrl('http://anam3.onrender.com/meetups/12', origin)).toBeNull()
    expect(appPathFromUrl('not a url', origin)).toBeNull()
  })
})
