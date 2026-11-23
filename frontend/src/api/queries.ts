// react-query 훅 모음. 화면은 여기 훅만 쓰고 api() 를 직접 부르지 않는다.
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  AdminMember,
  AdminStats,
  Department,
  Member,
  SignupOptions,
  MeetupCreateInput,
  MeetupDetail,
  MeetupSummary,
  MeetupUpdateInput,
  NoticeDetail,
  NoticeInput,
  NoticeSummary,
} from './types'

export const keys = {
  meetups: (scope: 'upcoming' | 'past') => ['meetups', scope] as const,
  meetup: (id: number) => ['meetup', id] as const,
  myMeetups: ['my-meetups'] as const,
  notices: ['notices'] as const,
  notice: (id: number) => ['notice', id] as const,
  admin: ['admin'] as const,
  signupOptions: ['signup-options'] as const,
}

// ---------------- 회원 ----------------

/** 가입·내 정보에서 고를 수 있는 기수(1 ~ 최신)와 학과. 로그인 없이 호출된다 */
export function useSignupOptions() {
  return useQuery({
    queryKey: keys.signupOptions,
    queryFn: () => api<SignupOptions>('/api/auth/signup-options'),
    staleTime: 10 * 60_000,
  })
}

export function useUpdateProfile() {
  return useMutation({
    mutationFn: (input: { name: string; cohort: number; department: Department }) =>
      api<Member>('/api/me/profile', { method: 'PUT', body: input }),
  })
}

// ---------------- 번개 ----------------

export function useMeetups(scope: 'upcoming' | 'past') {
  return useQuery({
    queryKey: keys.meetups(scope),
    queryFn: () => api<MeetupSummary[]>(`/api/meetups?scope=${scope}`),
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
    mutationFn: ({ id, action }: { id: number; action: 'join' | 'leave' | 'cancel' }) => {
      if (action === 'join') return api<MeetupDetail>(`/api/meetups/${id}/join`, { method: 'POST' })
      if (action === 'leave') return api<MeetupDetail>(`/api/meetups/${id}/join`, { method: 'DELETE' })
      return api<MeetupDetail>(`/api/meetups/${id}/cancel`, { method: 'POST' })
    },
    onSuccess: (d) => afterMeetupChange(qc, d),
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

// ---------------- 운영자 ----------------

export function useAdminStats() {
  return useQuery({ queryKey: [...keys.admin, 'stats'], queryFn: () => api<AdminStats>('/api/admin/stats') })
}

export function useAdminMembers() {
  return useQuery({ queryKey: [...keys.admin, 'members'], queryFn: () => api<AdminMember[]>('/api/admin/members') })
}

export function useAdminMeetups() {
  return useQuery({ queryKey: [...keys.admin, 'meetups'], queryFn: () => api<MeetupSummary[]>('/api/admin/meetups') })
}

export interface AdminSettings {
  inviteCode: string
  latestCohort: number
}

export function useAdminSettings() {
  return useQuery({
    queryKey: [...keys.admin, 'settings'],
    queryFn: () => api<AdminSettings>('/api/admin/settings'),
  })
}

export function useChangeLatestCohort() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (latestCohort: number) =>
      api<AdminSettings>('/api/admin/settings/latest-cohort', { method: 'PUT', body: { latestCohort } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [...keys.admin, 'settings'] })
      qc.invalidateQueries({ queryKey: keys.signupOptions })
    },
  })
}

export function useAdminMemberAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'ban' | 'unban' }) =>
      api<AdminMember>(`/api/admin/members/${id}/${action}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.admin }),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (id: number) =>
      api<{ temporaryPassword: string }>(`/api/admin/members/${id}/reset-password`, { method: 'POST' }),
  })
}

export function useChangeInviteCode() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (inviteCode: string) =>
      api<{ inviteCode: string }>('/api/admin/settings/invite-code', { method: 'PUT', body: { inviteCode } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...keys.admin, 'settings'] }),
  })
}
