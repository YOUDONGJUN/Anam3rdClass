// react-query 훅 모음. 화면은 여기 훅만 쓰고 api() 를 직접 부르지 않는다.
import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { api } from './client'
import type {
  About,
  AdminAction,
  AdminMember,
  AdminStats,
  AuthConfig,
  AuthStep,
  Block,
  Board,
  ChatRooms,
  Contact,
  DevMember,
  FeedbackInput,
  FeedbackPending,
  FeedbackStats,
  Member,
  MeetupCreateInput,
  MeetupMemories,
  Memory,
  MemoryFeed,
  MemoryVisibility,
  MeetupDetail,
  MeetupSummary,
  MeetupUpdateInput,
  NoticeDetail,
  NoticeInput,
  NoticeSummary,
  NotificationList,
  PushType,
  RankingPeriod,
  RankingScope,
  RankingView,
  Report,
  ReportInput,
  Role,
  School,
  SettlementDue,
  SettlementInput,
  SignupOptions,
} from './types'
import { askPushOnce } from '../lib/native'
import { uploadPhotos } from './uploads'

export const keys = {
  meetups: (scope: 'upcoming' | 'past', board: Board, limit = PAST_PAGE) => ['meetups', scope, board, limit] as const,
  myWaits: ['my-waits'] as const,
  meetup: (id: number) => ['meetup', id] as const,
  myMeetups: ['my-meetups'] as const,
  notices: ['notices'] as const,
  notice: (id: number) => ['notice', id] as const,
  blocks: ['blocks'] as const,
  admin: ['admin'] as const,
  signupOptions: ['signup-options'] as const,
  authConfig: ['auth-config'] as const,
  devMembers: ['dev-members'] as const,
  contacts: ['contacts'] as const,
  feedbackPending: ['feedback-pending'] as const,
  places: ['places'] as const,
  ranking: (period: RankingPeriod, scope: RankingScope, school?: number | null) =>
    ['ranking', period, scope, school ?? null] as const,
  mySettlements: ['my-settlements'] as const,
  notifications: ['notifications'] as const,
  unreadNotifications: ['notifications', 'unread'] as const,
  chats: ['chats'] as const,
  memories: ['memories'] as const,
  meetupMemories: (id: number) => ['memories', 'meetup', id] as const,
}

// ---------------- 알림함 ----------------

/** 종 아이콘 숫자: 1분마다, 탭으로 돌아올 때 다시 센다 (앱은 푸시가 오면 바로) */
export function useUnreadNotifications(enabled: boolean) {
  return useQuery({
    queryKey: keys.unreadNotifications,
    queryFn: () => api<{ count: number }>('/api/me/notifications/unread-count'),
    enabled,
    refetchInterval: 60_000,
    select: (d) => d.count,
  })
}

/** 알림함: 최근 50개. 열어 둔 동안은 다시 불러오지 않는다 (새 알림 표시가 보는 중에 사라지지 않게) */
export function useNotifications() {
  return useQuery({
    queryKey: keys.notifications,
    queryFn: () => api<NotificationList>('/api/me/notifications'),
    staleTime: 0,
    refetchOnWindowFocus: false,
  })
}

/**
 * 알림함을 열어 봤다: 화면에 받은 알림까지만 읽음 (upTo = 그중 가장 최근 시각).
 * 그 사이 새로 온 알림은 안 읽은 채로 남고 종 숫자에 다시 잡힌다
 */
export function useReadAllNotifications() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (upTo: string) =>
      api<void>(`/api/me/notifications/read-all?upTo=${encodeURIComponent(upTo)}`, { method: 'POST' }),
    onSuccess: () => {
      qc.setQueryData(keys.unreadNotifications, { count: 0 })
      void qc.invalidateQueries({ queryKey: keys.unreadNotifications })
    },
  })
}

// ---------------- 로그인 · 가입 (로그인 없이 호출) ----------------

/** 가입할 수 있는 학교와 학교마다 고를 수 있는 기수(1 ~ 최신)·학과 */
export function useSignupOptions() {
  return useQuery({
    queryKey: keys.signupOptions,
    queryFn: () => api<SignupOptions>('/api/auth/signup-options'),
    staleTime: 10 * 60_000,
  })
}

/** 카카오 REST API 키, 개발용 로그인 사용 여부, 문의 이메일 */
export function useAuthConfig() {
  return useQuery({
    queryKey: keys.authConfig,
    queryFn: () => api<AuthConfig>('/api/auth/config'),
    staleTime: Infinity,
  })
}

/** 로컬 개발 서버에서만: 샘플 계정 목록 */
export function useDevMembers(enabled: boolean) {
  return useQuery({
    queryKey: keys.devMembers,
    queryFn: () => api<DevMember[]>('/api/auth/dev/members'),
    enabled,
  })
}

// ---------------- 번개 ----------------

/** 지난 3교시·내 3교시를 한 번에 더 불러오는 개수 ('더 보기') */
export const PAST_PAGE = 30
export const MINE_PAGE = 100

/** board: school(우리 학교) · all(연합). limit: 지난 3교시 개수 ('더 보기'로 늘린다) */
export function useMeetups(scope: 'upcoming' | 'past', board: Board, limit = PAST_PAGE) {
  return useQuery({
    queryKey: keys.meetups(scope, board, limit),
    queryFn: () => api<MeetupSummary[]>(`/api/meetups?scope=${scope}&board=${board}&limit=${limit}`),
    refetchInterval: scope === 'upcoming' ? 60_000 : false,
    placeholderData: (prev) => prev, // 더 보기를 누르는 동안 보던 목록을 그대로 둔다
  })
}

export function useMeetup(id: number) {
  return useQuery({
    queryKey: keys.meetup(id),
    queryFn: () => api<MeetupDetail>(`/api/meetups/${id}`),
    refetchInterval: 30_000,
  })
}

/** 내가 열었거나 참여한 3교시. limit: 최근 것부터 ('더 보기'로 늘린다. 시간 겹침 검사는 기본 개수로 충분하다) */
export function useMyMeetups(limit = MINE_PAGE) {
  return useQuery({
    queryKey: limit === MINE_PAGE ? keys.myMeetups : [...keys.myMeetups, limit],
    queryFn: () => api<MeetupSummary[]>(`/api/me/meetups${limit === MINE_PAGE ? '' : `?limit=${limit}`}`),
    placeholderData: (prev) => prev,
  })
}

/** 빈자리를 기다리는 3교시 */
export function useMyWaits() {
  return useQuery({ queryKey: keys.myWaits, queryFn: () => api<MeetupSummary[]>('/api/me/waits') })
}

/** 연 사람 도구: 참여자 내보내기 · 연 사람 넘기기 (ref: 참여 번호) */
export function useHostTool() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ref, kind }: { id: number; ref: number; kind: 'kick' | 'transfer' }) =>
      kind === 'kick'
        ? api<MeetupDetail>(`/api/meetups/${id}/participants/${ref}`, { method: 'DELETE' })
        : api<MeetupDetail>(`/api/meetups/${id}/host`, { method: 'POST', body: { ref } }),
    onSuccess: (d) => afterMeetupChange(qc, d),
  })
}

function afterMeetupChange(qc: QueryClient, detail: MeetupDetail) {
  qc.setQueryData(keys.meetup(detail.id), detail)
  qc.invalidateQueries({ queryKey: ['meetups'] })
  qc.invalidateQueries({ queryKey: keys.myMeetups })
  qc.invalidateQueries({ queryKey: keys.admin })
  qc.invalidateQueries({ queryKey: keys.places })
  qc.invalidateQueries({ queryKey: keys.mySettlements })
  qc.invalidateQueries({ queryKey: keys.myWaits })
}

export function useCreateMeetup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: MeetupCreateInput) => api<MeetupDetail>('/api/meetups', { method: 'POST', body: input }),
    onSuccess: (d) => {
      afterMeetupChange(qc, d)
      askPushOnce()
    },
  })
}

export function useUpdateMeetup(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: MeetupUpdateInput) => api<MeetupDetail>(`/api/meetups/${id}`, { method: 'PUT', body: input }),
    onSuccess: (d) => afterMeetupChange(qc, d),
  })
}

export type MeetupActionKind = 'join' | 'leave' | 'cancel' | 'confirm' | 'wait' | 'unwait'

export function useMeetupAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: MeetupActionKind }) => {
      if (action === 'join') return api<MeetupDetail>(`/api/meetups/${id}/join`, { method: 'POST' })
      if (action === 'leave') return api<MeetupDetail>(`/api/meetups/${id}/join`, { method: 'DELETE' })
      // 빈자리 대기 (자리 나면 알림) 신청 · 취소
      if (action === 'wait') return api<MeetupDetail>(`/api/meetups/${id}/wait`, { method: 'POST' })
      if (action === 'unwait') return api<MeetupDetail>(`/api/meetups/${id}/wait`, { method: 'DELETE' })
      // 지금 인원으로 확정 (무산된 3교시도 모임 전이면 되살린다)
      if (action === 'confirm') return api<MeetupDetail>(`/api/meetups/${id}/confirm`, { method: 'POST' })
      return api<MeetupDetail>(`/api/meetups/${id}/cancel`, { method: 'POST' })
    },
    // 30초 자동 새로고침이 가는 중이면 멈춘다 — 옛 응답이 방금 참여한 상태를 덮어쓰지 않게
    onMutate: ({ id }) => qc.cancelQueries({ queryKey: keys.meetup(id) }),
    onSuccess: (d, { action }) => {
      afterMeetupChange(qc, d)
      if (action === 'join' || action === 'wait') askPushOnce()
    },
  })
}

/** 정원 늘리기 (빈자리를 기다리는 사람을 태우려고) */
export function useAddSeats() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, maxPeople }: { id: number; maxPeople: number }) =>
      api<MeetupDetail>(`/api/meetups/${id}/seats`, { method: 'PUT', body: { maxPeople } }),
    onSuccess: (d) => afterMeetupChange(qc, d),
  })
}

// ---------------- 정산 ----------------

export type SettlementAction =
  | { kind: 'create' | 'update'; input: SettlementInput }
  | { kind: 'cancel' | 'remind' }
  /** 보냈어요 (내 몫) */
  | { kind: 'sent'; sent: boolean }
  /** 받았어요 (계산한 사람이 한 사람 몫을 표시) */
  | { kind: 'share'; ref: number; sent: boolean }

/** 정산 만들기 · 고치기 · 취소 · 다시 알리기 · 보냄 표시 → 바뀐 3교시 상세 */
export function useSettlementAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: SettlementAction }) => {
      const base = `/api/meetups/${id}/settlement`
      switch (action.kind) {
        case 'create':
          return api<MeetupDetail>(base, { method: 'POST', body: action.input })
        case 'update':
          return api<MeetupDetail>(base, { method: 'PUT', body: action.input })
        case 'cancel':
          return api<MeetupDetail>(base, { method: 'DELETE' })
        case 'remind':
          return api<MeetupDetail>(`${base}/remind`, { method: 'POST' })
        case 'sent':
          return api<MeetupDetail>(`${base}/sent`, { method: 'PUT', body: { sent: action.sent } })
        case 'share':
          return api<MeetupDetail>(`${base}/shares/${action.ref}`, { method: 'PUT', body: { sent: action.sent } })
      }
    },
    onSuccess: (d) => afterMeetupChange(qc, d),
  })
}

/** 내가 보낼 정산 (내 3교시 탭 배지) */
export function useMySettlements(enabled = true) {
  return useQuery({
    queryKey: keys.mySettlements,
    queryFn: () => api<SettlementDue[]>('/api/me/settlements'),
    enabled,
    staleTime: 60_000,
  })
}

/** 송금 링크 (비우면 지운다) → 바뀐 내 정보 */
export function usePayLinks() {
  const qc = useQueryClient()
  const { updateMember } = useAuth()
  return useMutation({
    mutationFn: (input: { tossId: string; kakaoPayUrl: string }) =>
      api<Member>('/api/me/pay-links', { method: 'PUT', body: input }),
    onSuccess: (m) => {
      updateMember(m)
      // 내가 계산한 정산의 송금 버튼이 바뀐다
      qc.invalidateQueries({ queryKey: ['meetup'] })
    },
  })
}

/** 3교시 열기 화면의 장소 추천 (우리 학교 3교시에서 많이 쓴 장소) */
export function usePopularPlaces() {
  return useQuery({
    queryKey: keys.places,
    queryFn: () => api<string[]>('/api/meetups/places'),
    staleTime: 5 * 60_000,
  })
}

/** 3교시 지우기 (취소와 달리 목록·기록에서 아예 사라진다) */
export function useDeleteMeetup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/meetups/${id}`, { method: 'DELETE' }),
    onSuccess: (_d, id) => {
      qc.removeQueries({ queryKey: keys.meetup(id) })
      qc.invalidateQueries({ queryKey: ['meetups'] })
      qc.invalidateQueries({ queryKey: keys.myMeetups })
      qc.invalidateQueries({ queryKey: keys.admin })
      // 지운 3교시의 정산·후기가 '내 3교시' 배지·후기 창에 남지 않게
      qc.invalidateQueries({ queryKey: keys.mySettlements })
      qc.invalidateQueries({ queryKey: keys.feedbackPending })
    },
  })
}

// ---------------- 공지 ----------------

export function useNotices() {
  return useQuery({ queryKey: keys.notices, queryFn: () => api<NoticeSummary[]>('/api/notices') })
}

export function useNotice(id: number) {
  return useQuery({ queryKey: keys.notice(id), queryFn: () => api<NoticeDetail>(`/api/notices/${id}`) })
}

function afterNoticeChange(qc: QueryClient, detail?: NoticeDetail) {
  if (detail) qc.setQueryData(keys.notice(detail.id), detail)
  qc.invalidateQueries({ queryKey: keys.notices })
  qc.invalidateQueries({ queryKey: keys.admin })
}

export function useSaveNotice(id?: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: NoticeInput) =>
      id
        ? api<NoticeDetail>(`/api/notices/${id}`, { method: 'PUT', body: input })
        : api<NoticeDetail>('/api/notices', { method: 'POST', body: input }),
    onSuccess: (d) => afterNoticeChange(qc, d),
  })
}

export function useDeleteNotice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/notices/${id}`, { method: 'DELETE' }),
    onSuccess: (_d, id) => {
      qc.removeQueries({ queryKey: keys.notice(id) })
      afterNoticeChange(qc)
    },
  })
}

export function usePinNotice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, pinned }: { id: number; pinned: boolean }) =>
      api<NoticeDetail>(`/api/admin/notices/${id}/pin`, { method: 'PUT', body: { pinned } }),
    onSuccess: (d) => afterNoticeChange(qc, d),
  })
}

// ---------------- 3교시 후기 ----------------

/** 앱을 열면 물어볼 3교시. 화면이 열릴 때 한 번만 읽으면 된다 */
export function usePendingFeedback(enabled: boolean) {
  return useQuery({
    queryKey: keys.feedbackPending,
    queryFn: () => api<FeedbackPending[]>('/api/feedback/pending'),
    enabled,
    staleTime: Infinity,
  })
}

/** 후기 남기기 · 닫기 */
export function useSubmitFeedback() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: FeedbackInput }) =>
      api<void>(`/api/meetups/${id}/feedback`, { method: 'POST', body: input }),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: keys.feedbackPending })
      qc.invalidateQueries({ queryKey: keys.meetup(id) })
    },
  })
}

/** 대학원장 통계. days 가 null 이면 전체 기간 */
export function useFeedbackStats(days: number | null) {
  return useQuery({
    queryKey: [...keys.admin, 'feedback-stats', days],
    queryFn: () => api<FeedbackStats>(days === null ? '/api/admin/feedback-stats' : `/api/admin/feedback-stats?days=${days}`),
  })
}

// ---------------- 신고 · 차단 · 내 계정 ----------------

export function useReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: ReportInput) => api<void>('/api/reports', { method: 'POST', body: input }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.admin }),
  })
}

export function useBlocks() {
  return useQuery({ queryKey: keys.blocks, queryFn: () => api<Block[]>('/api/blocks') })
}

function afterBlockChange(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: keys.blocks })
  qc.invalidateQueries({ queryKey: ['meetups'] })
  qc.invalidateQueries({ queryKey: ['meetup'] })
}

/** 3교시 상세의 참여 번호(PersonView.ref)로 차단 */
export function useBlock() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ ref, left = false }: { ref: number; left?: boolean }) =>
      api<Block>('/api/blocks', { method: 'POST', body: { ref, left } }),
    onSuccess: () => afterBlockChange(qc),
  })
}

export function useUnblock() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/blocks/${id}`, { method: 'DELETE' }),
    onSuccess: () => afterBlockChange(qc),
  })
}

export function useAgreeTerms() {
  return useMutation({ mutationFn: () => api<Member>('/api/me/terms', { method: 'POST' }) })
}

/** 끌 앱 알림 종류 (보낸 목록으로 통째로 바꾼다) → 바뀐 내 정보 */
export function usePushSettings() {
  return useMutation({
    mutationFn: (off: PushType[]) => api<Member>('/api/me/push/settings', { method: 'PUT', body: { off } }),
  })
}

/** 내 학과·기수 고치기 (대학원장은 이름도) → 바뀐 내 정보 */
export function useUpdateMyProfile() {
  const qc = useQueryClient()
  const { updateMember } = useAuth()
  return useMutation({
    mutationFn: (input: { name?: string; cohort: number; departmentId: number }) =>
      api<Member>('/api/me/profile', { method: 'PUT', body: input }),
    onSuccess: (m) => {
      updateMember(m)
      // 3교시·공지·랭킹에 붙는 내 기수·학과가 바뀐다
      qc.invalidateQueries({ queryKey: ['meetups'] })
      qc.invalidateQueries({ queryKey: ['meetup'] })
      qc.invalidateQueries({ queryKey: keys.myMeetups })
      qc.invalidateQueries({ queryKey: keys.notices })
      qc.invalidateQueries({ queryKey: ['ranking'] })
      qc.invalidateQueries({ queryKey: keys.admin })
    },
  })
}

/** 랭킹에 안 나오기 → 바뀐 내 정보 */
export function useRankingSetting() {
  const qc = useQueryClient()
  const { updateMember } = useAuth()
  return useMutation({
    mutationFn: (hidden: boolean) => api<Member>('/api/me/ranking', { method: 'PUT', body: { hidden } }),
    onSuccess: (m) => {
      updateMember(m)
      qc.invalidateQueries({ queryKey: ['ranking'] })
      qc.invalidateQueries({ queryKey: ['meetup'] })
    },
  })
}

// ---------------- 랭킹 ----------------

/** school: 대학원장이 학교별로 볼 때 그 학교 (다른 사람은 서버가 무시하고 우리 학교) */
export function useRanking(period: RankingPeriod, scope: RankingScope, school?: number | null) {
  return useQuery({
    queryKey: keys.ranking(period, scope, school),
    queryFn: () =>
      api<RankingView>(`/api/rankings?period=${period}&scope=${scope}${school ? `&school=${school}` : ''}`),
    staleTime: 60_000,
  })
}

/** 이름·기수를 바꿔 달라고 요청할 곳: 우리 학교 교수 · 대학원장 (자주 바뀌지 않는다) */
export function useContacts(enabled = true) {
  return useQuery({
    queryKey: keys.contacts,
    queryFn: () => api<Contact[]>('/api/me/contacts'),
    staleTime: 10 * 60_000,
    enabled,
  })
}

/**
 * 지금 비밀번호로 새 비밀번호 바꾸기 → 새 로그인 토큰 (다른 기기는 로그아웃되고 이 기기는 그대로 쓴다)
 */
export function useChangePassword() {
  const { signIn } = useAuth()
  return useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      api<AuthStep>('/api/me/password', { method: 'PUT', body: input }),
    onSuccess: (res) => {
      if (res.token && res.member) signIn({ token: res.token, member: res.member })
    },
  })
}

/** 탈퇴 — 되돌릴 수 없다 */
export function useWithdraw() {
  return useMutation({ mutationFn: () => api<void>('/api/me', { method: 'DELETE' }) })
}

// ---------------- 관리 (교수 · 대학원장) ----------------

export function useAdminStats(enabled = true) {
  return useQuery({ queryKey: [...keys.admin, 'stats'], queryFn: () => api<AdminStats>('/api/admin/stats'), enabled })
}

export function useAdminMembers() {
  return useQuery({ queryKey: [...keys.admin, 'members'], queryFn: () => api<AdminMember[]>('/api/admin/members') })
}

export function useAdminMeetups() {
  return useQuery({ queryKey: [...keys.admin, 'meetups'], queryFn: () => api<MeetupSummary[]>('/api/admin/meetups') })
}

export function useAdminReports() {
  return useQuery({ queryKey: [...keys.admin, 'reports'], queryFn: () => api<Report[]>('/api/admin/reports') })
}

/** 관리자 조치 기록 (대학원장 전용): 최근 200건 */
export function useAdminActions(enabled = true) {
  return useQuery({
    queryKey: [...keys.admin, 'actions'],
    queryFn: () => api<AdminAction[]>('/api/admin/actions'),
    enabled,
  })
}

export function useResolveReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, resolved }: { id: number; resolved: boolean }) =>
      api<Report>(`/api/admin/reports/${id}`, { method: 'PUT', body: { resolved } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.admin }),
  })
}

/** 모든 학교와 도메인·학과 (숨긴 학과 포함) */
export function useAdminSchools() {
  return useQuery({ queryKey: [...keys.admin, 'schools'], queryFn: () => api<School[]>('/api/admin/schools') })
}

export interface SchoolInput {
  name: string
  shortName: string
  graduateSchool: string
  latestCohort: number
  /** 화면 이름의 동네 (빈칸이면 공식 이름) */
  place: string
}

export interface DepartmentInput {
  name: string
  shortName: string
  /** 비우면 학교의 대학원 */
  graduateSchool: string
  active?: boolean
}

/**
 * 관리 화면에서 학교·학과·원우 프로필을 고친 뒤: 관리 목록, 3교시·공지의 학교 배지와 학과 이름,
 * 그리고 내 정보(사이드바·내 정보·"우리 학교" 게시판 이름)까지 새로 읽는다 — 내 학교나 내 프로필을 고쳤을 수 있다
 */
function useRefreshAfterAdminEdit() {
  const qc = useQueryClient()
  const { updateMember } = useAuth()
  return () => {
    qc.invalidateQueries({ queryKey: keys.admin })
    qc.invalidateQueries({ queryKey: ['meetups'] })
    qc.invalidateQueries({ queryKey: ['meetup'] })
    qc.invalidateQueries({ queryKey: keys.myMeetups })
    qc.invalidateQueries({ queryKey: keys.notices })
    api<Member>('/api/me')
      .then(updateMember)
      .catch(() => {})
  }
}

/** 학교·도메인·학과 바꾸기 (대학원장 전용). 응답은 늘 학교 전체 목록 */
export function useSchoolAdmin() {
  const qc = useQueryClient()
  const refresh = useRefreshAfterAdminEdit()
  const done = (schools: School[]) => {
    qc.setQueryData([...keys.admin, 'schools'], schools)
    qc.invalidateQueries({ queryKey: keys.signupOptions })
    refresh()
  }
  return {
    addSchool: useMutation({
      mutationFn: (input: SchoolInput & { emailDomain: string }) =>
        api<School[]>('/api/admin/schools', { method: 'POST', body: input }),
      onSuccess: done,
    }),
    updateSchool: useMutation({
      mutationFn: ({ id, ...input }: SchoolInput & { id: number }) =>
        api<School[]>(`/api/admin/schools/${id}`, { method: 'PUT', body: input }),
      onSuccess: done,
    }),
    addDomain: useMutation({
      mutationFn: ({ schoolId, domain }: { schoolId: number; domain: string }) =>
        api<School[]>(`/api/admin/schools/${schoolId}/domains`, { method: 'POST', body: { domain } }),
      onSuccess: done,
    }),
    removeDomain: useMutation({
      mutationFn: ({ schoolId, domainId }: { schoolId: number; domainId: number }) =>
        api<School[]>(`/api/admin/schools/${schoolId}/domains/${domainId}`, { method: 'DELETE' }),
      onSuccess: done,
    }),
    addDepartment: useMutation({
      mutationFn: ({ schoolId, ...input }: DepartmentInput & { schoolId: number }) =>
        api<School[]>(`/api/admin/schools/${schoolId}/departments`, { method: 'POST', body: input }),
      onSuccess: done,
    }),
    updateDepartment: useMutation({
      mutationFn: ({ id, ...input }: DepartmentInput & { id: number }) =>
        api<School[]>(`/api/admin/departments/${id}`, { method: 'PUT', body: input }),
      onSuccess: done,
    }),
  }
}

export function useAdminMemberAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'ban' | 'unban' }) =>
      api<AdminMember>(`/api/admin/members/${id}/${action}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.admin }),
  })
}

/** 원우의 이름·기수·학과 고치기 (원우 이름은 본인이 못 바꾸고 요청한다) */
export function useUpdateMemberProfile() {
  const refresh = useRefreshAfterAdminEdit()
  return useMutation({
    mutationFn: ({ id, ...input }: { id: number; name: string; cohort: number; departmentId: number }) =>
      api<AdminMember>(`/api/admin/members/${id}/profile`, { method: 'PUT', body: input }),
    onSuccess: refresh,
  })
}

/** 탈퇴시키기 (대학원장 전용) */
export function useWithdrawMember() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<AdminMember>(`/api/admin/members/${id}/withdraw`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.admin })
      qc.invalidateQueries({ queryKey: ['meetups'] })
    },
  })
}

/** 교수 임명·해임 (대학원장 전용) */
export function useChangeRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, role }: { id: number; role: Exclude<Role, 'OWNER'> }) =>
      api<AdminMember>(`/api/admin/members/${id}/role`, { method: 'PUT', body: { role } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.admin }),
  })
}

/** 예전 회원에게 학교 이메일 넣어 두기 (대학원장 전용) */
export function useAssignEmail() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, email }: { id: number; email: string }) =>
      api<AdminMember>(`/api/admin/members/${id}/email`, { method: 'PUT', body: { email } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.admin }),
  })
}

// ---------------- 톡방 · 추억 ----------------

/** 내 톡방 목록과 안 읽은 수. 실시간 연결이 오면 바로, 아니면 1분마다 다시 센다 (LiveBridge) */
export function useChatRooms(enabled = true) {
  return useQuery({
    queryKey: keys.chats,
    queryFn: () => api<ChatRooms>('/api/me/chats'),
    enabled,
    refetchInterval: 60_000,
  })
}

/** 추억 모아보기 (20개씩 더 불러온다) */
export function useMemoryFeed() {
  return useInfiniteQuery({
    queryKey: keys.memories,
    queryFn: ({ pageParam }) => api<MemoryFeed>(pageParam ? `/api/memories?before=${pageParam}` : '/api/memories'),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextBefore,
  })
}

/** 3교시 상세의 추억 칸 */
export function useMeetupMemories(meetupId: number, enabled = true) {
  return useQuery({
    queryKey: keys.meetupMemories(meetupId),
    queryFn: () => api<MeetupMemories>(`/api/meetups/${meetupId}/memories`),
    enabled,
  })
}

export interface MemoryInput {
  /** 이미 줄인 사진 (lib/photo shrinkPhoto) */
  photos: Blob[]
  caption: string
  visibility: MemoryVisibility
}

/** 추억 올리기: 사진을 저장소에 바로 올린 뒤 그 이름으로 추억을 만든다 */
export function useCreateMemory(meetupId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: MemoryInput) => {
      const photoKeys = await uploadPhotos(`/api/meetups/${meetupId}/memories/uploads`, input.photos)
      return api<Memory>(`/api/meetups/${meetupId}/memories`, {
        method: 'POST',
        body: { caption: input.caption, visibility: input.visibility, photoKeys },
      })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.memories }),
  })
}

export function useDeleteMemory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/memories/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.memories }),
  })
}

/** 만든 곳 정보 ("써클"을 누르면). 로그인한 원우에게만 */
export function useAbout(enabled: boolean) {
  return useQuery({ queryKey: ['about'], queryFn: () => api<About>('/api/about'), enabled, staleTime: 10 * 60_000 })
}
