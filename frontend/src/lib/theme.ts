// 화면 테마: 색(학교 색 · 기본) × 밝기(자동 · 밝게 · 어둡게).
// <html data-theme data-school data-mode> 를 붙이면 index.css 의 색 변수가 바뀐다.
// index.html 의 작은 스크립트가 같은 일을 첫 화면 그리기 전에 한 번 한다 (깜빡임 방지) — 규칙을 바꾸면 같이 고친다.

export type ThemeColor = 'school' | 'basic'
export type ThemeMode = 'system' | 'light' | 'dark'

export interface ThemePrefs {
  color: ThemeColor
  mode: ThemeMode
}

/** 학교 색 테마가 있는 학교 (관리 화면에서 새로 넣은 학교는 기본 색) */
export const THEMED_SCHOOLS = ['KU', 'YONSEI', 'SKKU', 'SOGANG', 'HANYANG'] as const

const PREFS_KEY = 'anam3.theme'
const SCHOOL_KEY = 'anam3.school'

export const DEFAULT_PREFS: ThemePrefs = { color: 'school', mode: 'system' }

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function loadPrefs(): ThemePrefs {
  try {
    const raw = storage()?.getItem(PREFS_KEY)
    const p = raw ? (JSON.parse(raw) as Partial<ThemePrefs>) : {}
    return {
      color: p.color === 'basic' ? 'basic' : 'school',
      mode: p.mode === 'light' || p.mode === 'dark' ? p.mode : 'system',
    }
  } catch {
    return DEFAULT_PREFS
  }
}

export function savePrefs(prefs: ThemePrefs) {
  try {
    storage()?.setItem(PREFS_KEY, JSON.stringify(prefs))
  } catch {
    // 저장이 막혀 있어도 이번 화면에는 적용된다
  }
}

/** 마지막으로 로그인한 사람의 학교 — 로그아웃한 뒤 첫 화면도 그 학교 색으로 */
export function loadSchoolCode(): string | null {
  try {
    return storage()?.getItem(SCHOOL_KEY) ?? null
  } catch {
    return null
  }
}

export function saveSchoolCode(code: string | null) {
  try {
    if (code) storage()?.setItem(SCHOOL_KEY, code)
    else storage()?.removeItem(SCHOOL_KEY)
  } catch {
    // 무시
  }
}

export function isThemedSchool(code: string | null | undefined): code is (typeof THEMED_SCHOOLS)[number] {
  return THEMED_SCHOOLS.some((c) => c === code)
}

export function systemPrefersDark(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches === true
}

/** 실제로 붙일 값 (테스트하기 쉽게 DOM 과 떼어 둔다) */
export function resolveTheme(prefs: ThemePrefs, schoolCode: string | null, prefersDark: boolean) {
  const school = isThemedSchool(schoolCode) ? schoolCode : null
  return {
    theme: prefs.color === 'school' && school ? 'school' : 'basic',
    school,
    mode: prefs.mode === 'dark' || (prefs.mode === 'system' && prefersDark) ? 'dark' : 'light',
  } as const
}

export function applyTheme(prefs: ThemePrefs, schoolCode: string | null) {
  const root = document.documentElement
  const r = resolveTheme(prefs, schoolCode, systemPrefersDark())
  root.dataset.theme = r.theme
  root.dataset.mode = r.mode
  if (r.school) root.dataset.school = r.school
  else delete root.dataset.school

  // 휴대폰 주소창 색도 안내판 색으로
  const meta = document.querySelector('meta[name="theme-color"]')
  const board = boardColor()
  if (meta && board) meta.setAttribute('content', board)
}

/**
 * 지금 안내판 색을 rgb(…) 로. 학교 색 다크 테마는 color-mix 라서 계산값이 oklab(…) 로 나오는데,
 * 주소창 색(theme-color)은 브라우저마다 못 읽을 수 있어 캔버스에 한 점 칠해서 rgb 로 바꾼다.
 */
function boardColor(): string | null {
  if (!document.body) return null
  const probe = document.createElement('span')
  probe.style.cssText = 'position:absolute;visibility:hidden;background:var(--board)'
  document.body.appendChild(probe)
  const computed = getComputedStyle(probe).backgroundColor
  probe.remove()
  if (computed.startsWith('rgb')) return computed
  try {
    const ctx = document.createElement('canvas').getContext('2d', { willReadFrequently: true })
    if (!ctx) return null
    ctx.fillStyle = computed
    ctx.fillRect(0, 0, 1, 1)
    const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data
    return `rgb(${r}, ${g}, ${b})`
  } catch {
    return null
  }
}
