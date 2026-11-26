import { useEffect, useState } from 'react'
import { BellOff, Moon } from 'lucide-react'
import { usePushSettings } from '../api/queries'
import type { PushType } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { isStaff } from '../lib/person'
import { enablePush, isApp, pushPermission, sendPushToken, type PushState } from '../lib/native'
import { Button } from './ui'
import { cardClass } from './styles'
import { useToast } from './toastContext'

/** staffHint: 교수·대학원장에게 보일 설명 (없으면 hint) */
const ITEMS: Array<{ type: PushType; label: string; hint: string; staffHint?: string }> = [
  { type: 'JOIN', label: '내 3교시 참여·나감', hint: '내가 연 3교시에 누가 들어오거나 나가면' },
  { type: 'REACHED', label: '성사', hint: '참여한 3교시가 성사되면' },
  { type: 'DEADLINE', label: '모집 마감 30분 전', hint: '참여한 3교시에 아직 인원이 모자라면' },
  { type: 'REMINDER', label: '모임 1시간 전', hint: '참여한 3교시가 성사됐으면 모이기 1시간 전에' },
  { type: 'CHANGED', label: '시간·장소 변경 · 취소', hint: '참여한 3교시가 바뀌거나 취소되면' },
  { type: 'SPOT', label: '빈자리', hint: '자리 나면 알림을 신청한 3교시에 자리가 나면' },
  { type: 'SETTLE', label: '정산', hint: '정산 요청·다시 알리기, 내가 계산한 정산이 끝나면' },
  { type: 'SCHOOL', label: '새 우리 학교 3교시', hint: '우리 학교 3교시가 열리면 (우리 기수만 3교시는 그 기수에게만)' },
  { type: 'FEDERATION', label: '새 연합 3교시', hint: '연합 3교시가 열리면' },
  { type: 'NOTICE', label: '새 공지', hint: '교수·대학원장이 올린 공지' },
  { type: 'REPORT', label: '신고', hint: '내가 한 신고를 처리하면', staffHint: '새 신고, 내가 한 신고의 처리 결과' },
]

/** 앱 알림 켜고 끄기 (앱에서만 보인다) */
export function PushSettings() {
  const { member, updateMember } = useAuth()
  const save = usePushSettings()
  const toast = useToast()
  const [permission, setPermission] = useState<PushState | null>(null)

  useEffect(() => {
    if (!isApp) return
    let alive = true
    pushPermission()
      .then((p) => alive && setPermission(p))
      .catch(() => alive && setPermission('unavailable'))
    return () => {
      alive = false
    }
  }, [])

  if (!isApp || !member || permission === null || permission === 'unavailable') return null
  const off = new Set(member.pushOff)
  const staff = isStaff(member.role)

  /** 먼저 바꿔 보여 주고 저장한다. 실패하면 되돌린다 (서버가 느려도 스위치가 잠기지 않게) */
  function apply(next: Set<PushType>) {
    if (!member) return
    const before = member
    updateMember({ ...member, pushOff: [...next] })
    save.mutate([...next], {
      onSuccess: updateMember,
      onError: (e) => {
        updateMember(before)
        toast.show(e.message, 'error')
      },
    })
  }

  function toggle(type: PushType) {
    const next = new Set(off)
    if (next.has(type)) next.delete(type)
    else next.add(type)
    apply(next)
  }

  const allOff = ITEMS.every((item) => off.has(item.type))

  async function turnOn() {
    const state = await enablePush(sendPushToken).catch((): PushState => 'denied')
    setPermission(state)
    if (state === 'denied') toast.show('휴대폰 설정 › 애플리케이션 › 안암3교시 › 알림에서 켜 주세요')
  }

  return (
    <section className={`${cardClass} flex flex-col gap-3 px-5 py-5`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[17px] font-extrabold text-ink">알림</h2>
          <p className="mt-0.5 text-sm text-ink-3">이 폰으로 받을 알림을 골라요. 꺼도 알림함에는 남아요.</p>
        </div>
        {permission !== 'denied' && (
          <button
            type="button"
            className="hit shrink-0 text-sm font-semibold text-accent-ink underline underline-offset-2"
            onClick={() => apply(allOff ? new Set() : new Set(ITEMS.map((item) => item.type)))}
          >
            {allOff ? '모두 켜기' : '모두 끄기'}
          </button>
        )}
      </div>
      {permission === 'denied' ? (
        <div className="flex flex-col gap-3 rounded-xl bg-surface-2 px-4 py-4">
          <p className="flex items-center gap-2 text-[15px] font-semibold text-ink">
            <BellOff className="size-5 text-ink-3" aria-hidden />
            알림이 꺼져 있어요
          </p>
          <Button variant="secondary" onClick={() => void turnOn()}>
            알림 켜기
          </Button>
        </div>
      ) : (
        <ul className="flex flex-col divide-y divide-line">
          {ITEMS.map((item) => (
            <li key={item.type}>
              <label className="flex min-h-14 cursor-pointer items-center justify-between gap-4 py-2.5">
                <span className="min-w-0">
                  <span className="block text-[15px] font-semibold text-ink">{item.label}</span>
                  <span className="block text-[13px] leading-snug text-ink-3">
                    {staff && item.staffHint ? item.staffHint : item.hint}
                  </span>
                </span>
                <input
                  type="checkbox"
                  role="switch"
                  checked={!off.has(item.type)}
                  onChange={() => toggle(item.type)}
                  className="size-6 shrink-0 accent-[var(--accent)]"
                />
              </label>
            </li>
          ))}
        </ul>
      )}
      {permission !== 'denied' && (
        <p className="flex items-start gap-2 rounded-xl bg-surface-2 px-3.5 py-3 text-[13px] leading-snug text-ink-2">
          <Moon className="mt-0.5 size-4 shrink-0 text-ink-3" aria-hidden />
          <span>
            새 3교시·새 공지·정산 알림은 밤 12시~아침 8시에 쉬었다가 아침 8시에 보내요. 내 3교시 알림은 밤에도 바로 가요.
          </span>
        </p>
      )}
    </section>
  )
}
