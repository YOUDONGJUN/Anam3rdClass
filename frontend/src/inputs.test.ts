/// <reference types="node" />
// 아이폰(앱·사파리)은 글자가 16px 보다 작은 입력칸을 누르면 화면 전체를 확대한다.
// 글자를 넣는 입력칸(input · textarea · select)이 모두 16px 이상인지 화면 코드를 읽어 확인한다.
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { describe, expect, it } from 'vitest'

const SRC = fileURLToPath(new URL('.', import.meta.url))
/** 글자를 넣지 않는 입력칸 (확대되지 않는다) */
const NOT_TEXT = new Set(['checkbox', 'radio', 'file', 'hidden', 'range', 'color', 'submit', 'button', 'reset', 'image'])

function tsxFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? tsxFiles(join(dir, e.name)) : e.name.endsWith('.tsx') ? [join(dir, e.name)] : [],
  )
}

/** 16px 보다 작은 글자 크기 클래스 (text-xs · text-sm · text-[15px] …) */
function smallFont(className: string): string | null {
  const named = className.match(/(?:^|[\s`"'{:])text-(xs|sm)(?=$|[\s`"'}])/)
  if (named) return `text-${named[1]}`
  for (const m of className.matchAll(/text-\[(\d+(?:\.\d+)?)px\]/g)) {
    if (Number(m[1]) < 16) return m[0]
  }
  return null
}

/** 16px 이상으로 정했는지 (공통 inputClass 는 text-base) */
function hasFont(className: string): boolean {
  return (
    className.includes('inputClass') ||
    /(?:^|[\s`"'{])text-(base|lg|[2-9]?xl)(?=$|[\s`"'}])/.test(className) ||
    [...className.matchAll(/text-\[(\d+(?:\.\d+)?)px\]/g)].some((m) => Number(m[1]) >= 16)
  )
}

function scan(): { checked: number; found: string[] } {
  const found: string[] = []
  let checked = 0
  for (const file of tsxFiles(SRC)) {
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
    const visit = (node: ts.Node) => {
      if ((ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) && ['input', 'textarea', 'select'].includes(node.tagName.getText())) {
        const attrs = new Map(
          node.attributes.properties
            .filter(ts.isJsxAttribute)
            .map((a) => [a.name.getText(), a.initializer ? a.initializer.getText() : 'true'] as const),
        )
        const type = (attrs.get('type') ?? '').replace(/["'{}]/g, '')
        const className = attrs.get('className') ?? ''
        if (!NOT_TEXT.has(type) && !className.includes('sr-only')) {
          checked++
          const where = `${file.slice(SRC.length)}:${source.getLineAndCharacterOfPosition(node.getStart()).line + 1}`
          const small = smallFont(className)
          if (small) found.push(`${where} ${small}`)
          else if (!hasFont(className)) found.push(`${where} 글자 크기를 정하지 않음 (inputClass 또는 text-base 이상)`)
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(source)
  }
  return { checked, found }
}

describe('입력칸 글자 크기', () => {
  it('글자를 넣는 입력칸은 모두 16px 이상이다 (아이폰이 화면을 확대하지 않게)', () => {
    const { checked, found } = scan()
    expect(checked).toBeGreaterThan(30) // 화면 코드를 제대로 읽었는지 (톡방 입력창 · 검색칸 · 가입 · 관리 …)
    expect(found).toEqual([])
  })

  it('작은 글자 클래스를 알아본다', () => {
    expect(smallFont('h-11 text-[15px] text-ink')).toBe('text-[15px]')
    expect(smallFont('${inputClass} text-sm')).toBe('text-sm')
    expect(smallFont('text-ink text-base')).toBeNull()
    expect(hasFont('`${inputClass} pr-12`')).toBe(true)
    expect(hasFont('"text-ink"')).toBe(false)
  })
})
