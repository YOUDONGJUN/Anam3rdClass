import { startTransition, useCallback } from 'react'
import { useNavigate } from 'react-router'
import type { AuthStep } from '../api/types'
import { clearSignupProgress, saveSignupProgress, takeReturnPath } from '../lib/kakao'
import { useAuth } from './AuthContext'

/**
 * 로그인 끝: 내 정보 저장하고 원래 가려던 화면으로.
 * 로그인 상태와 화면 이동을 한 번에 바꾼다 — 따로 바뀌면 그 사이 가입 화면(GuestOnly)이 첫 화면으로 보내 버린다
 */
export function useFinishLogin() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  return useCallback(
    (res: AuthStep) => {
      if (!res.token || !res.member) return
      const to = takeReturnPath()
      clearSignupProgress()
      const { token, member } = res
      startTransition(() => {
        signIn({ token, member })
        navigate(to, { replace: true })
      })
    },
    [signIn, navigate],
  )
}

/** 카카오 로그인 결과 (웹 콜백 · 앱 카카오톡 로그인 같이 쓴다): 가입한 원우면 로그인, 처음이면 가입 화면으로 */
export function useKakaoStep() {
  const finish = useFinishLogin()
  const navigate = useNavigate()
  return useCallback(
    (res: AuthStep) => {
      if (res.step === 'DONE') {
        finish(res)
      } else if (res.signupToken) {
        saveSignupProgress({
          signupToken: res.signupToken,
          email: null,
          schoolId: null,
          schoolName: null,
          via: 'kakao',
          purpose: 'signup',
        })
        navigate('/signup', { replace: true })
      }
    },
    [finish, navigate],
  )
}
