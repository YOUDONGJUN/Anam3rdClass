// 제재 단계: 경고가 쌓이면 1번째 경고 · 2번째 7일 정지 · 3번째 30일 정지 · 4번째부터 영구 정지 (서버 MemberSanction.Kind)
import type { SanctionKind } from '../api/types'

export const SANCTION_STEPS = ['경고', '7일 정지', '30일 정지', '영구 정지'] as const

/** "주의": 최근 30일 동안 서로 다른 원우 이만큼이 같은 사람을 신고 (서버 ReportService.FLAG_REPORTERS) */
export const FLAG_REPORTERS = 3

const KINDS: readonly SanctionKind[] = ['WARNING', 'SUSPEND_7', 'SUSPEND_30', 'BAN']

/** 지금 경고 수 → 다음 경고의 단계 */
export function nextSanction(strikes: number): { kind: SanctionKind; label: string; strike: number } {
  const strike = Math.max(0, strikes) + 1
  const index = Math.min(strike, KINDS.length) - 1
  return { kind: KINDS[index], label: SANCTION_STEPS[index], strike }
}

/** 경고를 주면 어떻게 되는지 (경고 주기 창에 보인다) */
export function warnEffects(strikes: number): string[] {
  const next = nextSanction(strikes)
  if (next.kind === 'WARNING') return ['경고만 남고 계속 쓸 수 있어요', '본인에게 알림과 경고 창이 가요']
  return [
    next.kind === 'BAN' ? '영구 정지돼요. 바로 로그아웃되고 다시 로그인할 수 없어요' : `${next.label}돼요. 그동안 로그인할 수 없어요`,
    '그 원우가 연 모이기 전 3교시는 취소되고, 참여 중인 3교시에서는 빠져요',
    '본인에게 알림이 가요',
  ]
}
