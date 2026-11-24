import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { ChevronLeft } from 'lucide-react'
import { useAgreeTerms, useAuthConfig } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { Brand } from '../components/Brand'
import { Button, FormError } from '../components/ui'

// 이용약관 · 개인정보 처리방침. 학생이 비영리로 운영하는 비공식 커뮤니티 기준으로 쓴 초안이다 —
// 운영자가 읽어 보고 고친다. 고치면 TERMS_UPDATED 를 바꾼다.
export const TERMS_UPDATED = '2026년 11월 24일'

function Contact() {
  const config = useAuthConfig()
  const email = config.data?.supportEmail
  return email ? (
    <>
      <a href={`mailto:${email}`} className="font-semibold text-accent-ink underline underline-offset-4">
        {email}
      </a>
      로 메일을 보내 주세요.
    </>
  ) : (
    <>우리 학교 교수(관리자)나 대학원장에게 알려 주세요.</>
  )
}

function DocFrame({ title, children }: { title: string; children: ReactNode }) {
  const navigate = useNavigate()
  return (
    <div className="min-h-dvh bg-bg">
      <header className="bg-board pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex h-14 max-w-2xl items-center px-4">
          <Brand />
        </div>
        <div className="h-1 bg-accent" aria-hidden />
      </header>
      <main className="mx-auto max-w-2xl px-4 py-8">
        <button
          type="button"
          onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}
          className="mb-4 -ml-2 inline-flex h-10 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
        >
          <ChevronLeft className="size-5" aria-hidden />
          뒤로
        </button>
        <article className="rounded-3xl border border-line bg-surface px-5 py-7 sm:px-8">
          <h1 className="text-2xl font-extrabold tracking-[-0.02em] text-ink">{title}</h1>
          <p className="mt-1 text-sm text-ink-3">{TERMS_UPDATED}부터 적용</p>
          <div className="mt-6 flex flex-col gap-6 text-[15px] leading-relaxed text-ink-2 [&_h2]:mb-2 [&_h2]:text-[17px] [&_h2]:font-extrabold [&_h2]:text-ink [&_li]:ml-5 [&_li]:list-disc [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-1">
            {children}
          </div>
        </article>
      </main>
    </div>
  )
}

export function TermsPage() {
  return (
    <DocFrame title="이용약관">
      <section>
        <h2>1. 어떤 서비스인가요</h2>
        <p>
          안암3교시는 IT연합회(고려대·연세대·성균관대·서강대·한양대) 대학원 원우가 수업 끝나고 모이는 번개(3교시)를 열고
          참여하는 커뮤니티예요. 학생들이 돈을 받지 않고 운영하는 비공식 서비스이고, 학교 공식 서비스가 아니에요.
        </p>
      </section>
      <section>
        <h2>2. 가입</h2>
        <ul>
          <li>카카오로 로그인하고, IT연합회 학교 이메일로 한 번 인증해야 가입할 수 있어요.</li>
          <li>한 사람은 한 계정만 쓰고, 이름은 실명으로 적어 주세요.</li>
          <li>운영은 대학원장(최고 관리자)과 학교마다 임명된 교수(관리자)가 맡아요.</li>
        </ul>
      </section>
      <section>
        <h2>3. 꼭 지켜 주세요</h2>
        <p>
          함께 쓰는 공간이에요. 아래 같은 글과 행동은 <b className="text-ink">조금도 허용하지 않아요.</b>
        </p>
        <ul>
          <li>욕설, 혐오·차별 표현, 괴롭힘, 스토킹</li>
          <li>성적인 내용이나 원하지 않는 연락</li>
          <li>음주 강요, 불법이거나 위험한 일</li>
          <li>스팸, 광고, 영업, 종교·정치 권유</li>
          <li>다른 사람의 개인정보(연락처, 사진 등)를 허락 없이 올리는 것, 다른 사람인 척하는 것</li>
        </ul>
      </section>
      <section>
        <h2>4. 신고 · 차단 · 이용 제한</h2>
        <ul>
          <li>3교시, 공지, 3교시에서 만난 사람을 신고할 수 있어요. 누가 신고했는지는 아무에게도 보이지 않아요.</li>
          <li>신고는 운영진이 빠르게 확인해서, 규칙을 어긴 글은 지우거나 3교시를 취소하고, 어긴 사람은 이용을 막아요.</li>
          <li>불편한 사람은 차단할 수 있어요. 서로가 연 3교시가 보이지 않고, 서로의 3교시에 참여할 수 없어요.</li>
          <li>심한 욕설 같은 표현은 올릴 때 자동으로 걸러져요.</li>
        </ul>
      </section>
      <section>
        <h2>5. 모임은 각자의 책임이에요</h2>
        <p>
          서비스는 모일 사람을 찾도록 돕기만 해요. 모임에서 생긴 일은 참여한 사람 각자에게 책임이 있어요. 술은 적당히,
          강요는 금지, 귀가는 안전하게 해 주세요.
        </p>
      </section>
      <section>
        <h2>6. 탈퇴 · 바뀌는 내용</h2>
        <ul>
          <li>내 정보 › 탈퇴하기에서 언제든 탈퇴할 수 있어요.</li>
          <li>약관이 바뀌면 공지로 먼저 알려 드려요.</li>
        </ul>
      </section>
      <section>
        <h2>7. 문의</h2>
        <p>
          궁금한 점이나 문제가 있으면 <Contact />
        </p>
      </section>
    </DocFrame>
  )
}

export function PrivacyPage() {
  return (
    <DocFrame title="개인정보 처리방침">
      <section>
        <h2>1. 모으는 정보</h2>
        <ul>
          <li>카카오 회원번호 — 카카오 로그인에 써요. 카카오 닉네임·프로필 사진·전화번호는 받지 않아요.</li>
          <li>학교 이메일 — IT연합회 원우인지 확인하고, 카카오 계정이 바뀌었을 때 계정을 되찾는 데 써요.</li>
          <li>이름, 기수, 학과, 학교</li>
          <li>서비스 이용 기록 — 연·참여한 3교시, 쓴 공지, 신고·차단 기록</li>
        </ul>
      </section>
      <section>
        <h2>2. 누구에게 보이나요</h2>
        <ul>
          <li>다른 사람에게 이름은 가운데가 가려진 채로(김*대) 보여요. 성사된 3교시의 참여자끼리만 실명이 보여요. 교수(우리 학교)와 대학원장은 관리를 위해 관리 화면에서 실명을 볼 수 있어요.</li>
          <li>기수·학과·학교는 이름 옆에 함께 보여요.</li>
          <li>이메일은 본인과 대학원장에게만 보이고, 교수에게는 가려져서(ho**@…) 보여요.</li>
        </ul>
      </section>
      <section>
        <h2>3. 얼마나 보관하나요</h2>
        <ul>
          <li>탈퇴하면 바로 이름·이메일·카카오 회원번호·학과를 지워요. 함께했던 3교시와 공지에는 "탈퇴한 원우"로만 남아요.</li>
          <li>이메일 인증코드 기록은 하루가 지나면 지워요.</li>
        </ul>
      </section>
      <section>
        <h2>4. 맡기는 곳</h2>
        <p>서비스를 돌리려고 아래 회사의 서비스를 써요. 서버와 데이터베이스는 해외에 있어요.</p>
        <ul>
          <li>Render — 서버</li>
          <li>Neon — 데이터베이스</li>
          <li>Google(Gmail) — 인증코드 메일 보내기</li>
          <li>Kakao — 로그인</li>
        </ul>
      </section>
      <section>
        <h2>5. 내 정보에 대한 권리</h2>
        <ul>
          <li>내 정보 화면에서 언제든 확인할 수 있어요.</li>
          <li>이름·기수·학과를 고쳐야 하면 교수나 대학원장에게 요청해 주세요.</li>
          <li>탈퇴하면 위 3번처럼 지워져요.</li>
        </ul>
      </section>
      <section>
        <h2>6. 문의</h2>
        <p>
          개인정보에 대해 궁금한 점은 <Contact />
        </p>
      </section>
    </DocFrame>
  )
}

/** 가입 화면·약관 동의 화면의 체크 */
export function TermsCheckbox({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-line-strong px-4 py-3 text-[15px] text-ink has-focus-visible:ring-2 has-focus-visible:ring-accent">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 size-5 shrink-0 accent-[var(--accent)]"
      />
      <span className="leading-relaxed">
        <b>(필수)</b>{' '}
        <Link to="/terms" target="_blank" className="font-semibold text-accent-ink underline underline-offset-4">
          이용약관
        </Link>
        과{' '}
        <Link to="/privacy" target="_blank" className="font-semibold text-accent-ink underline underline-offset-4">
          개인정보 처리방침
        </Link>
        에 동의해요. 욕설·괴롭힘 같은 글과 행동은 허용되지 않아요.
      </span>
    </label>
  )
}

/** 약관이 생기기 전에 가입한 원우는 처음 들어올 때 한 번 동의한다 */
export function TermsGate() {
  const { member, updateMember, signOut } = useAuth()
  const agree = useAgreeTerms()
  const [checked, setChecked] = useState(false)
  if (!member) return null
  return (
    <div className="min-h-dvh bg-bg">
      <header className="bg-board pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex h-14 max-w-md items-center px-4">
          <Brand />
        </div>
        <div className="h-1 bg-accent" aria-hidden />
      </header>
      <main className="mx-auto max-w-md px-4 py-8">
        <div className="flex flex-col gap-5 rounded-3xl border border-line bg-surface p-6 sm:p-8">
          <div>
            <h1 className="text-xl font-extrabold text-ink">{member.name}님, 약관에 동의해 주세요</h1>
            <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">
              이제 IT연합회 다섯 학교가 함께 써요. 서로 지킬 약속과 개인정보를 어떻게 다루는지 정리했어요.
            </p>
          </div>
          <ul className="flex flex-col gap-1.5 rounded-xl bg-surface-2 px-4 py-3 text-sm leading-relaxed text-ink-2">
            <li>• 욕설·괴롭힘·성적인 글은 허용되지 않아요. 신고되면 운영진이 확인해요.</li>
            <li>• 불편한 사람은 신고하거나 차단할 수 있어요.</li>
            <li>• 탈퇴하면 이름·이메일은 바로 지워요.</li>
          </ul>
          <TermsCheckbox checked={checked} onChange={setChecked} />
          <FormError message={agree.error?.message} />
          <Button
            size="lg"
            disabled={!checked}
            loading={agree.isPending}
            onClick={() => agree.mutate(undefined, { onSuccess: (m) => updateMember(m) })}
          >
            동의하고 계속하기
          </Button>
          <button type="button" onClick={signOut} className="text-sm font-semibold text-ink-3 underline underline-offset-4">
            로그아웃
          </button>
        </div>
      </main>
    </div>
  )
}
