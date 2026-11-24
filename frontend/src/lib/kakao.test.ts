import { describe, expect, it } from 'vitest'
import { kakaoAuthorizeUrl, kakaoRedirectUri } from './kakao'

describe('kakao', () => {
  it('Redirect URI 는 카카오 디벨로퍼스에 등록한 주소와 같아야 한다', () => {
    expect(kakaoRedirectUri('http://localhost:5173')).toBe('http://localhost:5173/auth/kakao/callback')
    expect(kakaoRedirectUri('https://anam3.onrender.com')).toBe('https://anam3.onrender.com/auth/kakao/callback')
  })

  it('카카오 로그인 창 주소', () => {
    const url = new URL(kakaoAuthorizeUrl('rest-key', 'https://anam3.onrender.com/auth/kakao/callback', 'abc'))
    expect(url.origin + url.pathname).toBe('https://kauth.kakao.com/oauth/authorize')
    expect(url.searchParams.get('client_id')).toBe('rest-key')
    expect(url.searchParams.get('redirect_uri')).toBe('https://anam3.onrender.com/auth/kakao/callback')
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('state')).toBe('abc')
  })
})
