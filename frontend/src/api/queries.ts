// react-query 훅 모음. 화면은 여기 훅만 쓰고 api() 를 직접 부르지 않는다.
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { api } from './client'
import type {
  AdminMember,
  AdminStats,
  AuthConfig,
  Block,
  Board,
  DevMember,
  FeedbackInput,
  FeedbackPending,
  FeedbackStats,
  Member,
  MeetupCreateInput,
  MeetupDetail,
  MeetupSummary,
  MeetupUpdateInput,
  NoticeDetail,
  NoticeInput,
  NoticeSummary,
  PushType,
  Report,
  ReportInput,
  Role,
  School,
  SignupOptions,
} from './types'

export const keys = {
  meetups: (scope: 'upcoming' | 'past', board: Board) => ['meetups', scope, board] as const,
  meetup: (id: number) => ['meetup', id] as const,
  myMeetups: ['my-meetups'] as const,
  notices: ['notices'] as const,
  notice: (id: number) => ['notice', id] as const,
  blocks: ['blocks'] as const,
  admin: ['admin'] as const,
  signupOptions: ['signup-options'] as const,
  authConfig: ['auth-config'] as const,
  devMembers: ['dev-members'] as const,
  feedbackPending: ['feedback-pending'] as const,
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

/** board: school(우리 학교) · all(연합) */
export function useMeetups(scope: 'upcoming' | 'past', board: Board) {
  return useQuery({
    queryKey: keys.meetups(scope, board),
    queryFn: () => api<MeetupSummary[]>(`/api/meetups?scope=${scope}&board=${board}`),
    refetchInterval: scope === 'upcoming' ? 60_000 : false,
  })
}

export function useMeetup(id: number) {
  return useQuery({
    queryKey: keys.meetup(id),
    queryFn: () => api<MeetupDetail>(`/api/meetups/${id}`),
    refetchInterval: 30_000,
  })
}

export function useMyMeetups() {
  return useQuery({ queryKey: keys.myMeetups, queryFn: () => api<MeetupSummary[]>('/api/me/meetups') })
}

function afterMeetupChange(qc: QueryClient, detail: MeetupDetail) {
  qc.setQueryData(keys.meetup(detail.id), detail)
  qc.invalidateQueries({ queryKey: ['meetups'] })
  qc.invalidateQueries({ queryKey: keys.myMeetups })
  qc.invalidateQueries({ queryKey: keys.admin })
}

export function useCreateMeetup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: MeetupCreateInput) => api<MeetupDetail>('/api/meetups', { method: 'POST', body: input }),
    onSuccess: (d) => afterMeetupChange(qc, d),
  })
}

export function useUpdateMeetup(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: MeetupUpdateInput) => api<MeetupDetail>(`/api/meetups/${id}`, { method: 'PUT', body: input }),
    onSuccess: (d) => afterMeetupChange(qc, d),
  })
}

export function useMeetupAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'join' | 'leave' | 'cancel' | 'confirm' }) => {
      if (action === 'join') return api<MeetupDetail>(`/api/meetups/${id}/join`, { method: 'POST' })
      if (action === 'leave') return api<MeetupDetail>(`/api/meetups/${id}/join`, { method: 'DELETE' })
      // 지금 인원으로 확정 (무산된 3교시도 모임 전이면 되살린다)
      if (action === 'confirm') return api<MeetupDetail>(`/api/meetups/${id}/confirm`, { method: 'POST' })
      return api<MeetupDetail>(`/api/meetups/${id}/cancel`, { method: 'POST' })
    },
    onSuccess: (d) => afterMeetupChange(qc, d),
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
    mutationFn: (ref: number) => api<Block>('/api/blocks', { method: 'POST', body: { ref } }),
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

/** 원우의 이름·기수·학과 고치기 (본인은 못 바꾸고 요청한다) */
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
