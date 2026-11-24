import { describe, expect, it } from 'vitest'
import { isEditingPath, mainScriptOf } from './appVersion'

describe('mainScriptOf', () => {
  it('index.html 에서 빌드 스크립트 주소를 찾는다', () => {
    const html = '<script type="module" crossorigin src="/assets/index-DZB5Qc7j.js"></script><link href="/assets/index-CPO7wL2v.css">'
    expect(mainScriptOf(html)).toBe('/assets/index-DZB5Qc7j.js')
    expect(mainScriptOf('<script type="module" src="/src/main.tsx"></script>')).toBeNull()
  })
})

describe('isEditingPath', () => {
  it('글 쓰는 화면에서는 바로 새로고침하지 않는다', () => {
    for (const p of ['/meetups/new', '/meetups/3/edit', '/notices/new', '/notices/2/edit', '/signup/email', '/me', '/admin']) {
      expect(isEditingPath(p)).toBe(true)
    }
    for (const p of ['/', '/meetups/3', '/notices', '/welcome']) {
      expect(isEditingPath(p)).toBe(false)
    }
  })
})
