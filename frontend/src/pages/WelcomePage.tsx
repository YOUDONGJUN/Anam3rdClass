import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { EyeOff, MailCheck, ShieldCheck } from 'lucide-react'
import { api, ApiError } from '../api/client'
import { useAuthConfig, useDevMembers, useSignupOptions } from '../api/queries'
import type { AuthStep, DevMember } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { RoleTag } from '../components/PersonName'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, FormError, Spinner } from '../components/ui'
import { startKakaoLogin } from '../lib/kakao'
import { affiliation } from '../lib/person'

/** 카카오 심볼 (말풍선) */
function KakaoSymbol() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" aria-hidden>
      <path
        fill="currentColor"
        d="M12 3C6.48 3 2 6.5 2 10.82c0 2.78 1.86 5.22 4.66 6.6l-.95 3.48c-.08.3.26.54.52.37l4.13-2.74c.54.06 1.08.1 1.64.1 5.52 0 10-3.5 10-7.81C22 6.5 17.52 3 12 3Z"
      />
    </svg>
  )
}

function KakaoLoginButton({ from }: { from: string }) {
  const config = useAuthConfig()
  const clientId = config.data?.kakaoClientId ?? null
  const [leaving, setLeaving] = useState(false)

  if (config.isPending) return <Spinner label="불러오는 중" />
  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        disabled={!clientId || leaving}
        onClick={() => {
          if (!clientId) return
          setLeaving(true)
          startKakaoLogin(clientId, from)
        }}
        className="inline-flex h-13 w-full items-center justify-center gap-2 rounded-xl bg-kakao text-base font-semibold text-on-kakao transition-[filter,transform] select-none hover:brightness-95 active:scale-[0.98] disabled:opacity-50"
      >
        <KakaoSymbol />
        {leaving ? '카카오로 이동하는 중…' : '카카오로 시작하기'}
      </button>
      {config.isError && <FormError message="서버에 연결하지 못했어요. 새로고침해 주세요" />}
      {config.data && !clientId &&
        (config.data.devLogin ? (
          // 로컬 개발 서버: 키 없이도 아래 개발용 로그인으로 들어갈 수 있다
          <p className="text-center text-[13px] text-ink-3">카카오 키가 없어서 아래 개발용 로그인을 써요</p>
        ) : (
          <FormError message="카카오 로그인 준비 중이에요. 관리자에게 알려 주세요" />
        ))}
    </div>
  )
}

/** 로컬 개발 서버에서만 보이는 샘플 계정 로그인 */
function DevLogin({ from }: { from: string }) {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const members = useDevMembers(true)
  const [error, setError] = useState<string | null>(null)
  const [pendingId, setPendingId] = useState<number | null>(null)

  async function login(m: DevMember) {
    setError(null)
    setPendingId(m.id)
    try {
      const res = await api<AuthStep>('/api/auth/dev/login', { method: 'POST', body: { memberId: m.id } })
      if (res.token && res.member) {
        signIn({ token: res.token, member: res.member })
        navigate(from, { replace: true })
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '로그인하지 못했어요')
      setPendingId(null)
    }
  }

  return (
    <section className="mt-6 rounded-2xl border border-dashed border-line-strong px-4 py-4">
      <p className="text-sm font-bold text-ink">개발용 로그인</p>
      <p className="mt-0.5 text-[13px] text-ink-3">로컬 서버 전용 · 샘플 계정</p>
      <div className="mt-3 flex flex-col gap-2">
        {(members.data ?? []).map((m) => (
          <Button
            key={m.id}
            variant="secondary"
            size="sm"
            loading={pendingId === m.id}
            onClick={() => login(m)}
            className="justify-start!"
          >
            <span className="font-bold">{m.name}</span>
            <SchoolBadge school={m.school} />
            {m.role !== 'USER' && <RoleTag role={m.role} />}
            <span className="text-ink-3">{affiliation(m.cohortLabel, m.departmentLabel)}</span>
          </Button>
        ))}
      </div>
      <div className="mt-3">
        <FormError message={error} />
      </div>
    </section>
  )
}

/** 카드 안 짧은 안내: 아이콘 + 한 줄 */
const PROMISES = [
  { Icon: MailCheck, text: '학교 메일로 한 번만 인증' },
  { Icon: EyeOff, text: <>이름은 <b className="font-semibold text-ink">김*대</b>처럼 가려져요</> },
  { Icon: ShieldCheck, text: 'IT연합회 원우만 들어와요' },
]

const STEPS = [
  { title: '3교시를 열어요', body: '시간·장소·인원만 정하면 끝' },
  { title: '사람이 모여요', body: '몇 명 모였는지 바로 보여요' },
  { title: '성사되면 공개', body: '참여자끼리 실명·단톡방 공개' },
]

export function WelcomePage() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const config = useAuthConfig()
  const options = useSignupOptions()
  const schools = options.data?.schools ?? []

  return (
    <div className="min-h-dvh bg-bg lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      {/* 역 이름판 — 모바일은 위, 데스크톱은 왼쪽 절반을 꽉 채운다 */}
      <section className="flex flex-col bg-board pt-[env(safe-area-inset-top)] text-on-board lg:sticky lg:top-0 lg:h-dvh">
        <div className="flex-1 px-6 pt-10 pb-8 sm:px-10 lg:flex lg:flex-col lg:justify-center lg:px-16 lg:pb-12">
          <p className="text-sm font-semibold text-on-board-2">IT연합회 대학원 번개</p>
          <h1 className="mt-2 text-[52px] leading-[0.95] font-black tracking-[-0.04em] sm:text-7xl xl:text-8xl">안암3교시</h1>
          <p className="mt-5 text-xl leading-snug font-bold lg:text-2xl">
            수업 끝나고,
            <br />
            한잔할 사람?
          </p>
          <div className="mt-12 hidden lg:block">
            <Steps onBoard />
          </div>
        </div>
        <div>
          <div className="h-3 bg-accent" aria-hidden />
          <div className="flex items-center justify-between px-6 py-3.5 text-sm font-semibold text-on-board-2 sm:px-10 lg:px-16">
            <span>← 1·2교시 · 수업</span>
            <span>귀가 · 막차 →</span>
          </div>
        </div>
      </section>

      {/* 로그인 */}
      <section className="flex flex-col px-4 pt-6 pb-10 sm:px-10 lg:min-h-dvh lg:justify-center lg:px-16 lg:py-12">
        <div className="mx-auto w-full max-w-sm">
          <div className="rounded-3xl border border-line bg-surface p-6 shadow-[0_12px_40px_-16px_rgb(0_0_0/0.18)] sm:p-7">
            <h2 className="text-xl font-extrabold tracking-[-0.02em] text-ink">다섯 학교 대학원 원우끼리</h2>
            {schools.length > 0 && (
              <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="함께하는 학교">
                {schools.map((s) => (
                  <li key={s.id}>
                    <SchoolBadge school={s} />
                  </li>
                ))}
              </ul>
            )}
            <div className="mt-6">
              <KakaoLoginButton from={from} />
            </div>
            <ul className="mt-5 flex flex-col gap-2.5 border-t border-line pt-5">
              {PROMISES.map(({ Icon, text }, i) => (
                <li key={i} className="flex items-center gap-3 text-[15px] text-ink-2">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-surface-2 text-ink-3">
                    <Icon className="size-4" aria-hidden />
                  </span>
                  <span>{text}</span>
                </li>
              ))}
            </ul>
            {config.data?.devLogin && <DevLogin from={from} />}
          </div>

          <div className="mt-10 px-2 lg:hidden">
            <Steps />
          </div>

          <p className="mt-10 px-2 text-center text-xs leading-relaxed text-ink-3">
            학생들이 만든 비공식 커뮤니티예요
            <br />
            <Link to="/terms" className="underline underline-offset-4 hover:text-ink">
              이용약관
            </Link>
            {' · '}
            <Link to="/privacy" className="underline underline-offset-4 hover:text-ink">
              개인정보 처리방침
            </Link>
          </p>
        </div>
      </section>
    </div>
  )
}

/** 3단계를 노선도처럼. onBoard 면 안내판(진한 바탕) 위 색 */
function Steps({ onBoard = false }: { onBoard?: boolean }) {
  const title = onBoard ? 'text-on-board' : 'text-ink'
  const body = onBoard ? 'text-on-board-2' : 'text-ink-2'
  const ring = onBoard ? 'var(--board)' : 'var(--bg)'
  return (
    <ol>
      {STEPS.map((s, i) => {
        const last = i === STEPS.length - 1
        return (
          <li key={s.title} className="relative flex gap-4 pb-7 last:pb-0">
            {!last && (
              // 역(24px) 중심 12px 에 4px 선의 가운데를 맞춘다
              <span aria-hidden className="absolute top-3 left-2.5 h-full w-1 bg-accent" />
            )}
            <span
              aria-hidden
              className={`relative z-10 mt-0.5 size-6 shrink-0 rounded-full border-4 ${last ? 'border-ok' : 'border-accent bg-accent'}`}
              style={last ? { backgroundColor: ring, boxShadow: `inset 0 0 0 3px ${ring}, inset 0 0 0 10px var(--ok)` } : undefined}
            />
            <div className="min-w-0">
              <p className={`font-bold ${title}`}>{s.title}</p>
              <p className={`mt-0.5 text-[15px] ${body}`}>{s.body}</p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
