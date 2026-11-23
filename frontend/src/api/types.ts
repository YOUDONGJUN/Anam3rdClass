// 백엔드 응답 타입. backend/src/main/java/com/anam3/**/…Dtos.java 와 짝을 맞춘다.

export type Role = 'USER' | 'ADMIN'
export type MemberStatus = 'ACTIVE' | 'BANNED'

export type Department = 'SOFTWARE_SECURITY' | 'BIG_DATA' | 'AI'

export type MeetupCategory = 'SOJU' | 'BEER' | 'WINE' | 'MAKGEOLLI' | 'HIGHBALL' | 'ANYTHING' | 'NO_ALCOHOL'

export type MeetupStatus = 'RECRUITING' | 'CONFIRMED' | 'FAILED' | 'DONE' | 'CANCELED'

export interface Member {
  id: number
  loginId: string
  name: string
  cohort: number
  cohortLabel: string
  /** 관리자 계정 등은 처음에 비어 있을 수 있음 */
  department: Department | null
  departmentLabel: string | null
  role: Role
}

export interface SignupOptions {
  latestCohort: number
  departments: { code: Department; label: string }[]
}

export interface AuthResponse {
  token: string
  member: Member
}

/** masked=true 면 name 은 "김*수" 처럼 가려진 이름 */
export interface PersonView {
  name: string
  cohortLabel: string
  /** 짧은 학과 이름 (소보·빅융·인융). 없을 수 있음 */
  departmentLabel: string | null
  me: boolean
  host: boolean
  /** 관리자 — 이름이 가려지지 않고 "관리자" 표시가 붙는다 */
  admin: boolean
  masked: boolean
}

export interface MeetupSummary {
  id: number
  title: string
  place: string
  /** 마실 것 (여러 개, 정해진 순서) */
  categories: MeetupCategory[]
  /** 모임 목적 태그 (친목, 학업 …). 없으면 빈 배열 */
  purposes: string[]
  meetAt: string
  deadlineAt: string
  /** null 이면 최소 인원 없음 — 몇 명이 오든 진행(열자마자 성사) */
  minPeople: number | null
  /** null 이면 인원 제한 없음 */
  maxPeople: number | null
  participantCount: number
  status: MeetupStatus
  joinable: boolean
  joined: boolean
  hosting: boolean
  host: PersonView
}

export interface MeetupDetail extends MeetupSummary {
  description: string
  participants: PersonView[]
  revealed: boolean
  openChatUrl: string | null
  hasOpenChat: boolean
  canEdit: boolean
  canCancel: boolean
  canJoin: boolean
  canLeave: boolean
  createdAt: string
}

export interface MeetupCreateInput {
  title: string
  description: string
  place: string
  categories: MeetupCategory[]
  purposes: string[]
  meetAt: string
  deadlineAt: string
  /** null 이면 최소 인원 없음 */
  minPeople: number | null
  /** null 이면 인원 제한 없음 */
  maxPeople: number | null
  openChatUrl: string
}

export type MeetupUpdateInput = Pick<
  MeetupCreateInput,
  'title' | 'description' | 'place' | 'categories' | 'purposes' | 'openChatUrl'
>

/** official=true 면 관리자 글 (실명 + "관리자" 표시) */
export interface AuthorView {
  name: string
  cohortLabel: string | null
  departmentLabel: string | null
  official: boolean
  me: boolean
  masked: boolean
}

export interface NoticeSummary {
  id: number
  title: string
  preview: string
  author: AuthorView
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface NoticeDetail {
  id: number
  title: string
  content: string
  author: AuthorView
  pinned: boolean
  canEdit: boolean
  createdAt: string
  updatedAt: string
}

export interface NoticeInput {
  title: string
  content: string
}

export interface AdminMember {
  id: number
  loginId: string
  name: string
  cohort: number
  cohortLabel: string
  departmentLabel: string | null
  role: Role
  status: MemberStatus
  createdAt: string
}

export interface AdminStats {
  members: number
  meetups: number
  confirmedMeetups: number
  notices: number
}
