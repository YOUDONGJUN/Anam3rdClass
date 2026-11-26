import type { Contact, Role } from '../api/types'

/**
 * 이름·기수를 바꿔 달라고 요청할 곳 한 줄. 원우는 우리 학교 교수와 대학원장, 교수는 대학원장만 (대학원장은 null).
 * 예: '교수 박호랑, 이안암 · 대학원장 유동준'. 직책과 이름, '·'와 앞 이름은 줄이 바뀌어도 떨어지지 않게 묶는다
 */
export function contactLine(contacts: readonly Contact[], role: Role): string | null {
  if (role === 'OWNER') return null
  const names = (r: Role) => contacts.filter((c) => c.role === r).map((c) => c.name)
  const parts: string[] = []
  const managers = role === 'USER' ? names('MANAGER') : []
  if (managers.length > 0) parts.push(`교수\u00a0${managers.join(', ')}`)
  const owners = names('OWNER')
  if (owners.length > 0) parts.push(`대학원장\u00a0${owners.join(', ')}`)
  return parts.length > 0 ? parts.join('\u00a0· ') : null
}
