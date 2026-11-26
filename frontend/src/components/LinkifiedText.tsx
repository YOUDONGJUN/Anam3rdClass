import { Fragment } from 'react'
import { splitLinks } from '../lib/linkify'

/** 글 속 http(s) 주소를 누를 수 있는 링크로 (설문·신청서·오픈채팅 링크 등). 나머지는 그대로 */
export function LinkifiedText({ text }: { text: string }) {
  return (
    <>
      {splitLinks(text).map((part, i) =>
        part.url ? (
          <a
            key={i}
            href={part.url}
            target="_blank"
            rel="noopener noreferrer"
            className="break-all text-accent-ink underline underline-offset-2"
          >
            {part.text}
          </a>
        ) : (
          <Fragment key={i}>{part.text}</Fragment>
        ),
      )}
    </>
  )
}
