import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Check, History, Mail } from 'lucide-react'
import { api, ApiError } from '../api/client'
import { useSignupOptions } from '../api/queries'
import type { AuthStep, EmailCodeSent } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useFinishLogin, useKakaoStep } from '../auth/loginSteps'
import { AffiliationFields } from '../components/AffiliationFields'
import { Brand } from '../components/Brand'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, Field, FormError, Spinner } from '../components/ui'
import { buttonClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { TermsCheckbox } from './TermsPages'
import { useNow } from '../lib/hooks'
import { PASSWORD_RULE, passwordProblem } from '../lib/password'
import { withEmailDomain } from '../lib/schools'
import {
  clearSignupProgress,
  consumeKakaoState,
  kakaoRedirectUri,
  loadSignupProgress,
  saveReturnPath,
  saveSignupProgress,
  type SignupProgress,
} from '../lib/kakao'

/** 카카오 없이 시작 (가입 · 비밀번호 찾기 · 비밀번호 만들기): 가입 토큰을 받아 진행을 저장한다 */
async function startEmailFlow(purpose: 'signup' | 'password', presetEmail: string | null = null): Promise<SignupProgress> {
  const res = await api<AuthStep>('/api/auth/email/start', { method: 'POST' })
  const progress: SignupProgress = {
    signupToken: res.signupToken ?? '',
    email: null,
    schoolId: null,
    schoolName: null,
    via: 'email',
    purpose,
    presetEmail,
  }
  saveSignupProgress(progress)
  return progress
}

function AuthFrame({ children, step, secondStep = '이름·기수·학과' }: { children: ReactNode; step?: 1 | 2; secondStep?: string }) {
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
            <StepDot n={2} label={secondStep} active={step === 2} done={false} />
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

function RestartLink({ via }: { via?: 'kakao' | 'email' }) {
  return via === 'email' ? (
    <Link to="/welcome" replace onClick={clearSignupProgress} className={buttonClass('secondary', 'md', 'w-full')}>
      처음부터 다시 하기
    </Link>
  ) : (
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
  const kakaoStep = useKakaoStep()
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
        if (active) kakaoStep(res)
      })
      .catch((e: unknown) => {
        if (active) setError(e instanceof Error ? e.message : '카카오 로그인에 실패했어요')
      })
    return () => {
      active = false
    }
  }, [code, state, kakaoError, kakaoStep])

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

// ---------------- /login/email ----------------

/** 학교 이메일 + 비밀번호 로그인. 가입과 비밀번호 찾기도 여기서 간다 */
export function EmailLoginPage() {
  const finish = useFinishLogin()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [starting, setStarting] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      finish(await api<AuthStep>('/api/auth/email/login', { method: 'POST', body: { email: email.trim(), password } }))
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인하지 못했어요')
      setPending(false)
    }
  }

  async function startSignup() {
    setError(null)
    setStarting(true)
    try {
      await startEmailFlow('signup')
      navigate('/signup')
    } catch (err) {
      setError(err instanceof Error ? err.message : '가입을 시작하지 못했어요')
      setStarting(false)
    }
  }

  return (
    <AuthFrame>
      <div className="flex flex-col gap-5">
        <div>
          <h1 className="text-xl font-extrabold text-ink">이메일로 로그인</h1>
          <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">가입한 학교 메일과 비밀번호를 넣어 주세요.</p>
        </div>
        <form onSubmit={submit} className="flex flex-col gap-3" noValidate>
          <Field label="학교 이메일">
            {({ id }) => (
              <input
                id={id}
                type="email"
                inputMode="email"
                autoComplete="username"
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                className={inputClass}
                placeholder="학교 메일 주소"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            )}
          </Field>
          <Field label="비밀번호">
            {({ id }) => (
              <input
                id={id}
                type="password"
                autoComplete="current-password"
                className={inputClass}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            )}
          </Field>
          <FormError message={error} />
          <Button type="submit" size="lg" loading={pending} disabled={!email.trim() || !password}>
            로그인
          </Button>
        </form>
        <div className="flex flex-col gap-2 border-t border-line pt-5">
          <Button variant="secondary" loading={starting} onClick={startSignup}>
            <Mail className="size-4" aria-hidden />
            학교 메일로 가입하기
          </Button>
          <div className="flex items-center justify-between gap-3 px-1 text-sm">
            <Link to="/password" className="font-semibold text-ink-2 underline underline-offset-4 hover:text-ink">
              비밀번호를 잊었어요
            </Link>
            <Link to="/welcome" className="text-ink-3 hover:text-ink">
              카카오로 시작하기
            </Link>
          </div>
        </div>
        <p className="text-[13px] leading-relaxed text-ink-3">
          카카오로 가입했다면 비밀번호 찾기에서 비밀번호를 만들면 이메일로도 들어올 수 있어요. 같은 계정이에요.
        </p>
      </div>
    </AuthFrame>
  )
}

// ---------------- /signup · /password ----------------

/** 처음 온 카카오 계정, 또는 이메일로 가입: 학교 이메일 인증 → 이름·기수·학과 (이미 있는 메일이면 새 비밀번호) */
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
  return <AuthFlow progress={progress} onProgress={setProgress} />
}

/**
 * 비밀번호 찾기 · 만들기 (로그인한 채로도 들어온다): 학교 이메일 인증 → 새 비밀번호.
 * 가입한 적 없는 메일이면 그대로 가입으로 이어진다
 */
export function PasswordPage() {
  const { member } = useAuth()
  const presetEmail = member?.email ?? null
  const [progress, setProgress] = useState<SignupProgress | null>(() => {
    const saved = loadSignupProgress()
    // 로그인한 사람이 바뀌었거나(공용 PC) 로그인 상태가 달라진 진행은 버린다
    return saved?.purpose === 'password' && (saved.presetEmail ?? null) === presetEmail ? saved : null
  })
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (progress) return
    let active = true
    if (member) saveReturnPath('/me')
    startEmailFlow('password', presetEmail)
      .then((next) => active && setProgress(next))
      .catch((e: unknown) => active && setError(e instanceof Error ? e.message : '시작하지 못했어요'))
    return () => {
      active = false
    }
  }, [progress, presetEmail, member])

  if (error) {
    return (
      <AuthFrame>
        <div className="flex flex-col gap-4">
          <FormError message={error} />
          <RestartLink via="email" />
        </div>
      </AuthFrame>
    )
  }
  if (!progress) {
    return (
      <AuthFrame>
        <Spinner label="준비 중" />
      </AuthFrame>
    )
  }
  return <AuthFlow progress={progress} onProgress={setProgress} />
}

function AuthFlow({ progress, onProgress }: { progress: SignupProgress; onProgress: (next: SignupProgress) => void }) {
  if (!progress.email) {
    return (
      <AuthFrame step={1} secondStep={progress.purpose === 'password' ? '새 비밀번호' : '이름·기수·학과'}>
        <EmailStep
          progress={progress}
          onVerified={(next) => {
            saveSignupProgress(next)
            onProgress(next)
          }}
        />
      </AuthFrame>
    )
  }
  return progress.existing ? (
    <AuthFrame step={2} secondStep="새 비밀번호">
      <PasswordStep progress={progress} />
    </AuthFrame>
  ) : (
    <AuthFrame step={2}>
      <ProfileStep progress={progress} />
    </AuthFrame>
  )
}

function expiredMessage(e: unknown): string | null {
  return e instanceof ApiError && e.code === 'SIGNUP_EXPIRED' ? e.message : null
}

function emailStepTitle(progress: SignupProgress) {
  if (progress.purpose === 'password') {
    return { title: '학교 메일로 확인할게요', sub: '가입한 학교 메일로 인증코드를 보내요. 인증하면 새 비밀번호를 정해요.' }
  }
  if (progress.via === 'email') {
    return { title: '학교 메일로 가입해요', sub: '원우 확인용이에요. 이메일은 남에게 안 보여요.' }
  }
  return { title: '학교 이메일로 한 번만 인증해요', sub: '원우 확인용이에요. 이메일은 남에게 안 보여요.' }
}

function EmailStep({ progress, onVerified }: { progress: SignupProgress; onVerified: (next: SignupProgress) => void }) {
  const finish = useFinishLogin()
  const options = useSignupOptions()
  const now = useNow(1000)
  const [email, setEmail] = useState(progress.presetEmail ?? '')
  /** 로그인한 채로 비밀번호를 만들 때는 내 학교 메일로만 */
  const fixedEmail = Boolean(progress.presetEmail)
  const emailRef = useRef<HTMLInputElement>(null)
  const [sent, setSent] = useState<(EmailCodeSent & { resendAt: number }) | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const schools = options.data?.schools ?? []
  const resendIn = sent ? Math.max(0, Math.ceil((sent.resendAt - now.getTime()) / 1000)) : 0
  const { title, sub } = emailStepTitle(progress)

  /** 학교 메일 주소를 누르면 아이디 뒤에 붙인다. 아이디를 아직 안 썼으면 커서를 맨 앞에 둔다 */
  function pickDomain(domain: string) {
    const next = withEmailDomain(email, domain)
    setEmail(next)
    requestAnimationFrame(() => {
      const input = emailRef.current
      if (!input) return
      input.focus()
      const caret = next.startsWith('@') ? 0 : next.length
      input.setSelectionRange(caret, caret)
    })
  }

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
        finish(res) // 카카오: 이미 있는 계정(예전 회원, 카카오 계정 변경, 이메일 가입자)에 연결됐다
      } else if (res.signupToken) {
        onVerified({
          ...progress,
          signupToken: res.signupToken,
          email: res.email,
          schoolId: res.schoolId,
          schoolName: res.schoolName,
          existing: res.step === 'PASSWORD_REQUIRED',
        })
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
        <RestartLink via={progress.via} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-extrabold text-ink">{title}</h1>
        <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">{sub}</p>
      </div>

      <form onSubmit={sendCode} className="flex flex-col gap-3" noValidate>
        <Field label="학교 이메일">
          {({ id, describedBy }) => (
            <input
              id={id}
              ref={emailRef}
              type="text"
              inputMode="email"
              autoComplete="email"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              aria-describedby={describedBy}
              className={inputClass}
              placeholder="학교 메일 주소"
              value={email}
              disabled={Boolean(sent)}
              readOnly={fixedEmail}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </Field>
        {!sent && !fixedEmail && schools.length > 0 && (
          <div className="rounded-xl bg-surface-2 px-3 py-2.5">
            <p className="px-1 pb-1 text-[13px] text-ink-3">학교 메일 주소를 누르면 아이디 뒤에 붙어요</p>
            <ul className="flex flex-col gap-0.5 text-sm text-ink-2" aria-label="쓸 수 있는 학교 메일">
              {schools.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center gap-x-1 gap-y-0.5">
                  <span className="px-1">
                    <SchoolBadge school={s} />
                  </span>
                  {s.emailDomains.map((d) => (
                    <button
                      key={d.id}
                      type="button"
                      onClick={() => pickDomain(d.domain)}
                      aria-label={`@${d.domain} 붙이기`}
                      className="tabular min-h-9 rounded-lg px-2 text-left font-medium text-ink-2 transition-colors hover:bg-surface hover:text-accent-ink active:bg-surface"
                    >
                      @{d.domain}
                    </button>
                  ))}
                </li>
              ))}
            </ul>
          </div>
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
            {!fixedEmail && (
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
            )}
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

/** 새 비밀번호 + 확인 */
function PasswordFields({
  password,
  confirm,
  onPassword,
  onConfirm,
  label = '비밀번호',
}: {
  password: string
  confirm: string
  onPassword: (v: string) => void
  onConfirm: (v: string) => void
  label?: string
}) {
  return (
    <>
      <Field label={label} hint={`${PASSWORD_RULE} · 이메일로 로그인할 때 써요`}>
        {({ id, describedBy }) => (
          <input
            id={id}
            type="password"
            autoComplete="new-password"
            aria-describedby={describedBy}
            className={inputClass}
            value={password}
            onChange={(e) => onPassword(e.target.value)}
          />
        )}
      </Field>
      <Field label={`${label} 확인`}>
        {({ id }) => (
          <input
            id={id}
            type="password"
            autoComplete="new-password"
            className={inputClass}
            value={confirm}
            onChange={(e) => onConfirm(e.target.value)}
          />
        )}
      </Field>
    </>
  )
}

/** 이미 가입한 학교 이메일: 새 비밀번호를 정하면 그 계정으로 로그인 (비밀번호 찾기 · 카카오 가입자의 비밀번호 만들기) */
function PasswordStep({ progress }: { progress: SignupProgress }) {
  const finish = useFinishLogin()
  const toast = useToast()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const problem = passwordProblem(password, confirm)
    if (problem) return setError(problem)
    setError(null)
    setPending(true)
    try {
      const res = await api<AuthStep>('/api/auth/password', {
        method: 'POST',
        body: { signupToken: progress.signupToken, password },
      })
      toast.show('비밀번호를 정했어요. 이제 이메일로도 로그인할 수 있어요', 'ok')
      finish(res)
    } catch (err) {
      setExpired(expiredMessage(err))
      setError(err instanceof Error ? err.message : '비밀번호를 정하지 못했어요')
      setPending(false)
    }
  }

  if (expired) {
    return (
      <div className="flex flex-col gap-4">
        <FormError message={expired} />
        <RestartLink via={progress.via} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-extrabold text-ink">새 비밀번호를 정해요</h1>
        <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">
          <b className="text-ink">{progress.email}</b>
          {progress.purpose === 'password'
            ? ' 인증을 마쳤어요.'
            : ' 은 이미 가입된 메일이에요. 새로 가입하지 않고, 비밀번호를 정하면 그 계정으로 들어가요.'}
        </p>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
        <PasswordFields
          label="새 비밀번호"
          password={password}
          confirm={confirm}
          onPassword={setPassword}
          onConfirm={setConfirm}
        />
        <p className="text-[13px] leading-relaxed text-ink-3">카카오로 가입했어도 같은 계정이에요. 카카오 로그인도 그대로 돼요.</p>
        <FormError message={error} />
        <Button type="submit" size="lg" loading={pending} disabled={!password || !confirm}>
          비밀번호 정하고 들어가기
        </Button>
      </form>
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
          예전 기록과 이름·기수·학과가 그대로 이어져요.
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
  const byEmail = progress.via === 'email'
  const [name, setName] = useState('')
  const [cohort, setCohort] = useState('')
  const [departmentId, setDepartmentId] = useState<number | null>(null)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (byEmail) {
      const problem = passwordProblem(password, confirm)
      if (problem) return setError(problem)
    }
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
            ...(byEmail ? { password } : {}),
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
        <RestartLink via={progress.via} />
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
          {progress.purpose === 'password' && ' 이 메일로 가입한 계정이 없어서, 여기서 새로 가입해요.'}
        </p>
      </div>
      {!byEmail && progress.schoolId !== null && options.data?.legacySchoolIds.includes(progress.schoolId) && (
        <LegacyClaim progress={progress} onExpired={setExpired} />
      )}
      {/* 예전 계정 잇기는 카카오로만 된다 — 여기서 새로 가입하면 예전 계정과 따로 생긴다 */}
      {byEmail && progress.schoolId !== null && options.data?.legacySchoolIds.includes(progress.schoolId) && (
        <p className="flex gap-2 rounded-xl bg-surface-2 px-4 py-3 text-sm leading-relaxed text-ink-2">
          <History className="mt-0.5 size-4 shrink-0 text-ink-3" aria-hidden />
          <span>
            예전에 아이디·비밀번호로 쓰던 원우라면 여기서 새로 가입하지 말고,{' '}
            <Link to="/welcome" onClick={clearSignupProgress} className="font-semibold text-accent-ink underline underline-offset-4">
              카카오로 시작
            </Link>
            해서 예전 계정을 이어 주세요.
          </span>
        </p>
      )}
      <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
        <Field label="이름" hint="실명으로 · 성사된 3교시 참여자에게만 보여요">
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
        {byEmail && (
          <PasswordFields password={password} confirm={confirm} onPassword={setPassword} onConfirm={setConfirm} />
        )}
        <p className="rounded-xl bg-exit/25 px-4 py-3 text-sm leading-relaxed text-ink">
          <b>이름·기수·학과는 가입 후 직접 못 바꿔요.</b>
          <br />
          수정은 교수·대학원장에게 요청해 주세요.
        </p>
        <TermsCheckbox checked={agree} onChange={setAgree} />
        <FormError message={error} />
        <Button
          type="submit"
          size="lg"
          loading={pending}
          disabled={!name.trim() || !cohort || !departmentId || !agree || (byEmail && (!password || !confirm))}
        >
          가입하고 시작하기
        </Button>
      </form>
    </div>
  )
}
