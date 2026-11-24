import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
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
      {config.isError && <FormError message="서버에 연결하지 못했어요. 잠시 후 새로고침해 주세요" />}
      {config.data && !clientId &&
        (config.data.devLogin ? (
          // 로컬 개발 서버: 키 없이도 아래 개발용 로그인으로 들어갈 수 있다
          <p className="text-center text-[13px] text-ink-3">
            카카오 키(KAKAO_REST_API_KEY)를 넣고 서버를 켜면 카카오 로그인을 시험할 수 있어요
          </p>
        ) : (
          <FormError message="카카오 로그인이 아직 준비되지 않았어요. 관리자에게 알려 주세요" />
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
      <p className="mt-0.5 text-[13px] text-ink-3">로컬 서버에서만 보여요. 카카오·메일 없이 샘플 계정으로 들어가요.</p>
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

const STEPS = [
  { title: '3교시를 열어요', body: '시간, 장소, 인원만 정하면 끝. 이름은 가운데가 가려져요.' },
  { title: '사람이 모여요', body: '누가 왔는지는 몰라도 몇 명 모였는지는 실시간으로 보여요.' },
  { title: '성사되면 공개', body: '최소 인원이 차면 참여자끼리만 실명과 오픈채팅 링크가 열려요. 안 모이면 조용히 사라져요.' },
]

export function WelcomePage() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const config = useAuthConfig()
  const options = useSignupOptions()
  const schools = options.data?.schools ?? []

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
              <span>← 1·2교시 · 수업</span>
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

        {/* 로그인 */}
        <section className="px-4 py-8 sm:px-10 lg:px-0 lg:py-0">
          <div className="mx-auto max-w-md rounded-3xl border border-line bg-surface p-6 shadow-[0_12px_40px_-16px_rgb(0_0_0/0.18)] sm:p-8">
            <p className="mb-1 text-lg font-bold text-ink">IT연합회 대학원 번개 모임</p>
            <p className="mb-3 text-[15px] leading-relaxed text-ink-2">
              수업 끝나고 모이는 번개, 여기선 <b className="text-ink">3교시</b>라고 불러요. 우리 학교끼리도, 다섯 학교
              연합으로도 모여요.
            </p>
            {schools.length > 0 && (
              <ul className="mb-6 flex flex-wrap gap-1.5" aria-label="함께하는 학교">
                {schools.map((s) => (
                  <li key={s.id}>
                    <SchoolBadge school={s} />
                  </li>
                ))}
              </ul>
            )}
            <KakaoLoginButton from={from} />
            <p className="mt-4 rounded-xl bg-surface-2 px-4 py-3 text-sm leading-relaxed text-ink-2">
              처음이면 <b className="text-ink">학교 이메일</b>로 한 번만 인증해요. IT연합회 대학원 원우만 들어올 수
              있어요. 다른 사람에게 이름은 <b className="text-ink">김*대 · 61기 · 인융</b>처럼 가려져 보여요.
            </p>
            {config.data?.devLogin && <DevLogin from={from} />}
          </div>

          <div className="mx-auto max-w-md px-2 pt-10 lg:hidden">
            <p className="text-lg leading-relaxed font-bold text-ink">
              수업 끝나고 한잔하고 싶은데, 먼저 말 꺼내기 민망했죠?
            </p>
            <Steps />
          </div>
          <p className="mx-auto mt-10 max-w-md px-2 text-center text-xs leading-relaxed text-ink-3">
            학생들이 만든 비공식 커뮤니티예요. 학교 공식 서비스가 아니에요.
            <br />
            <Link to="/terms" className="underline underline-offset-4 hover:text-ink">
              이용약관
            </Link>
            {' · '}
            <Link to="/privacy" className="underline underline-offset-4 hover:text-ink">
              개인정보 처리방침
            </Link>
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
            // 역(24px) 중심 12px 에 4px 선의 가운데를 맞춘다
            <span aria-hidden className="absolute top-3 left-2.5 h-full w-1 bg-accent" />
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
