/** 글을 링크와 나머지로 나눈다. 주소 끝에 붙은 마침표·쉼표·닫는 괄호는 링크에서 뺀다 */
export interface TextPart {
  text: string
  url?: string
}

const URL_RE = /https?:\/\/[^\s<>"']+/g
const TRAILING = /[.,!?;:)\]}>'"」』]+$/

export function splitLinks(text: string): TextPart[] {
  const parts: TextPart[] = []
  let last = 0
  for (const match of text.matchAll(URL_RE)) {
    const start = match.index ?? 0
    let url = match[0]
    const tail = url.match(TRAILING)?.[0] ?? ''
    // "(https://a.b/c)." 처럼 주소 뒤에 붙은 문장부호는 링크에서 뺀다
    if (tail) url = url.slice(0, url.length - tail.length)
    if (start > last) parts.push({ text: text.slice(last, start) })
    parts.push({ text: url, url })
    last = start + url.length
  }
  if (last < text.length) parts.push({ text: text.slice(last) })
  return parts
}
