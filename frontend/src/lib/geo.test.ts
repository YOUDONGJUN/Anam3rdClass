import { afterEach, describe, expect, it, vi } from 'vitest'
import { distanceMeters, formatDistance, requestMyLocation, resetMyLocation } from './geo'

describe('거리', () => {
  it('두 점 사이 직선 거리 (고려대 ↔ 연세대 약 8.5km)', () => {
    const d = distanceMeters({ lat: 37.5894, lng: 127.0322 }, { lat: 37.5658, lng: 126.9386 })
    expect(d).toBeGreaterThan(8_000)
    expect(d).toBeLessThan(9_000)
    expect(distanceMeters({ lat: 37.5, lng: 127 }, { lat: 37.5, lng: 127 })).toBe(0)
  })

  it('보기 좋게: 100m 안 · 50m 단위 · 0.1km 단위 · 10km부터는 1km 단위', () => {
    expect(formatDistance(30)).toBe('100m 안')
    expect(formatDistance(349)).toBe('350m')
    expect(formatDistance(980)).toBe('1km')
    expect(formatDistance(1000)).toBe('1km')
    expect(formatDistance(1240)).toBe('1.2km')
    expect(formatDistance(9_960)).toBe('10km')
    expect(formatDistance(12_400)).toBe('12km')
  })
})

describe('내 위치', () => {
  afterEach(() => {
    resetMyLocation()
    vi.unstubAllGlobals()
  })

  it('받으면 그 위치, 거절하면 null (서버로 보내지 않는다)', async () => {
    const getCurrentPosition = vi
      .fn()
      .mockImplementationOnce((ok: PositionCallback) => ok({ coords: { latitude: 37.58, longitude: 127.03 } } as GeolocationPosition))
      .mockImplementationOnce((_ok: PositionCallback, fail: PositionErrorCallback) =>
        fail({ code: 1, PERMISSION_DENIED: 1 } as GeolocationPositionError),
      )
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } })
    expect(await requestMyLocation()).toEqual({ lat: 37.58, lng: 127.03 })
    expect(await requestMyLocation()).toBeNull()
  })

  it('위치를 쓸 수 없는 곳이면 null', async () => {
    vi.stubGlobal('navigator', {})
    expect(await requestMyLocation()).toBeNull()
  })
})
