// 백엔드 응답 타입. backend/src/main/java/com/anam3/**/…Dtos.java 와 짝을 맞춘다.

/** 원우(USER) · 교수(MANAGER) · 대학원장(OWNER, 1명). 화면 이름은 lib/person.ts 의 ROLE_LABEL */
export type Role = 'USER' | 'MANAGER' | 'OWNER'
export type MemberStatus = 'ACTIVE' | 'BANNED' | 'WITHDRAWN'

export type MeetupCategory = 'SOJU' | 'BEER' | 'WINE' | 'MAKGEOLLI' | 'HIGHBALL' | 'ANYTHING' | 'NO_ALCOHOL'

export type MeetupStatus = 'RECRUITING' | 'CONFIRMED' | 'FAILED' | 'DONE' | 'CANCELED'

/** 누구에게 여는지: 우리 학교 · 우리 기수 · 연합(다섯 학교) */
export type MeetupAudience = 'SCHOOL' | 'COHORT' | 'ALL'

/** 3교시 목록: 우리 학교 게시판 · 연합 게시판 */
export type Board = 'school' | 'all'

/**
 * 사람·3교시 옆에 붙는 학교 표시. code 로 배지·테마 색을 고른다 (lib/schools.ts).
 * code 가 null 이면 관리 화면에서 새로 넣은 학교 — 기본 색
 */
export interface SchoolTag {
  id: number
  code: string | null
  name: string
  shortName: string
}

export interface Member {
  id: number
  /** 인증된 학교 이메일 (예전 회원은 아직 없을 수 있음) */
  email: string | null
  school: SchoolTag
  /** SW·AI융합대학원 */
  graduateSchool: string | null
  name: string
  cohort: number
  cohortLabel: string
  /** 예전 운영자 계정 등은 비어 있을 수 있음 */
  departmentId: number | null
  /** 인공지능융합학과 */
  departmentLabel: string | null
  /** 인융 */
  departmentShortLabel: string | null
  role: Role
  /** false 면 약관 동의 화면부터 (예전 회원) */
  termsAgreed: boolean
}

export interface Department {
  id: number
  name: string
  shortName: string
  /** 학교의 대학원과 다를 때만 (예: 연세대 공학대학원). null 이면 학교의 대학원 */
  graduateSchool: string | null
  /** false 면 숨긴 학과 (가입할 때 고를 수 없음, 관리 화면에서만 보인다) */
  active: boolean
}

export interface EmailDomain {
  id: number
  domain: string
}

/** 학교 하나의 모든 것 (가입 화면은 숨긴 학과가 빠진 채로 온다) */
export interface School extends SchoolTag {
  graduateSchool: string | null
  latestCohort: number
  emailDomains: EmailDomain[]
  departments: Department[]
}

export interface SignupOptions {
  /** 가입할 수 있는 학교 (이 도메인 이메일로만 인증코드를 받는다) */
  schools: School[]
  /** 아직 카카오로 이어지지 않은 예전(아이디·비밀번호) 회원이 있는 학교 → 그 학교 가입 화면에 "예전 계정 잇기" */
  legacySchoolIds: number[]
}

export interface AuthConfig {
  /** 카카오 REST API 키 (공개값). 설정 안 됐으면 null */
  kakaoClientId: string | null
  /** 로컬 개발 서버면 true — "개발용 로그인"을 보여준다 */
  devLogin: boolean
  /** 문의 이메일 (약관·내 정보 화면). 없으면 null */
  supportEmail: string | null
}

export interface AuthResponse {
  token: string
  member: Member
}

/**
 * 로그인·가입 단계
 * - DONE: 로그인 끝 (token, member)
 * - EMAIL_REQUIRED: 처음 온 카카오 계정 → 학교 이메일 인증 (signupToken)
 * - PROFILE_REQUIRED: 이메일 인증 끝 → 이름·기수·학과 입력 (signupToken, email, schoolId, schoolName)
 */
export interface AuthStep {
  step: 'DONE' | 'EMAIL_REQUIRED' | 'PROFILE_REQUIRED'
  token: string | null
  member: Member | null
  signupToken: string | null
  email: string | null
  schoolId: number | null
  schoolName: string | null
}

export interface EmailCodeSent {
  email: string
  schoolName: string
  expiresInSeconds: number
  resendAfterSeconds: number
}

export interface DevMember {
  id: number
  name: string
  cohortLabel: string
  departmentLabel: string | null
  school: SchoolTag
  role: Role
}

/** masked=true 면 name 은 "김*대" 처럼 가려진 이름 */
export interface PersonView {
  name: string
  cohortLabel: string
  /** 짧은 학과 이름 (소보·빅융·인융). 없을 수 있음 */
  departmentLabel: string | null
  school: SchoolTag
  me: boolean
  host: boolean
  /** 대학원장 — 이름이 가려지지 않고 "대학원장" 표시가 붙는다 */
  owner: boolean
  masked: boolean
  /** 상세 화면 참여자에게만: 이 3교시 안의 참여 번호 (신고·차단할 때 쓴다) */
  ref: number | null
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
  /** 우리 기수만 모집하면 그 기수. null 이면 누구나 */
  onlyCohort: number | null
  audience: MeetupAudience
  /** 연 사람의 학교 */
  school: SchoolTag
  /** 중간 합류 OK — 늦게 와도 되고, 성사 뒤에도 시작 전까지 참여 */
  lateJoin: boolean
  /** 참여비(원). null 이면 없음 */
  fee: number | null
  /** 참여비 선입금 */
  feePrepaid: boolean
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
  /** 지우기: 연 사람 · 그 학교 교수 · 대학원장 */
  canDelete: boolean
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
  /** 우리 학교(기본) · 우리 기수 · 연합. 열 때만 정한다 */
  audience: MeetupAudience
  lateJoin: boolean
  /** null 이면 참여비 없음 */
  fee: number | null
  feePrepaid: boolean
}

export type MeetupUpdateInput = Pick<
  MeetupCreateInput,
  'title' | 'description' | 'place' | 'categories' | 'purposes' | 'openChatUrl'
>

/** official=true 면 대학원장 글 (실명 + "대학원장" 표시) */
export interface AuthorView {
  name: string
  cohortLabel: string | null
  departmentLabel: string | null
  school: SchoolTag
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
  /** 연합 공지 (다섯 학교 모두에게 보인다) */
  federated: boolean
  createdAt: string
  updatedAt: string
}

export interface NoticeDetail {
  id: number
  title: string
  content: string
  author: AuthorView
  pinned: boolean
  federated: boolean
  canEdit: boolean
  canPin: boolean
  createdAt: string
  updatedAt: string
}

export interface NoticeInput {
  title: string
  content: string
  /** 연합 공지로 쓰기 (대학원장만, 새로 쓸 때만) */
  federated?: boolean
}

/** 관리 화면 원우 그리드 한 줄. 교수에게는 email 이 가려져 온다 (ho**@korea.ac.kr) */
export interface AdminMember {
  id: number
  name: string
  email: string | null
  school: SchoolTag
  cohort: number
  cohortLabel: string
  departmentId: number | null
  departmentLabel: string | null
  /** 소보·빅융·인융 */
  departmentShortLabel: string | null
  role: Role
  status: MemberStatus
  /** false 면 아직 카카오로 로그인하지 않은 예전 회원 (탈퇴한 원우도) */
  kakaoLinked: boolean
  createdAt: string
}

export interface AdminStats {
  members: number
  meetups: number
  confirmedMeetups: number
  notices: number
  /** 처리 안 된 신고 */
  openReports: number
}

// ---------------- 신고 · 차단 ----------------

export type ReportTarget = 'MEETUP' | 'NOTICE' | 'MEMBER'
export type ReportReason = 'SPAM' | 'ABUSE' | 'SEXUAL' | 'ILLEGAL' | 'PRIVACY' | 'OTHER'

export interface ReportInput {
  targetType: ReportTarget
  /** MEETUP → 3교시 번호, NOTICE → 공지 번호, MEMBER → 참여 번호(PersonView.ref) */
  targetId: number
  reason: ReportReason
  detail: string
}

/** 관리 화면 신고 한 줄. 신고한 사람은 보이지 않는다 */
export interface Report {
  id: number
  targetType: ReportTarget
  reason: ReportReason
  detail: string | null
  /** 신고 당시 제목·내용 (3교시·공지). 글이 지워지거나 고쳐져도 남는다 */
  targetSnapshot: string | null
  status: 'OPEN' | 'RESOLVED'
  createdAt: string
  resolvedAt: string | null
  meetupId: number | null
  meetupTitle: string | null
  noticeId: number | null
  noticeTitle: string | null
  memberId: number | null
  memberName: string | null
  memberAffiliation: string | null
  /** 신고당한 사람의 직책 — 대학원장·(교수에게는) 다른 교수는 막을 수 없다 */
  memberRole: Role | null
  memberBanned: boolean
  school: SchoolTag | null
}

/** 내가 차단한 사람 (이름은 가려서) */
export interface Block {
  id: number
  name: string
  cohortLabel: string
  departmentLabel: string | null
  school: SchoolTag
  createdAt: string
}
