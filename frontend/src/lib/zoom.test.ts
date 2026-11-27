import { describe, expect, it } from 'vitest'
import { clampZoom, MAX_SCALE, NO_ZOOM, pinch, spanOf, TAP_SCALE, toggleZoom, zoomAt } from './zoom'

const image = { width: 300, height: 400 }
const box = { width: 360, height: 600 }

describe('zoomAt', () => {
  it('누른 자리의 사진이 그대로 머문다', () => {
    const z = zoomAt(NO_ZOOM, { x: 100, y: -50 }, 2)
    // 화면 100 아래 있던 사진 자리(배율 1 에서 100)는 2배 뒤에도 화면 100 에 있다
    expect(100 * z.scale + z.x).toBeCloseTo(100)
    expect(-50 * z.scale + z.y).toBeCloseTo(-50)
  })

  it('4배를 넘지 않는다', () => {
    expect(zoomAt(NO_ZOOM, { x: 0, y: 0 }, 10).scale).toBe(MAX_SCALE)
  })
})

describe('pinch', () => {
  it('두 배로 벌리면 두 배가 되고, 가운데가 움직이면 따라간다', () => {
    const from = spanOf({ x: -50, y: 0 }, { x: 50, y: 0 })
    const to = spanOf({ x: -80, y: 20 }, { x: 120, y: 20 })
    const z = pinch(NO_ZOOM, from, to)
    expect(z.scale).toBeCloseTo(2)
    // 처음 가운데(0,0) 아래 있던 사진 자리가 지금 가운데(20,20)에 있다
    expect(z.x).toBeCloseTo(20)
    expect(z.y).toBeCloseTo(20)
  })

  it('1배보다 작아지지 않는다', () => {
    const z = pinch(NO_ZOOM, spanOf({ x: -100, y: 0 }, { x: 100, y: 0 }), spanOf({ x: -10, y: 0 }, { x: 10, y: 0 }))
    expect(z.scale).toBe(1)
  })
})

describe('clampZoom', () => {
  it('1배면 옮길 수 없다', () => {
    expect(clampZoom({ scale: 1, x: 80, y: -30 }, image, box)).toEqual(NO_ZOOM)
  })

  it('확대하면 사진 끝이 칸 끝에 닿을 때까지만 옮긴다', () => {
    // 2배: 600×800 → 가로 (600-360)/2=120, 세로 (800-600)/2=100 까지
    expect(clampZoom({ scale: 2, x: 500, y: -500 }, image, box)).toEqual({ scale: 2, x: 120, y: -100 })
  })
})

describe('toggleZoom', () => {
  it('두 번 탭하면 키우고, 다시 두 번 탭하면 원래대로', () => {
    const zoomed = toggleZoom(NO_ZOOM, { x: 40, y: 40 })
    expect(zoomed.scale).toBe(TAP_SCALE)
    expect(toggleZoom(zoomed, { x: 0, y: 0 })).toEqual(NO_ZOOM)
  })
})
