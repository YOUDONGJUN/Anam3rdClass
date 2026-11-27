// 장소를 지도 앱에서 보기. 장소 검색에서 고른 3교시는 좌표가 있어 카카오맵이 그 자리를 바로 연다.
// 좌표가 없으면(직접 쓴 장소) 장소 이름으로 검색 화면을 연다 (API 키 필요 없음).
// 앱에서는 바깥 브라우저(또는 깔려 있는 지도 앱)로 열린다.
import type { GeoPoint } from '../api/types'

export interface MapProvider {
  label: string
  url: (place: string, location?: GeoPoint | null) => string
}

export const MAP_PROVIDERS: readonly MapProvider[] = [
  {
    label: '카카오맵',
    url: (place, location) =>
      location
        ? `https://map.kakao.com/link/map/${encodeURIComponent(place.trim().replaceAll(',', ' '))},${location.lat},${location.lng}`
        : `https://map.kakao.com/link/search/${encodeURIComponent(place.trim())}`,
  },
  { label: '네이버지도', url: (place) => `https://map.naver.com/p/search/${encodeURIComponent(place.trim())}` },
]
