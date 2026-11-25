// 장소를 지도 앱에서 찾기. 장소는 자유 글자라 좌표 없이 검색 화면을 연다 (API 키 필요 없음).
// 앱에서는 바깥 브라우저(또는 깔려 있는 지도 앱)로 열린다.

export interface MapProvider {
  label: string
  url: (place: string) => string
}

export const MAP_PROVIDERS: readonly MapProvider[] = [
  { label: '카카오맵', url: (place) => `https://map.kakao.com/link/search/${encodeURIComponent(place.trim())}` },
  { label: '네이버지도', url: (place) => `https://map.naver.com/p/search/${encodeURIComponent(place.trim())}` },
]
