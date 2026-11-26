import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import {
  Check,
  ChevronRight,
  Crown,
  EyeOff,
  FileText,
  KeyRound,
  Lock,
  LogOut,
  Mail,
  Minus,
  Pencil,
  ShieldCheck,
  Trophy,
  Users,
  UserX,
  X,
} from 'lucide-react'
import {
  useAdminStats,
  useAuthConfig,
  useBlocks,
  usePayLinks,
  useRanking,
  useRankingSetting,
  useUnblock,
  useUpdateMyProfile,
  useWithdraw,
} from '../api/queries'
import type { Member } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AffiliationFields } from '../components/AffiliationFields'
import { MadeBy } from '../components/MadeBy'
import { PayLinksFields } from '../components/PayLinksFields'
import { RoleTag } from '../components/PersonName'
import { PushSettings } from '../components/PushSettings'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, Field, FormError, Segmented } from '../components/ui'
import { cardClass, inputClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import {
  affiliation,
  canEditOwnName,
  cohortLocked,
  isStaff,
  maskName,
  profileEditRule,
  profileLockedUntil,
} from '../lib/person'
import { normalizeKakaoPayUrl, normalizeTossId, payLinksError } from '../lib/settlement'
import { formatDate } from '../lib/time'
import { isThemedSchool, type ThemeColor, type ThemeMode } from '../lib/theme'
import { useTheme } from '../theme/ThemeContext'

/** 화면 색·밝기 (이 기기에만 저장) */
function ThemeSettings({ schoolName }: { schoolName: string }) {
  const theme = useTheme()
  const themed = isThemedSchool(theme.schoolCode)
  return (
    <section className={`${cardClass} flex flex-col gap-4 px-5 py-5`}>
      <div>
        <h2 className="text-[17px] font-extrabold text-ink">화면</h2>
        <p className="mt-0.5 text-sm text-ink-3">이 기기에만 저장돼요.</p>
      </div>
      {themed && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="text-[15px] font-semibold text-ink">색</span>
          <Segmented<ThemeColor>
            label="화면 색"
            value={theme.color}
            onChange={theme.setColor}
            options={[
              { value: 'school', label: `${schoolName} 색` },
              { value: 'basic', label: '기본' },
            ]}
          />
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-[15px] font-semibold text-ink">밝기</span>
        <Segmented<ThemeMode>
          label="화면 밝기"
          value={theme.mode}
          onChange={theme.setMode}
          options={[
            { value: 'system', label: '자동' },
            { value: 'light', label: '밝게' },
            { value: 'dark', label: '어둡게' },
          ]}
        />
      </div>
    </section>
  )
}

/** 내가 차단한 사람 (상대에게는 알리지 않는다) */
function BlockedPeople() {
  const { data } = useBlocks()
  const unblock = useUnblock()
  const toast = useToast()
  if (!data || data.length === 0) return null
  return (
    <section>
      <h2 className="mb-3 px-1 text-[17px] font-extrabold text-ink">차단한 사람</h2>
      <ul className={`${cardClass} divide-y divide-line overflow-hidden`}>
        {data.map((b) => (
          <li key={b.id} className="flex items-center justify-between gap-3 px-5 py-3">
            <span className="flex min-w-0 flex-wrap items-center gap-1.5 text-[15px] text-ink">
              <span className="font-semibold">{b.name}</span>
              <span className="text-ink-3">· {affiliation(b.cohortLabel, b.departmentLabel)}</span>
              <SchoolBadge school={b.school} />
            </span>
            <Button
              variant="secondary"
              size="sm"
              loading={unblock.isPending && unblock.variables === b.id}
              onClick={() =>
                unblock.mutate(b.id, {
                  onSuccess: () => toast.show('차단을 풀었어요', 'ok'),
                  onError: (e) => toast.show(e.message, 'error'),
                })
              }
            >
              풀기
            </Button>
          </li>
        ))}
      </ul>
    </section>
  )
}

const rowClass = 'flex h-14 w-full items-center gap-3 px-5 text-left text-[15px] font-semibold text-ink hover:bg-surface-2'

/** 로그인 방법 하나: 켜져 있으면 체크 */
function LoginMethod({ on, label }: { on: boolean; label: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[13px] font-semibold ${
        on ? 'bg-accent-soft text-accent-ink' : 'bg-surface-2 text-ink-3'
      }`}
    >
      {on ? <Check className="size-3.5" strokeWidth={3} aria-hidden /> : <Minus className="size-3.5" aria-hidden />}
      {label}
      <span className="sr-only">{on ? '(사용 중)' : '(안 씀)'}</span>
    </span>
  )
}

/**
 * 내 정보 고치기 (한 장). 학과·기수는 누구나 — 원우는 30일에 한 번, 교수·대학원장은 언제든.
 * 이름은 대학원장만 여기서 고치고, 교수·원우는 요청한다
 */
function ProfileSheet({ member, onClose }: { member: Member; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const save = useUpdateMyProfile()
  const toast = useToast()
  const [name, setName] = useState(member.name)
  const [cohort, setCohort] = useState(String(member.cohort))
  const [departmentId, setDepartmentId] = useState<number | null>(member.departmentId)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  const nameEditable = canEditOwnName(member.role)
  const lockedUntil = profileLockedUntil(member)
  const cohortFixed = cohortLocked(member)
  /** 원우가 직접 바꿀 수 있는 것 (가입한 날이 지나면 학과만) */
  const editableAffiliation = cohortFixed ? '학과는' : '학과·기수는'
  const affiliationChanged = Number(cohort) !== member.cohort || departmentId !== member.departmentId
  const changed = affiliationChanged || (nameEditable && name.trim() !== member.name)

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (nameEditable && !name.trim()) return setError('이름을 입력해 주세요')
    if (!cohort || !departmentId) return setError('기수와 학과를 골라 주세요')
    if (
      member.role === 'USER' &&
      affiliationChanged &&
      !window.confirm(`${editableAffiliation} 30일에 한 번 바꿀 수 있어요.\n이대로 바꿀까요?`)
    )
      return
    save.mutate(
      { name: nameEditable ? name.trim() : undefined, cohort: Number(cohort), departmentId },
      {
        onSuccess: () => {
          toast.show('내 정보를 고쳤어요', 'ok')
          ref.current?.close()
        },
        onError: (err) => setError(err.message),
      },
    )
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="profile-sheet-title"
      className="m-auto max-h-[88dvh] w-[min(30rem,calc(100%-2rem))] overflow-y-auto rounded-3xl border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="sticky top-0 z-10 flex items-center justify-between gap-3 border-b border-line bg-surface px-5 py-4">
        <h2 id="profile-sheet-title" className="text-lg font-extrabold">
          내 정보 고치기
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={() => ref.current?.close()}
          className="flex size-10 shrink-0 items-center justify-center rounded-lg text-ink-2 hover:bg-surface-2"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-5 px-5 py-5" noValidate>
        {nameEditable ? (
          <Field label="이름">
            {({ id }) => (
              <input id={id} className={inputClass} maxLength={20} value={name} onChange={(e) => setName(e.target.value)} />
            )}
          </Field>
        ) : (
          <div className="flex flex-col gap-1.5">
            <span className="text-[15px] font-semibold text-ink">이름</span>
            <p className="flex items-center gap-2 rounded-xl bg-surface-2 px-3.5 py-3 text-base text-ink">
              <Lock className="size-4 shrink-0 text-ink-3" aria-hidden />
              {member.name}
            </p>
            <p className="text-sm text-ink-3">
              학교 이메일로 본인 인증을 해서 이름은 바꿀 수 없어요.{' '}
              {member.role === 'MANAGER' ? '대학원장에게 요청해 주세요.' : '교수\u2060·\u2060대학원장에게 요청해 주세요.'}
            </p>
          </div>
        )}

        {lockedUntil ? (
          <div className="flex flex-col gap-1.5">
            <span className="text-[15px] font-semibold text-ink">기수 · 학과</span>
            <p className="flex items-center gap-2 rounded-xl bg-surface-2 px-3.5 py-3 text-base text-ink">
              <Lock className="size-4 shrink-0 text-ink-3" aria-hidden />
              {affiliation(member.cohortLabel, member.departmentLabel)}
            </p>
            <p className="text-sm text-ink-3">
              {editableAffiliation} 30일에 한 번 바꿀 수 있어요.{' '}
              <b className="font-semibold text-ink-2">{formatDate(lockedUntil)}</b>부터 다시 바꿀 수 있어요.
            </p>
          </div>
        ) : (
          <>
            <AffiliationFields
              schoolId={member.school.id}
              cohort={cohort}
              departmentId={departmentId}
              onCohortChange={setCohort}
              onDepartmentChange={setDepartmentId}
              cohortLocked={cohortFixed}
            />
            {member.role === 'USER' && (
              <p className="-mt-2 text-sm text-ink-3">{editableAffiliation} 30일에 한 번 바꿀 수 있어요.</p>
            )}
          </>
        )}

        <FormError message={error} />

        <div className="flex gap-2.5">
          <Button variant="secondary" className="flex-1" onClick={() => ref.current?.close()}>
            닫기
          </Button>
          {(nameEditable || !lockedUntil) && (
            <Button type="submit" className="flex-[2]" loading={save.isPending} disabled={!changed}>
              저장
            </Button>
          )}
        </div>
      </form>
    </dialog>
  )
}

/** 랭킹: 이번 달 내 기록 + 랭킹에 안 나오기 */
/** 송금 링크: 정산할 때 함께한 원우에게만 보인다. 계좌번호는 받지 않는다 */
function PayLinksCard({ member }: { member: Member }) {
  const save = usePayLinks()
  const toast = useToast()
  const [tossId, setTossId] = useState(member.tossId ?? '')
  const [kakaoPayUrl, setKakaoPayUrl] = useState(member.kakaoPayUrl ?? '')
  const [error, setError] = useState<string | null>(null)
  const changed = tossId.trim() !== (member.tossId ?? '') || kakaoPayUrl.trim() !== (member.kakaoPayUrl ?? '')

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const bad = payLinksError(tossId, kakaoPayUrl)
    if (bad) return setError(bad)
    save.mutate(
      { tossId: normalizeTossId(tossId) ?? '', kakaoPayUrl: normalizeKakaoPayUrl(kakaoPayUrl) ?? '' },
      {
        onSuccess: (m) => {
          setTossId(m.tossId ?? '')
          setKakaoPayUrl(m.kakaoPayUrl ?? '')
          toast.show(m.tossId || m.kakaoPayUrl ? '송금 링크를 저장했어요' : '송금 링크를 지웠어요', 'ok')
        },
        onError: (err) => setError(err.message),
      },
    )
  }

  return (
    <section id="pay-links" className={`${cardClass} scroll-mt-20 px-5 py-5`} aria-labelledby="pay-links-title">
      <h2 id="pay-links-title" className="text-[17px] font-extrabold text-ink">
        송금 링크
      </h2>
      <p className="mt-0.5 text-sm text-ink-3">
        내가 계산한 정산에서 함께한 원우에게만 보여요. 계좌번호는 받지 않아요.
      </p>
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4" noValidate>
        <PayLinksFields
          tossId={tossId}
          kakaoPayUrl={kakaoPayUrl}
          onTossIdChange={setTossId}
          onKakaoPayUrlChange={setKakaoPayUrl}
        />
        <FormError message={error} />
        <Button type="submit" variant="secondary" disabled={!changed} loading={save.isPending}>
          저장
        </Button>
      </form>
    </section>
  )
}

function RankingCard({ member }: { member: Member }) {
  const ranking = useRanking('month', 'all')
  const setting = useRankingSetting()
  const toast = useToast()
  const mine = (category: 'HOST' | 'ATTEND') => ranking.data?.categories.find((c) => c.category === category)?.mine
  const host = mine('HOST')
  const attend = mine('ATTEND')
  const record = (label: string, e: typeof host) => (
    <div className="flex-1 rounded-xl bg-surface-2 px-3.5 py-3">
      <p className="text-[13px] font-semibold text-ink-3">{label}</p>
      <p className="tabular mt-0.5 text-lg font-extrabold text-ink">
        {e ? `${e.value}번` : '0번'}
        {e?.rank != null && <span className="ml-1.5 text-sm font-semibold text-accent-ink">{e.rank}등</span>}
      </p>
    </div>
  )
  return (
    <section id="ranking" className={`${cardClass} flex scroll-mt-20 flex-col gap-4 px-5 py-5`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[17px] font-extrabold text-ink">이번 달 내 기록</h2>
          <p className="mt-0.5 text-sm text-ink-3">성사된 뒤 모임 시간이 지난 3교시만 세요</p>
        </div>
        <Link to="/ranking" className="inline-flex shrink-0 items-center gap-1 text-sm font-bold text-accent-ink">
          <Trophy className="size-4" aria-hidden />
          랭킹 보기
        </Link>
      </div>
      <div className="flex gap-2.5">
        {record('연 3교시 (방장)', host)}
        {record('탄 3교시 (출석)', attend)}
      </div>
      <label className="flex min-h-12 cursor-pointer items-center justify-between gap-4 border-t border-line pt-3">
        <span className="min-w-0">
          <span className="block text-[15px] font-semibold text-ink">랭킹에 안 나오기</span>
          <span className="block text-[13px] leading-snug text-ink-3">켜면 남의 랭킹과 이름 옆 배지에서 빠져요. 내 기록은 나만 봐요</span>
        </span>
        <input
          type="checkbox"
          role="switch"
          checked={member.rankingHidden}
          disabled={setting.isPending}
          onChange={(e) =>
            setting.mutate(e.target.checked, {
              onSuccess: (m) => toast.show(m.rankingHidden ? '랭킹에서 빠졌어요' : '랭킹에 다시 나와요', 'ok'),
              onError: (err) => toast.show(err.message, 'error'),
            })
          }
          className="size-6 shrink-0 accent-[var(--accent)]"
        />
      </label>
    </section>
  )
}

/** 대학원장·교수: 관리 화면 바로가기 + 처리할 일 */
function StaffShortcut() {
  const stats = useAdminStats()
  const open = stats.data?.openReports ?? 0
  return (
    <Link
      to="/admin"
      className={`${cardClass} flex items-center gap-4 px-5 py-4 transition-colors hover:bg-surface-2`}
    >
      <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-board text-on-board">
        <ShieldCheck className="size-5" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-bold text-ink">관리 화면</span>
        <span className={`block text-sm ${open > 0 ? 'font-semibold text-danger-ink' : 'text-ink-3'}`}>
          {open > 0 ? `처리 안 된 신고 ${open}건` : '처리할 신고 없음'}
        </span>
      </span>
      <ChevronRight className="size-5 shrink-0 text-ink-3" aria-hidden />
    </Link>
  )
}

export function MyPage() {
  const { member, signOut } = useAuth()
  const config = useAuthConfig()
  const withdraw = useWithdraw()
  const toast = useToast()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const { hash } = useLocation()

  // "/me#pay-links" · "/me#ranking" 처럼 들어오면 그 칸으로 내려간다
  useEffect(() => {
    if (!hash) return
    document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ block: 'start' })
  }, [hash])

  if (!member) return null
  const supportEmail = config.data?.supportEmail

  function confirmWithdraw() {
    if (
      !window.confirm(
        '정말 탈퇴할까요?\n\n• 이름·이메일·카카오 연결이 바로 지워지고 되돌릴 수 없어요\n• 내가 연, 아직 모이기 전인 3교시는 취소돼요\n• 모집 중인 3교시에서는 빠져요\n\n같은 학교 이메일로 다시 가입할 수 있어요.',
      )
    )
      return
    withdraw.mutate(undefined, {
      onSuccess: () => {
        signOut()
        toast.show('탈퇴했어요. 그동안 함께해 줘서 고마워요')
        navigate('/welcome', { replace: true })
      },
      onError: (e) => toast.show(e.message, 'error'),
    })
  }

  const staff = isStaff(member.role)

  return (
    <div className="flex flex-col gap-8">
      <section
        className={`overflow-hidden rounded-3xl bg-board text-on-board ${
          member.role === 'OWNER' ? 'ring-4 ring-exit ring-offset-0' : ''
        }`}
      >
        {staff && (
          <div
            className={`flex items-center gap-2 px-6 py-2.5 text-sm font-bold ${
              member.role === 'OWNER' ? 'bg-exit text-on-exit' : 'bg-board-2 text-on-board'
            }`}
          >
            {member.role === 'OWNER' ? <Crown className="size-4" aria-hidden /> : <ShieldCheck className="size-4" aria-hidden />}
            {member.role === 'OWNER' ? '대학원장 · IT연합회 다섯 학교 관리' : `교수 · ${member.school.shortName} 관리`}
          </div>
        )}
        {/* 이름 줄 옆에만 고치기 버튼을 두고, 소속·학교·이메일은 아래 줄에 넓게 쓴다 (좁은 폰에서 '61기 ·'만 남고 끊기지 않게) */}
        <div className="px-5 py-5 sm:px-6 sm:py-6">
          <div className="flex items-center gap-3.5 sm:gap-4">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-accent text-xl font-black text-on-accent sm:size-14">
              {member.name.slice(0, 1)}
            </span>
            <p className="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1 text-xl font-extrabold">
              {member.name}
              <SchoolBadge school={member.school} />
              {member.role !== 'USER' && <RoleTag role={member.role} />}
            </p>
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="inline-flex h-11 shrink-0 items-center gap-1.5 rounded-xl bg-board-2 px-3 text-sm font-bold text-on-board hover:brightness-110"
            >
              <Pencil className="size-4" aria-hidden />
              고치기
            </button>
          </div>
          <div className="mt-3 text-sm text-on-board-2 sm:ml-[72px]">
            <p>{affiliation(member.cohortLabel, member.departmentLabel)}</p>
            <p className="mt-0.5">{[member.school.name, member.graduateSchool].filter(Boolean).join(' ')}</p>
            {member.email && <p className="mt-0.5 break-all">{member.email}</p>}
          </div>
        </div>
        <ul className="flex flex-col gap-2 border-t border-board-line px-6 py-4 text-sm text-on-board-2">
          {member.role === 'OWNER' ? (
            <>
              <li className="flex items-center gap-2.5">
                <Users className="size-4 shrink-0" aria-hidden />
                <span>대학원장 이름은 누구에게나 실명으로 보여요</span>
              </li>
              <li className="flex items-center gap-2.5">
                <Pencil className="size-4 shrink-0" aria-hidden />
                <span>{profileEditRule(member.role)}</span>
              </li>
            </>
          ) : (
            <>
              <li className="flex items-center gap-2.5">
                <EyeOff className="size-4 shrink-0" aria-hidden />
                <span>
                  이름은 <b className="font-semibold text-on-board">{maskName(member.name)}</b>처럼 가려져요
                </span>
              </li>
              <li className="flex items-center gap-2.5">
                <Users className="size-4 shrink-0" aria-hidden />
                <span>성사된 3교시 참여자에겐 실명</span>
              </li>
              <li className="flex items-center gap-2.5">
                <Pencil className="size-4 shrink-0" aria-hidden />
                <span>{profileEditRule(member.role)}</span>
              </li>
            </>
          )}
        </ul>
      </section>

      {staff && <StaffShortcut />}

      {!member.departmentId && (
        <div className="flex items-center justify-between gap-3 rounded-2xl bg-exit/25 px-5 py-4">
          <p className="text-[15px] font-semibold text-ink">학과가 비어 있어요</p>
          <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
            학과 고르기
          </Button>
        </div>
      )}

      <RankingCard member={member} />

      <PayLinksCard member={member} />

      <ThemeSettings schoolName={member.school.shortName} />

      <PushSettings />

      <BlockedPeople />

      <section className={`${cardClass} divide-y divide-line overflow-hidden`}>
        <h2 className="sr-only">계정</h2>
        <div className="flex flex-col gap-2 px-5 py-4">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <KeyRound className="size-5 text-ink-3" aria-hidden />
            <span className="text-[15px] font-semibold text-ink">로그인</span>
            <span className="flex gap-1.5">
              <LoginMethod on={member.kakaoLinked} label="카카오" />
              <LoginMethod on={member.hasPassword} label="이메일" />
            </span>
            {member.email && (
              <Link to="/password" className="ml-auto text-sm font-semibold text-accent-ink underline underline-offset-4">
                {member.hasPassword ? '비밀번호 바꾸기' : '비밀번호 만들기'}
              </Link>
            )}
          </div>
          {!member.hasPassword && member.email && (
            <p className="text-[13px] leading-relaxed text-ink-3">비밀번호를 만들면 학교 메일로도 들어올 수 있어요. 같은 계정이에요.</p>
          )}
        </div>
        <Link to="/terms" className={rowClass}>
          <FileText className="size-5 text-ink-3" aria-hidden />
          이용약관
          <ChevronRight className="ml-auto size-4 text-ink-3" aria-hidden />
        </Link>
        <Link to="/privacy" className={rowClass}>
          <FileText className="size-5 text-ink-3" aria-hidden />
          개인정보 처리방침
          <ChevronRight className="ml-auto size-4 text-ink-3" aria-hidden />
        </Link>
        {supportEmail && (
          <a href={`mailto:${supportEmail}`} className={rowClass}>
            <Mail className="size-5 text-ink-3" aria-hidden />
            문의하기
            <span className="ml-auto truncate text-sm font-medium text-ink-3">{supportEmail}</span>
          </a>
        )}
        <button
          type="button"
          onClick={() => {
            signOut()
            toast.show('로그아웃했어요')
          }}
          className={`${rowClass} text-danger-ink hover:bg-danger-soft`}
        >
          <LogOut className="size-5" aria-hidden />
          로그아웃
        </button>
      </section>

      {member.role === 'OWNER' ? (
        <p className="text-center text-[13px] text-ink-3">
          대학원장은 탈퇴할 수 없어요
        </p>
      ) : (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={confirmWithdraw}
            disabled={withdraw.isPending}
            className="inline-flex h-10 items-center gap-1.5 rounded-lg px-3 text-sm font-semibold text-ink-3 underline-offset-4 hover:text-danger-ink hover:underline disabled:opacity-50"
          >
            <UserX className="size-4" aria-hidden />
            탈퇴하기
          </button>
        </div>
      )}

      <MadeBy className="pb-2 text-center" />

      {editing && <ProfileSheet member={member} onClose={() => setEditing(false)} />}
    </div>
  )
}
