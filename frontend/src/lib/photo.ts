// 사진은 폰에서 줄여서 올린다 (원본 3~5MB → 수백 KB). 저장소 무료 용량(10GB)을 오래 쓰려고.

/** 톡방: 긴 변 1280px (≈200KB) · 추억: 2048px (≈400KB) */
export const CHAT_SIDE = 1280
export const MEMORY_SIDE = 2048
/** 한 번에 고를 수 있는 장수 */
export const PER_SEND = 5

/** 긴 변이 max 를 넘으면 비율을 지켜 줄인다 (작은 사진은 그대로) */
export function fitWithin(width: number, height: number, max: number): { width: number; height: number } {
  const long = Math.max(width, height)
  if (long <= max || long === 0) return { width, height }
  const scale = max / long
  return { width: Math.round(width * scale), height: Math.round(height * scale) }
}

async function decode(file: Blob): Promise<{ source: CanvasImageSource; width: number; height: number; close: () => void }> {
  // 사진의 회전 정보(EXIF)를 반영해서 읽는다 — 세로로 찍은 사진이 눕지 않게
  if ('createImageBitmap' in window) {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
      return { source: bitmap, width: bitmap.width, height: bitmap.height, close: () => bitmap.close() }
    } catch {
      // 아래 방법으로
    }
  }
  const url = URL.createObjectURL(file)
  try {
    const img = new Image()
    img.decoding = 'async'
    img.src = url
    await img.decode()
    return { source: img, width: img.naturalWidth, height: img.naturalHeight, close: () => URL.revokeObjectURL(url) }
  } catch (e) {
    URL.revokeObjectURL(url)
    throw e
  }
}

/** 사진을 줄여 JPEG 로. 사진이 아니거나 읽지 못하면 에러 */
export async function shrinkPhoto(file: Blob, maxSide: number, quality = 0.82): Promise<Blob> {
  if (!file.type.startsWith('image/')) throw new Error('사진만 올릴 수 있어요')
  const image = await decode(file).catch(() => {
    throw new Error('이 사진은 열 수 없어요. 다른 사진을 골라 주세요')
  })
  try {
    const size = fitWithin(image.width, image.height, maxSide)
    const canvas = document.createElement('canvas')
    canvas.width = size.width
    canvas.height = size.height
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('사진을 줄이지 못했어요')
    // 투명한 PNG 는 흰 바탕으로
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, size.width, size.height)
    ctx.drawImage(image.source, 0, 0, size.width, size.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    if (!blob) throw new Error('사진을 줄이지 못했어요')
    return blob
  } finally {
    image.close()
  }
}

/** 주소의 사진을 받아 다시 올릴 수 있게 (톡방 사진 → 추억에 올리기) */
export async function fetchPhoto(url: string): Promise<Blob> {
  const res = await fetch(url, { cache: 'force-cache' })
  if (!res.ok) throw new Error('사진을 가져오지 못했어요')
  return res.blob()
}
