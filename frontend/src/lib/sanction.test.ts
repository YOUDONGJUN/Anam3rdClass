import { describe, expect, it } from 'vitest'
import { nextSanction, warnEffects } from './sanction'

describe('제재 단계', () => {
  it('경고 수 → 다음 단계: 경고 · 7일 · 30일 · 영구, 그 뒤로도 영구', () => {
    expect(nextSanction(0)).toEqual({ kind: 'WARNING', label: '경고', strike: 1 })
    expect(nextSanction(1)).toEqual({ kind: 'SUSPEND_7', label: '7일 정지', strike: 2 })
    expect(nextSanction(2)).toEqual({ kind: 'SUSPEND_30', label: '30일 정지', strike: 3 })
    expect(nextSanction(3)).toEqual({ kind: 'BAN', label: '영구 정지', strike: 4 })
    expect(nextSanction(7).kind).toBe('BAN')
  })

  it('경고 주기 창 안내: 첫 경고는 계속 쓸 수 있고, 정지부터는 3교시가 정리된다', () => {
    expect(warnEffects(0)[0]).toContain('계속 쓸 수 있어요')
    expect(warnEffects(1)[0]).toBe('7일 정지돼요. 그동안 로그인할 수 없어요')
    expect(warnEffects(3)[0]).toContain('영구 정지돼요')
    expect(warnEffects(2)).toContain('그 원우가 연 모이기 전 3교시는 취소되고, 참여 중인 3교시에서는 빠져요')
  })
})
