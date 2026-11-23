// 백엔드 응답 타입. backend/src/main/java/com/anam3/**/…Dtos.java 와 짝을 맞춘다.

export type Role = 'USER' | 'ADMIN'
export type MemberStatus = 'ACTIVE' | 'BANNED'

export type MeetupCategory = 'SOJU' | 'BEER' | 'WINE' | 'MAKGEOLLI' | 'HIGHBALL' | 'ANYTHING' | 'NO_ALCOHOL'

export type MeetupStatus = 'RECRUITING' | 'CONFIRMED' | 'FAILED' | 'DONE' | 'CANCELED'

export interface Member {
  id: number
  loginId: string
  name: string
  admissionYear: number
  admissionLabel: string
  role: Role
}

export interface AuthResponse {
  token: string
  member: Member
}

/** masked=true 면 name 은 "김*수" 처럼 가려진 이름 */
export interface PersonView {
  name: string
  admissionLabel: string
  me: boolean
  host: boolean
  masked: boolean
}

export interface MeetupSummary {
  id: number
  title: string
  place: string
  category: MeetupCategory
  meetAt: string
  deadlineAt: string
  minPeople: number
  maxPeople: number
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
  category: MeetupCategory
  meetAt: string
  deadlineAt: string
  minPeople: number
  maxPeople: number
  openChatUrl: string
}

export type MeetupUpdateInput = Pick<MeetupCreateInput, 'title' | 'description' | 'place' | 'category' | 'openChatUrl'>

export interface AuthorView {
  name: string
  admissionLabel: string | null
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
  admissionYear: number
  admissionLabel: string
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
