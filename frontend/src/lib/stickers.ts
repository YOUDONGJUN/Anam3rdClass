// 톡방 이모티콘 (KU 호랑이). 그림은 public/stickers/tiger/*.svg — 살짝 움직이고, 폰의 '동작 줄이기'를 켜면 멈춘다.
// 이름(key)은 서버 ChatStickers 와 같아야 한다 (거기 없는 이름은 보낼 수 없다).

export interface Sticker {
  key: string
  /** 화면 읽기 프로그램 · 신고 기록 · 알림에 쓰는 이름 */
  label: string
  src: string
}

const TIGER: [string, string][] = [
  ['going', '가는 중'],
  ['where', '어디야?'],
  ['thanks', '고마워요'],
  ['arrived', '도착!'],
  ['late', '늦어요'],
  ['cheers', '건배!'],
  ['good', '수고했어요'],
  ['lol', 'ㅋㅋㅋ'],
  ['sorry', '미안해요'],
  ['ok', '좋아요'],
  ['bye', '또 봐요'],
  ['hungry', '배고파'],
]

export const STICKERS: Sticker[] = TIGER.map(([name, label]) => ({
  key: `tiger-${name}`,
  label,
  src: `/stickers/tiger/${name}.svg`,
}))

const byKey = new Map(STICKERS.map((s) => [s.key, s]))

export function stickerOf(key: string | null | undefined): Sticker | null {
  return key ? (byKey.get(key) ?? null) : null
}
