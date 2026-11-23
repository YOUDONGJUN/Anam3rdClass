import type { MeetupSummary } from '../api/types'
import { statusView, type Tone } from '../lib/meetup'

const toneClass: Record<Tone, string> = {
  accent: 'bg-accent-soft text-accent-ink',
  ok: 'bg-ok-soft text-ok-ink',
  muted: 'bg-surface-2 text-ink-3',
  danger: 'bg-danger-soft text-danger-ink',
}

export function StatusPill({ meetup }: { meetup: Pick<MeetupSummary, 'status' | 'joinable'> }) {
  const { label, tone } = statusView(meetup)
  return (
    <span className={`inline-flex h-6 shrink-0 items-center rounded-md px-2 text-xs font-bold ${toneClass[tone]}`}>
      {label}
    </span>
  )
}
