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
  /** 로그인 방법: 카카오 연결 · 학교 이메일 + 비밀번호 */
  kakaoLinked: boolean
  hasPassword: boolean
  /** 끈 앱 알림 종류 */
  pushOff: PushType[]
  /** 원우가 학과·기수를 다시 고칠 수 있는 때 (30일에 한 번). null 이거나 지났으면 지금 고칠 수 있다 */
  profileEditableAt: string | null
  /** 원우가 기수를 직접 고칠 수 있는 마지막 때 (가입하고 하루). 교수·대학원장은 null = 언제나 */
  cohortEditableUntil: string | null
  /** 랭킹에 안 나오기 */
  rankingHidden: boolean
  /** 정산 송금 링크: 토스 아이디 · 카카오페이 송금 링크 (없으면 null) */
  tossId: string | null
  kakaoPayUrl: string | null
}

/** 앱 알림 종류 (서버 PushType) */
export type PushType =
  | 'JOIN'
  | 'REACHED'
  | 'DEADLINE'
  | 'REMINDER'
  | 'CHANGED'
  | 'NOTICE'
  | 'FEDERATION'
  | 'SCHOOL'
  | 'SPOT'
  | 'SETTLE'

/** 알림함의 한 줄 (푸시와 같은 내용) */
export interface NotificationItem {
  id: number
  type: PushType
  /** 3교시 제목 */
  title: string
  body: string
  /** 누르면 여는 화면 */
  path: string
  createdAt: string
  read: boolean
}

export interface NotificationList {
  items: NotificationItem[]
  unreadCount: number
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
/** PASSWORD_REQUIRED: 이메일로 시작했는데 이미 가입한 학교 이메일 — 새 비밀번호를 정하면 로그인 */
export interface AuthStep {
  step: 'DONE' | 'EMAIL_REQUIRED' | 'PROFILE_REQUIRED' | 'PASSWORD_REQUIRED'
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
  /** 이번 달 방장왕·출석왕 1등 배지 */
  crowns: RankingCategory[]
  /** 탈퇴한 원우 (이름이 "탈퇴한 원우") */
  withdrawn: boolean
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
  /** 지금 인원으로 확정 (무산된 3교시도 모임 전이면 되살리기): 연 사람 · 대학원장, 2명 이상일 때 */
  canConfirm: boolean
  /** 3교시 후기: 성사된 3교시 다음 날부터 7일 동안 함께한 사람에게만. 그 밖엔 null */
  feedback: FeedbackPrompt | null
  /** 내가 빈자리 대기 중 (자리 나면 알림) */
  waiting: boolean
  /** 빈자리 대기 신청: 정원이 찼고 아직 참여를 받는 3교시 */
  canWait: boolean
  /** 기다리는 사람 수: 연 사람·대학원장에게만 (그 밖엔 null) */
  waitCount: number | null
  /** 정원 늘리기: 연 사람·대학원장, 최대 인원이 30명 아래일 때 */
  canAddSeats: boolean
  /** 정산 만들기: 함께한 사람, 성사된 3교시가 모인 뒤 7일 안, 아직 정산이 없을 때 */
  canSettle: boolean
  /** 정산: 함께한 사람에게만 (없으면 null) */
  settlement: SettlementView | null
  /** 성사된 뒤 나간 원우 (그때 함께 있던 원우에게만). ref 는 나간 기록 번호 — 신고·차단할 때 left 와 함께 보낸다 */
  leftPeople: PersonView[]
}

/** 정산에서 한 사람 몫 (계산한 사람은 빼고) */
export interface SettlementShare {
  person: PersonView
  sent: boolean
  sentAt: string | null
}

/** 3교시 상세의 정산. 돈은 서비스가 받지 않고, 누가 보냈는지만 적는다 */
export interface SettlementView {
  /** 계산한 원우 */
  payer: PersonView
  /** 내가 계산했다 */
  mine: boolean
  total: number
  /** 1인 금액 (100원 단위 올림) */
  perPerson: number
  /** 계산한 사람 포함 나눈 인원 */
  headcount: number
  shares: SettlementShare[]
  sentCount: number
  /** 계산한 사람의 토스 링크 (1인 금액이 붙어 있다) */
  tossUrl: string | null
  /** 계산한 사람의 카카오페이 송금 링크 (금액은 직접) */
  kakaoPayUrl: string | null
  /** 고치기: 계산한 사람, 아직 아무도 안 보냈을 때 */
  editable: boolean
  /** 다시 알리기: 계산한 사람, 하루에 한 번 */
  canRemind: boolean
  /** 다시 알리기를 할 수 있는 때 (계산한 사람, 안 끝났을 때만) */
  remindableAt: string | null
  done: boolean
  createdAt: string
}

/** 정산 만들기 · 고치기. refs: 나눌 원우의 참여 번호 (participants[].ref, 나는 빼고) */
export interface SettlementInput {
  total: number
  refs: number[]
}

/** 내가 보낼 정산 (내 3교시) */
export interface SettlementDue {
  meetupId: number
  title: string
  meetAt: string
  amount: number
  payer: PersonView
  tossUrl: string | null
  kakaoPayUrl: string | null
}

// ---------------- 랭킹 ----------------

/** 방장왕 · 출석왕 · 성사율 · 연합왕 (화면 이름은 lib/ranking.ts) */
export type RankingCategory = 'HOST' | 'ATTEND' | 'RATE' | 'FEDERATION'
export type RankingPeriod = 'month' | 'all'
export type RankingScope = 'all' | 'school'

/** 랭킹 한 사람. 이름은 가려서 (내 이름·대학원장 이름만 그대로). rank 가 null 이면 랭킹에 안 나오는 중(내 기록만) */
export interface RankingEntry {
  rank: number | null
  name: string
  masked: boolean
  me: boolean
  owner: boolean
  school: SchoolTag
  departmentLabel: string | null
  /** 횟수. 성사율은 성사된 수 */
  value: number
  /** 성사율만: 연 수 */
  of: number | null
}

export interface RankingCategoryView {
  category: RankingCategory
  /** 상위 10명 */
  top: RankingEntry[]
  /** 내 기록 (없으면 null) */
  mine: RankingEntry | null
}

/** 학교 대항: 참여율 = active ÷ members */
export interface RankingSchoolRow {
  rank: number
  school: SchoolTag
  members: number
  active: number
  meetups: number
  mine: boolean
}

export interface RankingView {
  period: RankingPeriod
  scope: RankingScope
  /** 이번 달이면 1일 0시, 전체면 null */
  since: string | null
  categories: RankingCategoryView[]
  schools: RankingSchoolRow[]
}

// ---------------- 3교시 후기 ----------------

/** 좋았어요 · 괜찮았어요 · 아쉬웠어요 */
export type FeedbackRating = 'GOOD' | 'OK' | 'BAD'

/** 앱을 열면 물어볼 3교시 (가장 최근 것부터) */
export interface FeedbackPending {
  meetupId: number
  title: string
  place: string
  categories: MeetupCategory[]
  meetAt: string
  participantCount: number
  /** 이때까지만 묻는다 */
  until: string
}

/** met=false 면 rating 은 보내지 않는다. skip=true 면 답하지 않고 닫기 */
export interface FeedbackInput {
  met?: boolean
  rating?: FeedbackRating
  skip?: boolean
}

/** 상세 화면 후기 칸: until 까지. answered 면 이미 남겼다 */
export interface FeedbackPrompt {
  until: string
  answered: boolean
}

/** 대학원장 통계 묶음 하나 (학교, 또는 federation=true 면 연합). enough=false 면 모임·만족도 숫자는 null */
export interface FeedbackStatsGroup {
  school: SchoolTag | null
  federation: boolean
  /** 모임 시간이 지난 3교시 (취소 포함) */
  meetups: number
  reached: number
  /** 성사된 3교시 참여자 수 */
  participants: number
  /** 답한 후기 수 */
  answers: number
  enough: boolean
  /** 후기가 하나라도 있는 성사 3교시 */
  metKnown: number | null
  /** 그중 '모였어요'가 더 많거나 같은 것 */
  metMeetups: number | null
  good: number | null
  ok: number | null
  bad: number | null
}

export interface FeedbackStats {
  /** null 이면 전체 기간 */
  days: number | null
  /** 묶음마다 답이 이만큼은 있어야 숫자를 보여준다 */
  minAnswers: number
  total: FeedbackStatsGroup
  groups: FeedbackStatsGroup[]
  /** 후기에서 '아쉬웠어요' 뒤 한 신고 수 */
  feedbackReports: number
  /** 답이 적어 감춘 칸이 있어서 total 의 모임·만족도에서 뺐다 */
  partial: boolean
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
> & {
  /** 인원 바꾸기 (null 이면 각각 최소 인원 없음 · 최대 인원 없음) */
  people: { minPeople: number | null; maxPeople: number | null }
  /** 시간 바꾸기. 바꾸지 않았으면 null (마감이 지난 성사 3교시도 다른 칸은 고칠 수 있게) */
  schedule: { meetAt: string; deadlineAt: string } | null
  /** 대상 바꾸기 (이미 참여한 원우가 빠지게 좁힐 수는 없다) */
  audience: MeetupAudience
  /** 참여 방식 바꾸기 */
  options: { lateJoin: boolean; fee: number | null; feePrepaid: boolean }
}

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
  /** 이 학교 원우에게만 보이는 공지 (연합이면 null) */
  school: SchoolTag | null
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
  school: SchoolTag | null
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
  /** 이 학교에만 보이는 공지 (대학원장만 다른 학교를 고른다, 새로 쓸 때만). 비우면 우리 학교 */
  schoolId?: number | null
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
  /** 로그인 방법: 카카오 · 학교 이메일 + 비밀번호. 둘 다 false 면 아직 안 들어온 예전 회원 (탈퇴한 원우도) */
  kakaoLinked: boolean
  hasPassword: boolean
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
  /** 3교시 후기에서 '아쉬웠어요' 뒤 사람 신고 (서버가 정말 그렇게 답했는지 확인하고 표시한다) */
  fromFeedback?: boolean
  /** targetId 가 나간 원우의 기록 번호(leftPeople.ref)인지 */
  left?: boolean
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
  /** 3교시 후기에서 한 신고 — 대학원장에게만 true 로 온다 */
  fromFeedback: boolean
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
