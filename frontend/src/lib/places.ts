// 3교시 열기 화면의 장소 추천: 우리 학교에서 많이 쓴 장소가 먼저, 남는 자리는 학교 근처 기본 장소로.

export const PLACE_SUGGESTIONS = 8

/** 많이 쓴 장소(서버 순서) 다음 기본 장소. 앞뒤 공백·대소문자만 다른 것은 하나로, limit 개까지 */
export function mergePlaces(popular: readonly string[], presets: readonly string[], limit = PLACE_SUGGESTIONS): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const raw of [...popular, ...presets]) {
    const place = raw.trim()
    const key = place.toLowerCase().replace(/\s+/g, ' ')
    if (!place || seen.has(key)) continue
    seen.add(key)
    result.push(place)
    if (result.length >= limit) break
  }
  return result
}
