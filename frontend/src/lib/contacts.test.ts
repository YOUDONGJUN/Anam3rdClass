import { describe, expect, it } from 'vitest'
import type { Contact, Role } from '../api/types'
import { contactLine } from './contacts'

const school = { id: 1, code: 'KU', name: '고려대학교', shortName: '고려대' }
const person = (name: string, role: Role): Contact => ({ name, role, school })

describe('contactLine', () => {
  const all = [person('박호랑', 'MANAGER'), person('이안암', 'MANAGER'), person('김고대', 'OWNER')]

  it('원우는 우리 학교 교수와 대학원장', () => {
    expect(contactLine(all, 'USER')).toBe('교수\u00a0박호랑, 이안암\u00a0· 대학원장\u00a0김고대')
  })

  it('교수는 대학원장만, 대학원장은 없다', () => {
    expect(contactLine(all, 'MANAGER')).toBe('대학원장\u00a0김고대')
    expect(contactLine(all, 'OWNER')).toBeNull()
  })

  it('교수가 없으면 대학원장만, 아무도 없으면 null', () => {
    expect(contactLine([person('김고대', 'OWNER')], 'USER')).toBe('대학원장\u00a0김고대')
    expect(contactLine([], 'USER')).toBeNull()
  })
})
