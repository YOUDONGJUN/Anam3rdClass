import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { api, ApiError } from '../api/client'
import type { AuthResponse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Button, Field, FormError, Segmented } from '../components/ui'
import { inputClass } from '../components/styles'
import { kstParts } from '../lib/time'

function useAfterAuth() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  return (res: AuthResponse) => {
    signIn(res)
    navigate(from, { replace: true })
  }
}

function LoginForm() {
  const done = useAfterAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      done(await api<AuthResponse>('/api/auth/login', { method: 'POST', body: { loginId, password } }))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인하지 못했어요')
      setPending(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <Field label="아이디">
        {({ id }) => (
          <input
            id={id}
            className={inputClass}
            autoComplete="username"
            autoCapitalize="none"
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            required
          />
        )}
      </Field>
      <Field label="비밀번호">
        {({ id }) => (
          <input
            id={id}
            type="password"
            className={inputClass}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        )}
      </Field>
      <FormError message={error} />
      <Button type="submit" size="lg" loading={pending} disabled={!loginId || !password}>
        로그인
      </Button>
      <p className="text-center text-sm text-ink-3">비밀번호를 잊었다면 운영자에게 임시 비밀번호를 요청해 주세요.</p>
    </form>
  )
}

function SignupForm() {
  const done = useAfterAuth()
  const thisYear = kstParts(new Date()).year
  const years = Array.from({ length: 12 }, (_, i) => thisYear + (kstParts(new Date()).month >= 11 ? 1 : 0) - i)

  const [form, setForm] = useState({ inviteCode: '', name: '', admissionYear: String(thisYear), loginId: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      done(
        await api<AuthResponse>('/api/auth/signup', {
          method: 'POST',
          body: { ...form, admissionYear: Number(form.admissionYear) },
        }),
      )
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '가입하지 못했어요')
      setPending(false)
    }
  }

  const ready = form.inviteCode && form.name && form.loginId && form.password.length >= 8

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <Field label="입장 코드" hint="단톡방 공지에 있는 코드를 넣어 주세요. 우리 대학원 사람만 들어올 수 있어요.">
        {({ id, describedBy }) => (
          <input
            id={id}
            aria-describedby={describedBy}
            className={inputClass}
            autoCapitalize="none"
            autoComplete="off"
            value={form.inviteCode}
            onChange={set('inviteCode')}
          />
        )}
      </Field>
      <div className="grid grid-cols-[1fr_8.5rem] gap-3">
        <Field label="이름">
          {({ id }) => (
            <input id={id} className={inputClass} autoComplete="name" value={form.name} onChange={set('name')} />
          )}
        </Field>
        <Field label="학번">
          {({ id }) => (
            <select id={id} className={inputClass} value={form.admissionYear} onChange={set('admissionYear')}>
              {years.map((y) => (
                <option key={y} value={y}>
                  {String(y % 100).padStart(2, '0')}학번
                </option>
              ))}
            </select>
          )}
        </Field>
      </div>
      <p className="-mt-2 rounded-xl bg-surface-2 px-4 py-3 text-sm leading-relaxed text-ink-2">
        다른 사람에게 이름은 <b className="text-ink">김*수 · 24학번</b>처럼 가려져 보여요. 번개가 성사되면 함께하는 사람끼리만
        실명이 보여요.
      </p>
      <Field label="아이디" hint="영문·숫자·_ 4~20자">
        {({ id, describedBy }) => (
          <input
            id={id}
            aria-describedby={describedBy}
            className={inputClass}
            autoComplete="username"
            autoCapitalize="none"
            value={form.loginId}
            onChange={set('loginId')}
          />
        )}
      </Field>
      <Field label="비밀번호" hint="8자 이상">
        {({ id, describedBy }) => (
          <input
            id={id}
            type="password"
            aria-describedby={describedBy}
            className={inputClass}
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
          />
        )}
      </Field>
      <FormError message={error} />
      <Button type="submit" size="lg" loading={pending} disabled={!ready}>
        가입하고 시작하기
      </Button>
    </form>
  )
}

const STEPS = [
  { title: '번개를 열어요', body: '시간, 장소, 최소 인원만 정하면 끝. 이름은 가운데가 가려져요.' },
  { title: '사람이 모여요', body: '누가 왔는지는 몰라도 몇 명 모였는지는 실시간으로 보여요.' },
  { title: '성사되면 공개', body: '최소 인원이 차면 참여자끼리만 실명과 오픈채팅 링크가 열려요. 안 모이면 조용히 사라져요.' },
]

export function WelcomePage() {
  const [tab, setTab] = useState<'login' | 'signup'>('login')

  return (
    <div className="min-h-dvh bg-bg">
      <div className="mx-auto grid max-w-6xl gap-0 lg:min-h-dvh lg:grid-cols-[1.1fr_1fr] lg:items-center lg:gap-16 lg:px-10 lg:py-12">
        {/* 역 이름판 */}
        <section className="flex flex-col">
          <div className="overflow-hidden bg-board text-on-board pt-[env(safe-area-inset-top)] lg:rounded-3xl">
            <div className="px-6 pt-10 pb-7 sm:px-10 lg:pt-14">
              <h1 className="text-[56px] leading-[0.95] font-black tracking-[-0.04em] sm:text-7xl">안암3교시</h1>
              <p className="mt-3 text-base font-medium tracking-wide text-on-board-2">Anam 3rd Period</p>
            </div>
            <div className="h-3 bg-accent" aria-hidden />
            <div className="flex items-center justify-between px-6 py-3.5 text-sm font-semibold text-on-board-2 sm:px-10">
              <span>← 2교시 · 수업</span>
              <span>귀가 · 막차 →</span>
            </div>
          </div>

          <div className="hidden px-6 pt-10 sm:px-10 lg:block lg:px-0">
            <p className="text-xl leading-relaxed font-bold text-ink">
              수업 끝나고 한잔하고 싶은데,
              <br />
              먼저 말 꺼내기 민망했죠?
            </p>
            <Steps />
          </div>
        </section>

        {/* 로그인 / 가입 */}
        <section className="px-4 py-8 sm:px-10 lg:px-0 lg:py-0">
          <div className="mx-auto max-w-md rounded-3xl border border-line bg-surface p-6 shadow-[0_12px_40px_-16px_rgb(0_0_0/0.18)] sm:p-8">
            <p className="mb-1 text-lg font-bold text-ink">고려대 SW·AI융합대학원 번개 모임</p>
            <p className="mb-6 text-[15px] text-ink-2">동기·선후배끼리만 쓰는 공간이에요.</p>
            <Segmented
              label="로그인 또는 가입"
              value={tab}
              onChange={setTab}
              options={[
                { value: 'login', label: '로그인' },
                { value: 'signup', label: '처음이에요' },
              ]}
            />
            <div className="mt-6">{tab === 'login' ? <LoginForm /> : <SignupForm />}</div>
          </div>

          <div className="mx-auto max-w-md px-2 pt-10 lg:hidden">
            <p className="text-lg leading-relaxed font-bold text-ink">
              수업 끝나고 한잔하고 싶은데, 먼저 말 꺼내기 민망했죠?
            </p>
            <Steps />
          </div>
          <p className="mx-auto mt-10 max-w-md px-2 text-center text-xs text-ink-3">
            학생들이 만든 비공식 커뮤니티예요. 학교 공식 서비스가 아니에요.
          </p>
        </section>
      </div>
    </div>
  )
}

/** 3단계를 노선도처럼 */
function Steps() {
  return (
    <ol className="mt-6">
      {STEPS.map((s, i) => (
        <li key={s.title} className="relative flex gap-4 pb-6 last:pb-0">
          {i < STEPS.length - 1 && (
            <span aria-hidden className="absolute top-3 left-[11px] h-full w-1 bg-accent" />
          )}
          <span
            aria-hidden
            className={`relative z-10 mt-0.5 shrink-0 rounded-full ${
              i === STEPS.length - 1
                ? 'size-6 border-4 border-ok bg-surface shadow-[inset_0_0_0_3px_var(--surface),inset_0_0_0_10px_var(--ok)]'
                : 'size-6 border-4 border-accent bg-accent'
            }`}
          />
          <div>
            <p className="font-bold text-ink">{s.title}</p>
            <p className="mt-1 text-[15px] leading-relaxed text-ink-2">{s.body}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
