import { Frown, Meh, Smile, type LucideIcon } from 'lucide-react'
import type { FeedbackRating } from '../api/types'

/** 후기 만족도 버튼 세 개 (후기 창 · 상세 화면 후기 칸이 같이 쓴다) */
export const RATING_CHOICES: { value: FeedbackRating; label: string; Icon: LucideIcon }[] = [
  { value: 'GOOD', label: '좋았어요', Icon: Smile },
  { value: 'OK', label: '괜찮았어요', Icon: Meh },
  { value: 'BAD', label: '아쉬웠어요', Icon: Frown },
]
