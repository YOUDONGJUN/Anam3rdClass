// 3교시 열기 화면의 장소 추천: 우리 학교에서 많이 쓴 장소가 먼저, 남는 자리는 학교 근처 기본 장소로.
import type { GeoPoint, PlaceSuggestion } from '../api/types'

export const PLACE_SUGGESTIONS = 8

/** 장소 칸의 값: 이름 + 장소 검색에서 고른 위치·지역 (직접 쓰면 위치 없음) */
export interface PlaceValue {
  place: string
  location: GeoPoint | null
  region: string | null
}

const keyOf = (name: string) => name.trim().toLowerCase().replace(/\s+/g, ' ')

/** 많이 쓴 장소(서버 순서) 다음 기본 장소. 앞뒤 공백·대소문자만 다른 것은 하나로, limit 개까지 */
export function mergePlaces(
  popular: readonly PlaceSuggestion[],
  presets: readonly string[],
  limit = PLACE_SUGGESTIONS,
): PlaceSuggestion[] {
  const seen = new Set<string>()
  const result: PlaceSuggestion[] = []
  const all: PlaceSuggestion[] = [...popular, ...presets.map((name) => ({ name, location: null, region: null }))]
  for (const raw of all) {
    const name = raw.name.trim()
    const key = keyOf(name)
    if (!name || seen.has(key)) continue
    seen.add(key)
    result.push({ ...raw, name })
    if (result.length >= limit) break
  }
  return result
}
