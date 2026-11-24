import { Link, useNavigate } from 'react-router'
import { Check, ChevronRight, Crown, EyeOff, FileText, KeyRound, LogOut, Mail, Minus, Pencil, ShieldCheck, Users, UserX } from 'lucide-react'
import { useAdminStats, useAuthConfig, useBlocks, useMyMeetups, useUnblock, useWithdraw } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { MadeBy } from '../components/MadeBy'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { RoleTag } from '../components/PersonName'
import { PushSettings } from '../components/PushSettings'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, EmptyState, ErrorState, Segmented } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { affiliation, isStaff, maskName } from '../lib/person'
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
  const { data, isPending, isError, error, refetch } = useMyMeetups()
  const config = useAuthConfig()
  const withdraw = useWithdraw()
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow()

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
        <div className="flex items-center gap-4 px-6 py-6">
          <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-accent text-xl font-black text-on-accent">
            {member.name.slice(0, 1)}
          </span>
          <div className="min-w-0">
            <p className="flex flex-wrap items-center gap-2 text-xl font-extrabold">
              {member.name}
              <SchoolBadge school={member.school} />
              {member.role !== 'USER' && <RoleTag role={member.role} />}
            </p>
            <p className="mt-1 text-sm text-on-board-2">
              {affiliation(member.cohortLabel, member.departmentLabel)}
            </p>
            <p className="mt-0.5 text-sm text-on-board-2">
              {[member.school.name, member.graduateSchool].filter(Boolean).join(' ')}
            </p>
            {member.email && <p className="mt-0.5 truncate text-sm text-on-board-2">{member.email}</p>}
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
                <span>내 정보는 관리 › 원우에서 고쳐요</span>
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
                <span>{member.role === 'MANAGER' ? '정보 수정은 대학원장에게 요청' : '정보 수정은 교수·대학원장에게 요청'}</span>
              </li>
            </>
          )}
        </ul>
      </section>

      {staff && <StaffShortcut />}

      {!member.departmentId && (
        <p className="rounded-2xl bg-exit/25 px-5 py-4 text-[15px] font-semibold text-ink">
          학과가 비어 있어요. 교수·대학원장에게 요청해 주세요.
        </p>
      )}

      <section>
        <h2 className="mb-3 px-1 text-[17px] font-extrabold text-ink">내 3교시</h2>
        {isPending ? (
          <div className={`${cardClass} divide-y divide-line`}>
            <MeetupRowSkeleton />
            <MeetupRowSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error.message} onRetry={() => refetch()} />
        ) : data.length === 0 ? (
          <div className={cardClass}>
            <EmptyState
              title="아직 함께한 3교시가 없어요"
              body="내가 열고 참여한 3교시가 여기 모여요"
              action={
                <Link to="/" className={buttonClass('secondary')}>
                  열린 3교시 보기
                </Link>
              }
            />
          </div>
        ) : (
          <div className={`${cardClass} divide-y divide-line overflow-hidden`}>
            {data.map((m) => (
              <MeetupRow key={m.id} meetup={m} now={now} showDate />
            ))}
          </div>
        )}
      </section>

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
    </div>
  )
}
