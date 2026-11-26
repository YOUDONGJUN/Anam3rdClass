// 사진 올리기: 서버에서 올릴 주소를 받아 저장소(R2)에 바로 올린다 — 사진이 서버를 거치지 않는다
import { absoluteUrl, api, ApiError } from './client'
import type { UploadTicket } from './types'

/** 사진들을 올리고 저장소 이름(key)을 돌려준다. 하나라도 실패하면 에러 */
export async function uploadPhotos(ticketPath: string, photos: Blob[]): Promise<string[]> {
  if (photos.length === 0) return []
  const tickets = await api<UploadTicket[]>(ticketPath, {
    method: 'POST',
    body: { contentTypes: photos.map((p) => p.type || 'image/jpeg') },
  })
  await Promise.all(
    tickets.map(async (t, i) => {
      let res: Response
      try {
        res = await fetch(absoluteUrl(t.uploadUrl), {
          method: 'PUT',
          headers: { 'Content-Type': t.contentType },
          body: photos[i],
        })
      } catch {
        throw new ApiError(0, 'NETWORK', '사진을 올리지 못했어요. 인터넷 연결을 확인해 주세요')
      }
      if (!res.ok) throw new ApiError(res.status, 'UPLOAD_FAILED', '사진을 올리지 못했어요. 다시 시도해 주세요')
    }),
  )
  return tickets.map((t) => t.key)
}
