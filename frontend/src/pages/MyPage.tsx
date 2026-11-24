import { Link, useNavigate } from 'react-router'
import { ChevronRight, FileText, LogOut, Mail, ShieldCheck, UserX } from 'lucide-react'
import { useAuthConfig, useBlocks, useMyMeetups, useUnblock, useWithdraw } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { MeetupRow, MeetupRowSkeleton } from '../components/MeetupRow'
import { RoleTag } from '../components/PersonName'
import { SchoolBadge } from '../components/SchoolBadge'
import { Button, EmptyState, ErrorState, Segmented } from '../components/ui'
import { buttonClass, cardClass } from '../components/styles'
import { useToast } from '../components/toastContext'
import { useNow } from '../lib/hooks'
import { affiliation, isStaff } from '../lib/person'
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

  return (
    <div className="flex flex-col gap-8">
      <section className="overflow-hidden rounded-3xl bg-board text-on-board">
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
        <p className="border-t border-board-line px-6 py-3 text-sm leading-relaxed text-on-board-2">
          다른 사람에게는 이름이 가운데가 가려진 채로 보여요. 성사된 3교시의 참여자와 교수·대학원장(관리 화면)에게만 실명이 보여요.
          <br />
          이름·기수·학과를 바꿔야 하면 교수나 대학원장에게 요청해 주세요.
        </p>
      </section>

      {!member.departmentId && (
        <p className="rounded-2xl bg-exit/25 px-5 py-4 text-[15px] font-semibold text-ink">
          학과가 비어 있어요. 교수나 대학원장에게 학과를 넣어 달라고 요청해 주세요.
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
              body="내가 연 3교시와 참여한 3교시가 여기에 모여요. 무산된 것도 나만 볼 수 있게 남아요."
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

      <BlockedPeople />

      <section className={`${cardClass} divide-y divide-line overflow-hidden`}>
        <h2 className="sr-only">계정</h2>
        {isStaff(member.role) && (
          <Link to="/admin" className={rowClass}>
            <ShieldCheck className="size-5 text-ink-3" aria-hidden />
            관리 페이지
            <ChevronRight className="ml-auto size-4 text-ink-3" aria-hidden />
          </Link>
        )}
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
            if (window.confirm('로그아웃할까요? 다음에는 카카오로 바로 들어올 수 있어요.')) signOut()
          }}
          className={`${rowClass} text-danger-ink hover:bg-danger-soft`}
        >
          <LogOut className="size-5" aria-hidden />
          로그아웃
        </button>
      </section>

      {member.role === 'OWNER' ? (
        <p className="text-center text-[13px] text-ink-3">
          대학원장은 탈퇴할 수 없어요. 서버 설정(OWNER_EMAIL)에서 대학원장을 먼저 바꿔 주세요.
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
    </div>
  )
}
