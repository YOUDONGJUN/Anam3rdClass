import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ChevronLeft, X } from 'lucide-react'
import { useAgreeTerms, useAuthConfig } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { Brand } from '../components/Brand'
import { Button, FormError } from '../components/ui'

// 이용약관 · 개인정보 처리방침. 학생이 비영리로 운영하는 비공식 커뮤니티 기준으로 쓴 초안이다 —
// 운영자가 읽어 보고 고친다. 고치면 TERMS_UPDATED 를 바꾼다.
export const TERMS_UPDATED = '2026년 11월 26일'

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
          onClick={() => ((window.history.state?.idx ?? 0) > 0 ? navigate(-1) : navigate('/'))}
          className="mb-4 -ml-2 inline-flex h-10 items-center gap-1 rounded-lg px-2 text-[15px] font-semibold text-ink-2 hover:text-ink"
        >
          <ChevronLeft className="size-5" aria-hidden />
          뒤로
        </button>
        <article className="rounded-3xl border border-line bg-surface px-5 py-7 sm:px-8">
          <h1 className="text-2xl font-extrabold tracking-[-0.02em] text-ink">{title}</h1>
          <p className="mt-1 text-sm text-ink-3">{TERMS_UPDATED}부터 적용</p>
          <DocBody>{children}</DocBody>
        </article>
      </main>
    </div>
  )
}

function DocBody({ children }: { children: ReactNode }) {
  return (
    <div className="mt-6 flex flex-col gap-6 text-[15px] leading-relaxed text-ink-2 [&_h2]:mb-2 [&_h2]:text-[17px] [&_h2]:font-extrabold [&_h2]:text-ink [&_li]:ml-5 [&_li]:list-disc [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-1">
      {children}
    </div>
  )
}

export function TermsPage() {
  return (
    <DocFrame title="이용약관">
      <TermsBody />
    </DocFrame>
  )
}

function TermsBody() {
  return (
    <>
      <section>
        <h2>1. 어떤 서비스인가요</h2>
        <p>
          안암3교시는 IT연합회(고려대·연세대·성균관대·서강대·한양대) 대학원 원우가 수업 끝나고 모이는 번개(3교시)를 열고
          참여하는 커뮤니티예요. 학생들이 만든 써클이 돈을 받지 않고 운영하는 비공식 서비스이고, 학교 공식 서비스가 아니에요.
        </p>
      </section>
      <section>
        <h2>2. 가입</h2>
        <ul>
          <li>카카오 또는 이메일로 시작하고, IT연합회 학교 이메일로 인증해야 가입할 수 있어요. 학교 이메일 하나에 계정은 하나예요.</li>
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
        <p>
          정산은 얼마씩 나눌지와 누가 보냈는지만 함께 적는 기능이에요. 서비스는 돈을 받거나 보내지 않아요. 송금은 각자
          토스·카카오페이 같은 앱에서 하고, 돈을 주고받으며 생긴 일은 당사자끼리 해결해야 해요.
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
    </>
  )
}

export function PrivacyPage() {
  return (
    <DocFrame title="개인정보 처리방침">
      <PrivacyBody />
    </DocFrame>
  )
}

function PrivacyBody() {
  return (
    <>
      <section>
        <h2>1. 모으는 정보</h2>
        <ul>
          <li>카카오 회원번호 — 카카오로 로그인할 때만 써요. 카카오 닉네임·프로필 사진·전화번호는 받지 않아요.</li>
          <li>비밀번호 — 이메일로 로그인할 때만 써요. 알아볼 수 없게 바꿔서(암호화) 저장하고, 운영진도 볼 수 없어요.</li>
          <li>학교 이메일 — IT연합회 원우인지 확인하고, 이메일 로그인·비밀번호 찾기와 카카오 계정이 바뀌었을 때 계정을 되찾는 데 써요.</li>
          <li>이름, 기수, 학과, 학교</li>
          <li>서비스 이용 기록 — 연·참여한 3교시, 쓴 공지, 신고·차단 기록</li>
          <li>앱 알림 토큰 — 안드로이드 앱에서 알림을 받을 때만, 그 폰으로 알림을 보내는 데 써요. 받을 알림 종류 설정도 함께 저장해요.</li>
          <li>송금 링크(선택) — 토스 아이디, 카카오페이 송금 링크. 내가 계산한 정산에서 송금 버튼을 보여 주는 데만 써요. 계좌번호는 받지 않아요.</li>
          <li>정산 기록 — 총액, 1인 금액, 누가 보냈다고 표시했는지</li>
        </ul>
      </section>
      <section>
        <h2>2. 누구에게 보이나요</h2>
        <ul>
          <li>다른 사람에게 이름은 가운데가 가려진 채로(유*준) 보여요. 성사된 3교시의 참여자끼리만 실명이 보여요. 교수(우리 학교)와 대학원장은 관리를 위해 관리 화면에서 실명을 볼 수 있어요.</li>
          <li>기수·학과·학교는 이름 옆에 함께 보여요.</li>
          <li>랭킹에는 가려진 이름과 학교·학과, 성사된 3교시를 열고 탄 횟수가 보여요. 내 정보에서 "랭킹에 안 나오기"를 켜면 빠져요.</li>
          <li>이메일은 본인과 대학원장에게만 보이고, 교수에게는 가려져서(ho**@…) 보여요.</li>
          <li>정산과 송금 링크는 그 3교시를 함께한 원우끼리만 보여요. 교수와 대학원장에게도 보이지 않아요.</li>
        </ul>
      </section>
      <section>
        <h2>3. 얼마나 보관하나요</h2>
        <ul>
          <li>탈퇴하면 바로 이름·이메일·카카오 회원번호·비밀번호·학과·송금 링크를 지워요. 함께했던 3교시·공지·정산에는 "탈퇴한 원우"로만 남아요.</li>
          <li>정산 기록은 그 3교시 기록과 함께 보관하고, 3교시를 지우면 함께 지워요.</li>
          <li>이메일 인증코드 기록은 하루가 지나면 지워요.</li>
          <li>앱 알림 토큰은 로그아웃하거나 탈퇴하면, 앱을 지워서 더는 쓸 수 없게 되면 지워요.</li>
        </ul>
      </section>
      <section>
        <h2>4. 맡기는 곳</h2>
        <p>서비스를 돌리려고 아래 회사의 서비스를 써요. 서버와 데이터베이스는 해외에 있어요.</p>
        <ul>
          <li>Render — 서버</li>
          <li>Neon — 데이터베이스</li>
          <li>Google(Gmail) — 인증코드 메일 보내기</li>
          <li>Google(Firebase) — 앱 알림 보내기</li>
          <li>Kakao — 로그인</li>
        </ul>
      </section>
      <section>
        <h2>5. 앱 권한</h2>
        <ul>
          <li>알림(선택) — 참여·성사·시간 변경·공지·정산 알림을 받을 때만 써요. 허용하지 않아도 앱을 쓸 수 있어요.</li>
          <li>카메라·위치·연락처·사진은 쓰지 않아요.</li>
        </ul>
      </section>
      <section>
        <h2>6. 내 정보에 대한 권리</h2>
        <ul>
          <li>내 정보 화면에서 언제든 확인할 수 있어요.</li>
          <li>학과·기수는 내 정보에서 직접 고쳐요 (원우는 30일에 한 번). 이름은 학교 이메일로 본인 인증을 해서 바꿀 수 없고, 고쳐야 하면 교수나 대학원장에게 요청해 주세요.</li>
          <li>송금 링크는 내 정보에서 언제든 바꾸거나 지울 수 있어요.</li>
          <li>탈퇴하면 위 3번처럼 지워져요.</li>
        </ul>
      </section>
      <section id="delete-account">
        <h2>7. 계정 지우기(탈퇴)</h2>
        <ul>
          <li>앱과 웹 모두 내 정보 › 탈퇴하기에서 바로 지울 수 있어요. 지우는 내용은 위 3번과 같아요.</li>
          <li>로그인할 수 없어서 직접 지울 수 없으면 아래 문의로 요청해 주세요. 본인 확인 뒤 지워 드려요.</li>
        </ul>
      </section>
      <section>
        <h2>8. 운영 · 개인정보 보호책임자</h2>
        <ul>
          <li>운영: 써클</li>
          <li>개인정보 보호책임자: 유동준</li>
        </ul>
        <p className="mt-2">
          개인정보에 대해 궁금한 점은 <Contact />
        </p>
      </section>
    </>
  )
}

/** 가입 중에 약관을 읽는 창 (화면을 떠나지 않아서 쓰던 내용이 그대로 남는다 — 앱에서도) */
function DocDialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-label={title}
      className="m-auto max-h-[85dvh] w-[min(40rem,calc(100vw-2rem))] overflow-y-auto overscroll-contain rounded-3xl bg-surface p-0 text-left shadow-2xl backdrop:bg-black/50"
    >
      <div className="sticky top-0 flex items-center justify-between gap-3 border-b border-line bg-surface px-5 py-3">
        <h2 className="text-lg font-extrabold text-ink">{title}</h2>
        <button
          type="button"
          onClick={() => ref.current?.close()}
          className="inline-flex size-10 items-center justify-center rounded-full text-ink-2 hover:bg-surface-2 hover:text-ink"
          aria-label="닫기"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>
      <div className="px-5 pb-7">
        <p className="mt-3 text-sm text-ink-3">{TERMS_UPDATED}부터 적용</p>
        <DocBody>{children}</DocBody>
      </div>
    </dialog>
  )
}

/** 가입 화면·약관 동의 화면의 체크 */
export function TermsCheckbox({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  const [reading, setReading] = useState<'terms' | 'privacy' | null>(null)
  const linkClass = 'font-semibold text-accent-ink underline underline-offset-4'
  return (
    <>
      <div className="flex items-start gap-3 rounded-xl border border-line-strong px-4 py-3 text-[15px] text-ink has-focus-visible:ring-2 has-focus-visible:ring-accent">
        <input
          id="agree-terms"
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          className="mt-0.5 size-5 shrink-0 cursor-pointer accent-[var(--accent)]"
        />
        <p className="leading-relaxed">
          <label htmlFor="agree-terms" className="cursor-pointer">
            <b>(필수)</b>{' '}
          </label>
          <button type="button" className={linkClass} onClick={() => setReading('terms')}>
            이용약관
          </button>
          과{' '}
          <button type="button" className={linkClass} onClick={() => setReading('privacy')}>
            개인정보 처리방침
          </button>
          <label htmlFor="agree-terms" className="cursor-pointer">
            에 동의해요. 욕설·괴롭힘 같은 글과 행동은 허용되지 않아요.
          </label>
        </p>
      </div>
      {reading === 'terms' && (
        <DocDialog title="이용약관" onClose={() => setReading(null)}>
          <TermsBody />
        </DocDialog>
      )}
      {reading === 'privacy' && (
        <DocDialog title="개인정보 처리방침" onClose={() => setReading(null)}>
          <PrivacyBody />
        </DocDialog>
      )}
    </>
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
