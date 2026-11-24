/**
 * 새 버전 알아채기. 빌드마다 메인 스크립트 파일 이름(/assets/index-해시.js)이 바뀌니,
 * 지금 떠 있는 화면의 스크립트와 서버의 index.html 이 가리키는 스크립트를 비교한다.
 */

const MAIN_SCRIPT = /\/assets\/index-[\w-]+\.js/

/** index.html 에서 메인 스크립트 주소. 못 찾으면 null */
export function mainScriptOf(html: string): string | null {
  return html.match(MAIN_SCRIPT)?.[0] ?? null
}

/** 지금 떠 있는 화면의 메인 스크립트 주소. 개발 서버에서는 null */
export function loadedMainScript(doc: Document = document): string | null {
  for (const el of doc.querySelectorAll<HTMLScriptElement>('script[type="module"][src]')) {
    const found = mainScriptOf(el.getAttribute('src') ?? '')
    if (found) return found
  }
  return null
}

/** 쓰던 내용이 날아갈 수 있는 화면 (여기서는 바로 새로고침하지 않고 다른 화면으로 옮길 때 한다) */
export function isEditingPath(path: string) {
  return (
    /\/(new|edit)\/?$/.test(path) ||
    path.startsWith('/signup') ||
    path.startsWith('/password') ||
    path.startsWith('/login/email') ||
    path === '/me' ||
    path.startsWith('/admin')
  )
}
