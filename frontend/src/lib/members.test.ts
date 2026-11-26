import { describe, expect, it } from 'vitest'
import type { AdminMember, SchoolTag } from '../api/types'
import {
  countByCohort,
  countByDepartment,
  countBySchool,
  EMPTY_FILTERS,
  filterMembers,
  findDuplicates,
  isLegacy,
  sortMembers,
} from './members'

const KU: SchoolTag = { id: 1, code: 'KU', name: '고려대학교', shortName: '고려대' }
const YONSEI: SchoolTag = { id: 2, code: 'YONSEI', name: '연세대학교', shortName: '연세대' }

function member(over: Partial<AdminMember>): AdminMember {
  return {
    id: 1,
    name: '김고대',
    email: 'godae@korea.ac.kr',
    school: KU,
    cohort: 61,
    cohortLabel: '61기',
    departmentId: 3,
    departmentLabel: '인공지능융합학과',
    departmentShortLabel: '인융',
    role: 'USER',
    status: 'ACTIVE',
    kakaoLinked: true,
    hasPassword: false,
    createdAt: '2026-11-01T00:00:00Z',
    ...over,
  }
}

const list = [
  member({ id: 1, name: '김고대' }),
  member({ id: 2, name: '이안암', cohort: 60, cohortLabel: '60기', departmentId: 2, departmentLabel: '빅데이터융합학과', role: 'MANAGER' }),
  member({ id: 3, name: '박호랑', status: 'BANNED', createdAt: '2026-11-03T00:00:00Z' }),
  member({ id: 4, name: '옛회원', email: null, departmentId: null, departmentLabel: null, kakaoLinked: false }),
  member({
    id: 5,
    name: 'Kim',
    school: YONSEI,
    email: 'kim@yonsei.ac.kr',
    cohort: 3,
    cohortLabel: '3기',
    departmentId: 5,
    departmentLabel: '인공지능컴퓨팅전공',
  }),
]

describe('filterMembers', () => {
  it('조건을 모두 만족하는 원우만', () => {
    expect(filterMembers(list, { ...EMPTY_FILTERS, cohort: '61' }).map((m) => m.id)).toEqual([1, 3, 4])
    expect(filterMembers(list, { ...EMPTY_FILTERS, cohort: '61', status: 'ACTIVE' }).map((m) => m.id)).toEqual([1, 4])
    expect(filterMembers(list, { ...EMPTY_FILTERS, role: 'MANAGER' }).map((m) => m.id)).toEqual([2])
    expect(filterMembers(list, { ...EMPTY_FILTERS, school: 2 }).map((m) => m.id)).toEqual([5])
    expect(filterMembers(list, { ...EMPTY_FILTERS, department: 2 }).map((m) => m.id)).toEqual([2])
  })

  it('기수 거르기는 기수별 묶음의 key 를 그대로 쓴다 (여러 학교면 학교별 기수)', () => {
    for (const rows of [list, list.slice(0, 4)]) {
      for (const b of countByCohort(rows)) {
        expect(filterMembers(rows, { ...EMPTY_FILTERS, cohort: b.key })).toHaveLength(b.count)
      }
    }
    expect(filterMembers(list, { ...EMPTY_FILTERS, cohort: '2-3' }).map((m) => m.id)).toEqual([5])
  })

  it('탈퇴한 원우는 상태에서 "탈퇴"를 골랐을 때만 보인다', () => {
    const withGone = [...list, member({ id: 9, name: '탈퇴한 원우', status: 'WITHDRAWN' })]
    expect(filterMembers(withGone, EMPTY_FILTERS).map((m) => m.id)).not.toContain(9)
    expect(filterMembers(withGone, { ...EMPTY_FILTERS, status: 'WITHDRAWN' }).map((m) => m.id)).toEqual([9])
  })

  it('학과 미입력도 고를 수 있다', () => {
    expect(filterMembers(list, { ...EMPTY_FILTERS, department: 'none' }).map((m) => m.id)).toEqual([4])
  })

  it('검색어는 이름·이메일·기수·학과·학교에서 찾는다 (대소문자 무시)', () => {
    expect(filterMembers(list, { ...EMPTY_FILTERS, q: '빅데이터' }).map((m) => m.id)).toEqual([2])
    expect(filterMembers(list, { ...EMPTY_FILTERS, q: 'YONSEI' }).map((m) => m.id)).toEqual([5])
    expect(filterMembers(list, { ...EMPTY_FILTERS, q: '연세대' }).map((m) => m.id)).toEqual([5])
    expect(filterMembers(list, { ...EMPTY_FILTERS, q: '  ' })).toHaveLength(5)
  })
})

describe('sortMembers', () => {
  it('기수 내림차순, 같으면 이름순', () => {
    expect(sortMembers(list, 'cohort', 'desc').map((m) => m.id)).toEqual([1, 3, 4, 2, 5])
  })

  it('직책은 대학원장 → 교수 → 원우', () => {
    expect(sortMembers(list, 'role', 'asc')[0].id).toBe(2)
  })

  it('원래 목록은 건드리지 않는다', () => {
    const before = list.map((m) => m.id)
    sortMembers(list, 'name', 'asc')
    expect(list.map((m) => m.id)).toEqual(before)
  })
})

describe('통계', () => {
  it('학교별은 많은 순', () => {
    expect(countBySchool(list)).toEqual([
      { key: '1', label: '고려대', count: 4 },
      { key: '2', label: '연세대', count: 1 },
    ])
  })

  it('기수별은 학교마다 최신 기수부터 (학교가 하나면 학교 이름 없이)', () => {
    expect(countByCohort(list).map((b) => [b.label, b.count])).toEqual([
      ['고려대 61기', 3],
      ['고려대 60기', 1],
      ['연세대 3기', 1],
    ])
    expect(countByCohort(list.slice(0, 4)).map((b) => [b.label, b.count])).toEqual([
      ['61기', 3],
      ['60기', 1],
    ])
  })

  it('학과별은 많은 순, 미입력도 센다. 학교가 여럿이면 학교 이름을 붙인다', () => {
    expect(countByDepartment(list).map((b) => [b.label, b.count])).toEqual([
      ['고려대 인공지능융합학과', 2],
      ['고려대 빅데이터융합학과', 1],
      ['연세대 인공지능컴퓨팅전공', 1],
      ['학과 미입력', 1],
    ])
  })
})

describe('예전 회원 · 중복 가입 의심', () => {
  it('예전 회원은 카카오도 이메일 비밀번호도 없는 계정 (탈퇴한 원우 빼고)', () => {
    expect(isLegacy(member({ kakaoLinked: false }))).toBe(true)
    expect(isLegacy(member({ kakaoLinked: false, hasPassword: true }))).toBe(false) // 이메일로 가입
    expect(isLegacy(member({ kakaoLinked: true }))).toBe(false)
    expect(isLegacy(member({ kakaoLinked: false, status: 'WITHDRAWN' }))).toBe(false)
  })

  it('같은 학교 · 이름 · 기수 · 학과가 같으면 중복 의심 (다른 학교·탈퇴는 빼고)', () => {
    const rows = [
      member({ id: 1, name: '김고대' }),
      member({ id: 2, name: '김 고대', email: 'godae2@korea.ac.kr' }),
      member({ id: 3, name: '김고대', cohort: 60, cohortLabel: '60기' }),
      member({ id: 4, name: '김고대', school: YONSEI }),
      member({ id: 5, name: '김고대', status: 'WITHDRAWN' }),
      member({ id: 6, name: '이안암' }),
    ]
    expect(findDuplicates(rows).map((g) => g.map((m) => m.id))).toEqual([[1, 2]])
    expect(findDuplicates(list)).toEqual([])
  })
})

describe('관리 홈의 할 일에서 온 거르기', () => {
  it('중복 가입 의심 · 아직 안 들어온 예전 회원만 본다', () => {
    const rows = [
      member({ id: 1, name: '김고대' }),
      member({ id: 2, name: '김고대', email: 'godae2@korea.ac.kr' }),
      member({ id: 3, name: '이안암', kakaoLinked: false }),
      member({ id: 4, name: '박호랑', kakaoLinked: true }),
    ]
    expect(filterMembers(rows, { ...EMPTY_FILTERS, only: 'duplicates' }).map((m) => m.id)).toEqual([1, 2])
    expect(filterMembers(rows, { ...EMPTY_FILTERS, only: 'legacy' }).map((m) => m.id)).toEqual([3])
  })
})
