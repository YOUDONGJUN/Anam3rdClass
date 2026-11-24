import type { ReportReason } from '../api/types'

/** 신고 이유 (신고 창, 관리 화면 신고 목록) */
export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'ABUSE', label: '욕설·괴롭힘·혐오' },
  { value: 'SEXUAL', label: '성적인 내용' },
  { value: 'ILLEGAL', label: '불법·위험한 내용' },
  { value: 'PRIVACY', label: '개인정보 노출' },
  { value: 'OTHER', label: '기타' },
]

export const REASON_LABEL = Object.fromEntries(REPORT_REASONS.map((r) => [r.value, r.label])) as Record<
  ReportReason,
  string
>
