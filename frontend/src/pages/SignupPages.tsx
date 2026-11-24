import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Check, History, Mail } from 'lucide-react'
import { api, ApiError } from '../api/client'
import { useSignupOptions } from '../api/queries'
import type { AuthStep, EmailCodeSent } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AffiliationFields } from '../components/AffiliationFields'
import { Brand } from '../components/Brand'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, Field, FormError, Spinner } from '../components/ui'
import { buttonClass, inputClass } from '../components/styles'
import { TermsCheckbox } from './TermsPages'
import { useNow } from '../lib/hooks'
import {
  clearSignupProgress,
  consumeKakaoState,
  kakaoRedirectUri,
  loadSignupProgress,
  saveSignupProgress,
  takeReturnPath,
  type SignupProgress,
} from '../lib/kakao'

/** 로그인 끝: 내 정보 저장하고 원래 가려던 화면으로 */
function useFinishLogin() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  return useCallback(
    (res: AuthStep) => {
      if (!res.token || !res.member) return
      clearSignupProgress()
      signIn({ token: res.token, member: res.member })
      navigate(takeReturnPath(), { replace: true })
    },
    [signIn, navigate],
  )
}

function AuthFrame({ children, step }: { children: ReactNode; step?: 1 | 2 }) {
  return (
    <div className="min-h-dvh bg-bg">
      <header className="bg-board pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex h-14 max-w-md items-center px-4">
          <Brand />
        </div>
        <div className="h-1 bg-accent" aria-hidden />
      </header>
      <main className="mx-auto max-w-md px-4 py-8">
        {step && (
          <ol className="mb-5 flex items-center gap-2 text-sm font-semibold" aria-label="가입 단계">
            <StepDot n={1} label="학교 이메일 인증" active={step === 1} done={step > 1} />
            <span aria-hidden className="h-0.5 w-6 rounded bg-line-strong" />
            <StepDot n={2} label="이름·기수·학과" active={step === 2} done={false} />
          </ol>
        )}
        <div className="rounded-3xl border border-line bg-surface p-6 shadow-[0_12px_40px_-16px_rgb(0_0_0/0.18)] sm:p-8">
          {children}
        </div>
      </main>
    </div>
  )
}

function StepDot({ n, label, active, done }: { n: number; label: string; active: boolean; done: boolean }) {
  return (
    <li className={`flex items-center gap-1.5 ${active ? 'text-ink' : 'text-ink-3'}`} aria-current={active ? 'step' : undefined}>
      <span
        className={`flex size-6 items-center justify-center rounded-full text-xs font-bold ${
          active || done ? 'bg-accent text-on-accent' : 'bg-surface-2 text-ink-3'
        }`}
      >
        {done ? <Check className="size-3.5" strokeWidth={3} aria-hidden /> : n}
      </span>
      {label}
    </li>
  )
}

function RestartLink() {
  return (
    <Link to="/welcome" replace onClick={clearSignupProgress} className={buttonClass('secondary', 'md', 'w-full')}>
      카카오 로그인부터 다시 하기
    </Link>
  )
}

// ---------------- /auth/kakao/callback ----------------

// React 개발 모드(StrictMode)는 effect 를 두 번 돌린다. 카카오 인가 코드는 한 번만 쓸 수 있어서 요청을 코드별로 하나만 만든다
const kakaoRequests = new Map<string, Promise<AuthStep>>()

function kakaoLogin(code: string, state: string | null): Promise<AuthStep> {
  let request = kakaoRequests.get(code)
  if (!request) {
    request = consumeKakaoState(state)
      ? api<AuthStep>('/api/auth/kakao', { method: 'POST', body: { code, redirectUri: kakaoRedirectUri() } })
      : Promise.reject(new Error('로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요'))
    kakaoRequests.set(code, request)
  }
  return request
}

/** 카카오가 돌려보내는 곳. 이미 가입했으면 바로 로그인, 처음이면 가입 화면으로 */
export function KakaoCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const finish = useFinishLogin()
  const [error, setError] = useState<string | null>(null)

  const code = params.get('code')
  const state = params.get('state')
  const kakaoError = params.get('error')
  // 카카오 동의 화면에서 "취소"를 누르면 error=access_denied 로 돌아온다
  const immediateError = kakaoError
    ? kakaoError === 'access_denied'
      ? '카카오 로그인을 취소했어요'
      : '카카오 로그인에 실패했어요. 다시 시도해 주세요'
    : !code
      ? '카카오 로그인 정보가 없어요. 다시 시도해 주세요'
      : null

  useEffect(() => {
    if (!code || kakaoError) return
    let active = true // 두 번 돌아도 결과는 마지막 한 번만 처리한다
    kakaoLogin(code, state)
      .then((res) => {
        if (!active) return
        if (res.step === 'DONE') {
          finish(res)
        } else if (res.signupToken) {
          saveSignupProgress({ signupToken: res.signupToken, email: null, schoolId: null, schoolName: null })
          navigate('/signup', { replace: true })
        }
      })
      .catch((e: unknown) => {
        if (active) setError(e instanceof Error ? e.message : '카카오 로그인에 실패했어요')
      })
    return () => {
      active = false
    }
  }, [code, state, kakaoError, finish, navigate])

  const message = immediateError ?? error
  return (
    <AuthFrame>
      {message ? (
        <div className="flex flex-col gap-4">
          <FormError message={message} />
          <RestartLink />
        </div>
      ) : (
        <Spinner label="카카오 계정 확인 중" />
      )}
    </AuthFrame>
  )
}

// ---------------- /signup ----------------

/** 처음 온 카카오 계정: 학교 이메일 인증 → 이름·기수·학과 */
export function SignupPage() {
  const [progress, setProgress] = useState<SignupProgress | null>(loadSignupProgress)

  if (!progress) {
    return (
      <AuthFrame>
        <p className="mb-4 text-[15px] text-ink-2">가입 정보가 없거나 시간이 지났어요.</p>
        <RestartLink />
      </AuthFrame>
    )
  }
  return progress.email ? (
    <AuthFrame step={2}>
      <ProfileStep progress={progress} />
    </AuthFrame>
  ) : (
    <AuthFrame step={1}>
      <EmailStep
        progress={progress}
        onVerified={(next) => {
          saveSignupProgress(next)
          setProgress(next)
        }}
      />
    </AuthFrame>
  )
}

function expiredMessage(e: unknown): string | null {
  return e instanceof ApiError && e.code === 'SIGNUP_EXPIRED' ? e.message : null
}

function EmailStep({ progress, onVerified }: { progress: SignupProgress; onVerified: (next: SignupProgress) => void }) {
  const finish = useFinishLogin()
  const options = useSignupOptions()
  const now = useNow(1000)
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState<(EmailCodeSent & { resendAt: number }) | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const schools = options.data?.schools ?? []
  const resendIn = sent ? Math.max(0, Math.ceil((sent.resendAt - now.getTime()) / 1000)) : 0

  async function sendCode(e?: FormEvent) {
    e?.preventDefault()
    setError(null)
    setPending(true)
    try {
      const res = await api<EmailCodeSent>('/api/auth/email/code', {
        method: 'POST',
        body: { signupToken: progress.signupToken, email: email.trim() },
      })
      setSent({ ...res, resendAt: Date.now() + res.resendAfterSeconds * 1000 })
      setCode('')
    } catch (err) {
      setExpired(expiredMessage(err))
      setError(err instanceof Error ? err.message : '인증코드를 보내지 못했어요')
    } finally {
      setPending(false)
    }
  }

  async function verify(e: FormEvent) {
    e.preventDefault()
    if (!sent) return
    setError(null)
    setPending(true)
    try {
      const res = await api<AuthStep>('/api/auth/email/verify', {
        method: 'POST',
        body: { signupToken: progress.signupToken, email: sent.email, code },
      })
      if (res.step === 'DONE') {
        finish(res) // 이미 있는 계정(예전 회원, 카카오 계정 변경)에 연결됐다
      } else if (res.signupToken) {
        onVerified({ signupToken: res.signupToken, email: res.email, schoolId: res.schoolId, schoolName: res.schoolName })
      }
    } catch (err) {
      setExpired(expiredMessage(err))
      setError(err instanceof Error ? err.message : '인증하지 못했어요')
      setPending(false)
    }
  }

  if (expired) {
    return (
      <div className="flex flex-col gap-4">
        <FormError message={expired} />
        <RestartLink />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-extrabold text-ink">학교 이메일로 한 번만 인증해요</h1>
        <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">
          IT연합회 대학원 원우인지 확인하는 용도예요. 다른 사람에게 이메일은 보이지 않아요.
        </p>
      </div>

      <form onSubmit={sendCode} className="flex flex-col gap-3" noValidate>
        <Field label="학교 이메일">
          {({ id, describedBy }) => (
            <input
              id={id}
              type="email"
              inputMode="email"
              autoComplete="email"
              autoCapitalize="none"
              aria-describedby={describedBy}
              className={inputClass}
              placeholder="학교 메일 주소"
              value={email}
              disabled={Boolean(sent)}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </Field>
        {!sent && schools.length > 0 && (
          <ul className="flex flex-col gap-1.5 rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2" aria-label="쓸 수 있는 학교 메일">
            {schools.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <SchoolBadge school={s} />
                <span className="tabular">{s.emailDomains.map((d) => `@${d.domain}`).join(' · ')}</span>
              </li>
            ))}
          </ul>
        )}
        {!sent ? (
          <Button type="submit" size="lg" loading={pending} disabled={!email.trim()}>
            <Mail className="size-5" aria-hidden />
            인증코드 받기
          </Button>
        ) : (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-2">
            <span>
              <b className="text-ink">{sent.email}</b> 로 코드를 보냈어요. 스팸함도 확인해 주세요.
            </span>
            <button
              type="button"
              className="font-semibold text-accent-ink underline underline-offset-4"
              onClick={() => {
                setSent(null)
                setCode('')
                setError(null)
              }}
            >
              이메일 바꾸기
            </button>
          </div>
        )}
      </form>

      {sent && (
        <form onSubmit={verify} className="flex flex-col gap-3" noValidate>
          <Field label="인증코드 6자리" hint="10분 안에 입력해 주세요">
            {({ id, describedBy }) => (
              <input
                id={id}
                inputMode="numeric"
                autoComplete="one-time-code"
                aria-describedby={describedBy}
                maxLength={6}
                className={`${inputClass} tabular text-center text-xl font-bold tracking-[0.4em]`}
                placeholder="000000"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              />
            )}
          </Field>
          <Button type="submit" size="lg" loading={pending} disabled={code.length !== 6}>
            인증하기
          </Button>
          <Button variant="ghost" size="sm" disabled={resendIn > 0 || pending} onClick={() => sendCode()}>
            {resendIn > 0 ? `${resendIn}초 뒤에 다시 받을 수 있어요` : '코드 다시 받기'}
          </Button>
        </form>
      )}

      <FormError message={error} />
    </div>
  )
}

/**
 * 예전(아이디·비밀번호 시절)에 가입했던 원우: 예전 아이디·비밀번호를 맞히면 그 계정을 그대로 잇는다.
 * 예전 3교시·공지 기록이 남고, 이름·기수·학과도 예전 그대로다.
 */
function LegacyClaim({ progress, onExpired }: { progress: SignupProgress; onExpired: (message: string) => void }) {
  const finish = useFinishLogin()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      finish(
        await api<AuthStep>('/api/auth/legacy', {
          method: 'POST',
          body: { signupToken: progress.signupToken, loginId: loginId.trim(), password },
        }),
      )
    } catch (err) {
      const expired = expiredMessage(err)
      if (expired) onExpired(expired)
      setError(err instanceof Error ? err.message : '예전 계정을 잇지 못했어요')
      setPending(false)
    }
  }

  return (
    <details className="group rounded-xl border border-line-strong bg-surface px-4 py-3">
      <summary className="flex min-h-8 cursor-pointer list-none items-center gap-2 text-[15px] font-semibold text-ink">
        <History className="size-4 shrink-0 text-ink-3" aria-hidden />
        예전에 아이디·비밀번호로 가입했나요?
        <span className="ml-auto shrink-0 text-sm font-medium whitespace-nowrap text-accent-ink group-open:hidden">
          잇기
        </span>
      </summary>
      <form onSubmit={submit} className="mt-3 flex flex-col gap-3" noValidate>
        <p className="text-sm leading-relaxed text-ink-2">
          예전 아이디·비밀번호를 넣으면 그 계정에 카카오와 학교 이메일을 연결해요. 예전 3교시 기록과 이름·기수·학과가
          그대로 이어져요.
        </p>
        <Field label="예전 아이디">
          {({ id }) => (
            <input
              id={id}
              className={inputClass}
              autoComplete="username"
              autoCapitalize="none"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
            />
          )}
        </Field>
        <Field label="예전 비밀번호">
          {({ id }) => (
            <input
              id={id}
              type="password"
              className={inputClass}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </Field>
        <FormError message={error} />
        <Button type="submit" variant="secondary" loading={pending} disabled={!loginId.trim() || !password}>
          예전 계정 잇고 시작하기
        </Button>
      </form>
    </details>
  )
}

function ProfileStep({ progress }: { progress: SignupProgress }) {
  const finish = useFinishLogin()
  const options = useSignupOptions()
  const [name, setName] = useState('')
  const [cohort, setCohort] = useState('')
  const [departmentId, setDepartmentId] = useState<number | null>(null)
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      finish(
        await api<AuthStep>('/api/auth/signup', {
          method: 'POST',
          body: {
            signupToken: progress.signupToken,
            name: name.trim(),
            cohort: Number(cohort),
            departmentId,
            agreeTerms: agree,
          },
        }),
      )
    } catch (err) {
      setExpired(expiredMessage(err))
      setError(err instanceof Error ? err.message : '가입하지 못했어요')
      setPending(false)
    }
  }

  if (expired) {
    return (
      <div className="flex flex-col gap-4">
        <FormError message={expired} />
        <RestartLink />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-extrabold text-ink">거의 다 왔어요</h1>
        <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">
          <b className="text-ink">{progress.email}</b>
          {progress.schoolName && ` (${progress.schoolName})`} 인증을 마쳤어요.
        </p>
      </div>
      {progress.schoolId !== null && options.data?.legacySchoolIds.includes(progress.schoolId) && (
        <LegacyClaim progress={progress} onExpired={setExpired} />
      )}
        <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
        <Field label="이름" hint="실명으로 적어 주세요. 3교시가 성사되면 함께하는 사람에게만 보여요.">
          {({ id, describedBy }) => (
            <input
              id={id}
              aria-describedby={describedBy}
              className={inputClass}
              autoComplete="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          )}
        </Field>
        <AffiliationFields
          schoolId={progress.schoolId}
          cohort={cohort}
          departmentId={departmentId}
          onCohortChange={setCohort}
          onDepartmentChange={setDepartmentId}
        />
        <p className="rounded-xl bg-exit/25 px-4 py-3 text-sm leading-relaxed text-ink">
          가입하면 <b>이름·기수·학과는 직접 바꿀 수 없어요.</b> 바꿔야 하면 교수나 대학원장에게 요청해 주세요.
        </p>
        <TermsCheckbox checked={agree} onChange={setAgree} />
        <FormError message={error} />
        <Button type="submit" size="lg" loading={pending} disabled={!name.trim() || !cohort || !departmentId || !agree}>
          가입하고 시작하기
        </Button>
        </form>
    </div>
  )
}
