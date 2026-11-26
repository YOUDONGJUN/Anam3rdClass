import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { Camera, ImagePlus } from 'lucide-react'
import { useMeetupMemories } from '../api/queries'
import type { MeetupDetail, Memory } from '../api/types'
import { MemoryCard } from './MemoryCard'
import { MemoryComposer } from './MemoryComposer'
import { ReportDialog, type ReportTargetInfo } from './ReportDialog'
import { Button } from './ui'
import { cardClass } from './styles'

/**
 * 3교시 상세의 추억 칸: 모였던 3교시에 함께한 원우가 사진을 남기고, 남긴 사진을 본다.
 * 추억 알림을 누르면 (#memories) 여기로 내려온다
 */
export function MeetupMemoriesCard({ m, now }: { m: MeetupDetail; now: Date }) {
  const { data } = useMeetupMemories(m.id)
  const [composing, setComposing] = useState(false)
  const [reporting, setReporting] = useState<ReportTargetInfo | null>(null)
  const ref = useRef<HTMLElement>(null)
  const { hash } = useLocation()

  useEffect(() => {
    if (hash === '#memories' && data && ref.current) ref.current.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [hash, data])

  if (!data || (data.items.length === 0 && !data.canAdd)) return null
  const report = (x: Memory) => setReporting({ type: 'MEETUP', id: x.meetupId, label: `‘${x.meetupTitle}’의 추억 사진` })

  return (
    <section ref={ref} id="memories" aria-labelledby="memories-title" className="scroll-mt-20 flex flex-col gap-4">
      <div className={`${cardClass} flex flex-wrap items-center gap-x-4 gap-y-3 px-5 py-4`}>
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent-ink">
          <Camera className="size-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="memories-title" className="text-[16px] font-extrabold text-ink">
            추억 {data.items.length > 0 && <span className="tabular text-accent-ink">{data.items.length}</span>}
          </h2>
          <p className="text-sm text-ink-3">
            {data.canAdd
              ? data.photosLeft > 0
                ? `인생네컷·단체 사진을 남겨 보세요 · ${data.photosLeft}장 더 올릴 수 있어요`
                : '이 3교시의 사진 10장을 모두 채웠어요'
              : '함께한 원우가 남긴 사진이에요'}
          </p>
        </div>
        {data.canAdd && data.photosLeft > 0 && (
          <Button onClick={() => setComposing(true)}>
            <ImagePlus className="size-4" aria-hidden />
            추억 남기기
          </Button>
        )}
      </div>
      {data.items.map((x) => (
        <MemoryCard key={x.id} memory={x} now={now} showMeetup={false} onReport={report} />
      ))}
      {composing && (
        <MemoryComposer meetupId={m.id} photosLeft={data.photosLeft} onClose={() => setComposing(false)} />
      )}
      {reporting && <ReportDialog key={reporting.id} target={reporting} onClose={() => setReporting(null)} />}
    </section>
  )
}
