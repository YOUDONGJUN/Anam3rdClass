/** 글 복사. 안 되는 브라우저(오래된 웹뷰)는 숨긴 입력칸으로 복사한다. 됐는지 돌려준다 */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // 아래 방법으로
  }
  try {
    const el = document.createElement('textarea')
    el.value = text
    el.setAttribute('readonly', '')
    // 16px 보다 작으면 아이폰이 화면을 확대한다
    el.style.cssText = 'position:fixed;top:0;left:0;opacity:0;font-size:16px'
    document.body.appendChild(el)
    el.select()
    const ok = document.execCommand('copy')
    el.remove()
    return ok
  } catch {
    return false
  }
}
