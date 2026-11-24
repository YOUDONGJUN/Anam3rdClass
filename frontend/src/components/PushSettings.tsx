import { useEffect, useState } from 'react'
import { BellOff } from 'lucide-react'
import { usePushSettings } from '../api/queries'
import type { PushType } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { enablePush, isApp, pushPermission, sendPushToken, type PushState } from '../lib/native'
import { Button } from './ui'
import { cardClass } from './styles'
import { useToast } from './toastContext'

const ITEMS: Array<{ type: PushType; label: string; hint: string }> = [
  { type: 'JOIN', label: '내 3교시 참여·나감', hint: '내가 연 3교시에 누가 들어오거나 나가면' },
  { type: 'REACHED', label: '성사', hint: '참여한 3교시가 성사되면' },
  { type: 'DEADLINE', label: '모집 마감 30분 전', hint: '참여한 3교시에 아직 인원이 모자라면' },
  { type: 'CHANGED', label: '시간·장소 변경 · 취소', hint: '참여한 3교시가 바뀌거나 취소되면' },
  { type: 'NOTICE', label: '새 공지', hint: '교수·대학원장이 올린 공지' },
  { type: 'FEDERATION', label: '새 연합 3교시', hint: '다섯 학교 연합 3교시가 열리면' },
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

  function toggle(type: PushType) {
    const next = new Set(off)
    if (next.has(type)) next.delete(type)
    else next.add(type)
    save.mutate([...next], {
      onSuccess: updateMember,
      onError: (e) => toast.show(e.message, 'error'),
    })
  }

  async function turnOn() {
    const state = await enablePush(sendPushToken).catch((): PushState => 'denied')
    setPermission(state)
    if (state === 'denied') toast.show('휴대폰 설정 › 애플리케이션 › 안암3교시 › 알림에서 켜 주세요')
  }

  return (
    <section className={`${cardClass} flex flex-col gap-3 px-5 py-5`}>
      <div>
        <h2 className="text-[17px] font-extrabold text-ink">알림</h2>
        <p className="mt-0.5 text-sm text-ink-3">이 폰으로 받을 알림을 골라요.</p>
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
                  <span className="block text-[13px] leading-snug text-ink-3">{item.hint}</span>
                </span>
                <input
                  type="checkbox"
                  role="switch"
                  checked={!off.has(item.type)}
                  disabled={save.isPending}
                  onChange={() => toggle(item.type)}
                  className="size-6 shrink-0 accent-[var(--accent)]"
                />
              </label>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
