// 학교별로 다른 것들 (장소 추천). 학교 색은 index.css, 테마 규칙은 lib/theme.ts

/** 3교시 열기 화면의 장소 추천. 학교 근처 자주 모이는 곳 (없으면 추천 없이 직접 쓴다) */
export const PLACE_PRESETS: Record<string, string[]> = {
  KU: ['안암역 2번 출구', '참살이길', '고대 정문 앞', '개운사길', '제기동역'],
  YONSEI: ['신촌역', '연세로', '연대 정문 앞', '명물거리', '이대역'],
  SKKU: ['혜화역', '대학로', '성대 정문 앞', '성균관대역', '율전동'],
  SOGANG: ['신촌역', '서강대역', '서강대 정문 앞', '대흥역', '광흥창역'],
  HANYANG: ['한양대역', '왕십리역', '한양대 정문 앞', '사근동', '뚝섬역'],
}

export function placePresets(code: string | null | undefined): string[] {
  return (code && PLACE_PRESETS[code]) || []
}
