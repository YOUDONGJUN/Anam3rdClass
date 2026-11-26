import { useEffect } from 'react'

/**
 * 쓰던 내용이 있는 화면에서 나가기 전에 묻기.
 * 화면 안의 [뒤로]·[돌아가기] 버튼과 안드로이드 뒤로가기는 confirmLeave() 를 거친다.
 * 새로고침·탭 닫기는 브라우저가 묻는다 (beforeunload). 웹 브라우저의 뒤로 버튼은 막을 수 없어서,
 * 3교시 열기는 따로 임시저장한다.
 */
let guard: (() => boolean) | null = null

/** 나가도 되는지. 지킬 내용이 없으면 바로 true */
export function confirmLeave(): boolean {
  return guard ? guard() : true
}

/** active 인 동안 나가기 전에 message 로 묻는다 */
export function useLeaveGuard(active: boolean, message = '쓰던 내용이 사라져요. 나갈까요?') {
  useEffect(() => {
    if (!active) return
    const ask = () => window.confirm(message)
    guard = ask
    const onBeforeUnload = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => {
      if (guard === ask) guard = null
      window.removeEventListener('beforeunload', onBeforeUnload)
    }
  }, [active, message])
}

/** sessionStorage 읽기·쓰기 (사생활 보호 창 등에서 막혀도 화면은 그대로 돈다) */
export function readDraft<T>(key: string): T | null {
  try {
    const raw = window.sessionStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export function writeDraft(key: string, value: unknown | null) {
  try {
    if (value === null) window.sessionStorage.removeItem(key)
    else window.sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 저장이 막혀 있으면 임시저장 없이 쓴다
  }
}
