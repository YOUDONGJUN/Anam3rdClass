// 사진 크게 보기의 확대·이동 계산 (두 손가락으로 벌리기 · 두 번 탭 · 끌어서 옮기기).
// 좌표는 모두 사진 칸의 가운데를 (0, 0) 으로 본다.
// 사진은 가운데를 기준으로 scale 배 커진 뒤 (x, y) 만큼 옮겨진다 — CSS transform: translate(x, y) scale(scale)

export interface Zoom {
  scale: number
  x: number
  y: number
}

export interface Point {
  x: number
  y: number
}

export interface Size {
  width: number
  height: number
}

/** 두 손가락: 가운데와 사이 거리 */
export interface Span {
  mid: Point
  dist: number
}

export const MIN_SCALE = 1
export const MAX_SCALE = 4
/** 두 번 탭하면 이만큼 */
export const TAP_SCALE = 2.5

export const NO_ZOOM: Zoom = { scale: 1, x: 0, y: 0 }

function clamp(v: number, min: number, max: number) {
  return Math.min(max, Math.max(min, v))
}

export function spanOf(a: Point, b: Point): Span {
  return { mid: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }, dist: Math.max(1, Math.hypot(a.x - b.x, a.y - b.y)) }
}

/** 배율은 1~4배, 옮긴 거리는 사진 끝이 칸 안쪽으로 들어오지 않을 만큼까지 */
export function clampZoom(z: Zoom, image: Size, box: Size): Zoom {
  const scale = clamp(z.scale, MIN_SCALE, MAX_SCALE)
  const maxX = Math.max(0, (image.width * scale - box.width) / 2)
  const maxY = Math.max(0, (image.height * scale - box.height) / 2)
  return { scale, x: clamp(z.x, -maxX, maxX) || 0, y: clamp(z.y, -maxY, maxY) || 0 }
}

/** 화면의 point 아래 있는 사진 자리가 그대로 머물도록 배율을 바꾼다 */
export function zoomAt(z: Zoom, point: Point, scale: number): Zoom {
  const s = clamp(scale, MIN_SCALE, MAX_SCALE)
  const ratio = s / z.scale
  return { scale: s, x: point.x - (point.x - z.x) * ratio, y: point.y - (point.y - z.y) * ratio }
}

/** 두 손가락: 처음 두 손가락 가운데 있던 사진 자리가 지금 가운데를 따라오고, 벌린 만큼 커진다 */
export function pinch(start: Zoom, from: Span, to: Span): Zoom {
  const scale = clamp((start.scale * to.dist) / from.dist, MIN_SCALE, MAX_SCALE)
  const qx = (from.mid.x - start.x) / start.scale
  const qy = (from.mid.y - start.y) / start.scale
  return { scale, x: to.mid.x - qx * scale, y: to.mid.y - qy * scale }
}

/** 두 번 탭: 확대돼 있으면 원래대로, 아니면 누른 자리를 키운다 */
export function toggleZoom(z: Zoom, point: Point): Zoom {
  return z.scale > 1.01 ? NO_ZOOM : zoomAt(z, point, TAP_SCALE)
}
