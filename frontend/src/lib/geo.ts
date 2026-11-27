// 거리 계산과 내 위치. 내 위치는 이 폰(브라우저) 안에서만 쓴다 — 서버로 보내지도, 저장하지도 않는다.
// 앱을 쓰는 동안 메모리에만 두고, 새로 열면 다시 받는다 (이미 허락했으면 묻지 않고).
import { useSyncExternalStore } from 'react'
import type { GeoPoint } from '../api/types'

const EARTH_RADIUS_M = 6_371_000

/** 두 점 사이 직선 거리(m) */
export function distanceMeters(a: GeoPoint, b: GeoPoint): number {
  const rad = (d: number) => (d * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/** 350m · 1.2km · 12km (직선 거리라 너무 잘게 쪼개지 않는다) */
export function formatDistance(meters: number): string {
  if (meters < 100) return '100m 안'
  const byFifty = Math.round(meters / 50) * 50
  if (byFifty < 1000) return `${byFifty}m`
  if (meters < 10_000) {
    const km = Math.round(meters / 100) / 10
    return `${km % 1 === 0 ? km.toFixed(0) : km.toFixed(1)}km`
  }
  return `${Math.round(meters / 1000)}km`
}

export type GeoStatus = 'idle' | 'locating' | 'ok' | 'denied' | 'unavailable'

export interface MyLocation {
  point: GeoPoint | null
  status: GeoStatus
}

let current: MyLocation = { point: null, status: 'idle' }
const listeners = new Set<() => void>()

function update(next: MyLocation) {
  current = next
  listeners.forEach((l) => l())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 내 위치 (받기 전이면 point 가 null) */
export function useMyLocation(): MyLocation {
  return useSyncExternalStore(subscribe, () => current)
}

let pending: Promise<GeoPoint | null> | null = null

/**
 * 내 위치를 받는다. 처음이면 폰이 허락을 묻는다. 대략적인 위치면 충분하다 (정확도 높이기 끔).
 * 거절했거나 못 받으면 null
 */
export function requestMyLocation(): Promise<GeoPoint | null> {
  if (pending) return pending
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    update({ point: null, status: 'unavailable' })
    return Promise.resolve(null)
  }
  update({ point: current.point, status: 'locating' })
  pending = new Promise<GeoPoint | null>((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const point = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        update({ point, status: 'ok' })
        resolve(point)
      },
      (err) => {
        update({ point: null, status: err.code === err.PERMISSION_DENIED ? 'denied' : 'unavailable' })
        resolve(null)
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 5 * 60_000 },
    )
  }).finally(() => {
    pending = null
  })
  return pending
}

/** 이미 허락한 적이 있으면 묻지 않고 위치를 받아 둔다 (허락 여부를 알 수 없는 곳에서는 아무것도 안 한다) */
export async function refreshLocationIfGranted(): Promise<void> {
  if (current.status === 'ok' || current.status === 'locating') return
  try {
    const perm = await navigator.permissions?.query({ name: 'geolocation' as PermissionName })
    if (perm?.state === 'granted') await requestMyLocation()
  } catch {
    // 권한 조회가 안 되는 브라우저·웹뷰: 누를 때 받는다
  }
}

/** 테스트용: 내 위치 상태를 처음으로 */
export function resetMyLocation() {
  pending = null
  update({ point: null, status: 'idle' })
}
