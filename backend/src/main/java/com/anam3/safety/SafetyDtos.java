package com.anam3.safety;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.member.MemberRole;
import com.anam3.school.SchoolDtos.SchoolTag;

public final class SafetyDtos {

    private SafetyDtos() {
    }

    /**
     * 신고하기. targetId 는 대상마다 다르다:
     * MEETUP → 3교시 번호, NOTICE → 공지 번호, MEMBER → 3교시 상세 화면의 참여 번호(PersonView.ref)
     */
    public record ReportRequest(
            @NotNull(message = "무엇을 신고할지 골라 주세요")
            Report.TargetType targetType,

            @NotNull(message = "무엇을 신고할지 골라 주세요")
            Long targetId,

            @NotNull(message = "신고 이유를 골라 주세요")
            Report.Reason reason,

            @Size(max = 500, message = "자세한 내용은 500자까지 쓸 수 있어요")
            String detail,

            /** 3교시 후기에서 '아쉬웠어요' 뒤 사람 신고. 정말 그렇게 답했을 때만 표시가 붙는다 */
            Boolean fromFeedback,

            /** MEMBER 신고에서 targetId 가 성사된 뒤 나간 원우의 기록 번호(leftPeople.ref)인지 */
            Boolean left,

            /** MEMBER 신고에서 targetId 가 톡방 메시지 번호인지 — 보낸 원우를 신고하고 메시지 내용을 기록에 남긴다 */
            Boolean chat) {
    }

    /**
     * 차단하기: 3교시 상세 화면의 참여 번호(PersonView.ref). left=true 면 나간 원우의 기록 번호,
     * chat=true 면 톡방 메시지 번호 (보낸 원우를 차단)
     */
    public record BlockRequest(@NotNull(message = "차단할 사람을 골라 주세요") Long ref, Boolean left, Boolean chat) {
    }

    /** 내가 차단한 사람 (이름은 가려서) */
    public record BlockView(Long id, String name, String cohortLabel, String departmentLabel, SchoolTag school,
            Instant createdAt) {
    }

    /** 관리 화면 신고 한 줄. 신고한 사람은 보여주지 않는다 */
    public record ReportView(Long id, Report.TargetType targetType, Report.Reason reason, String detail,
            /** 신고 당시 제목·내용 (3교시·공지·톡방 메시지). 그 밖의 사람 신고는 null */
            String targetSnapshot,
            Report.Status status, Instant createdAt, Instant resolvedAt,
            /** 3교시 신고, 사람 신고(그 3교시) */
            Long meetupId, String meetupTitle,
            /** 공지 신고 (지워졌으면 null) */
            Long noticeId, String noticeTitle,
            /** 신고당한 사람 — 관리 화면이라 실명 */
            Long memberId, String memberName, String memberAffiliation,
            /** 신고당한 사람의 직책 — 대학원장·교수는 화면에서 "이용 차단" 버튼을 감춘다 */
            MemberRole memberRole, boolean memberBanned,
            SchoolTag school,
            /** 3교시 후기에서 한 신고 — 대학원장에게만 true 로 내려간다 (교수에게는 늘 false) */
            boolean fromFeedback,
            /** 최근 30일 동안 신고당한 사람을 신고한 서로 다른 원우 수 (3명 이상이면 화면에 "주의") */
            int reporterCount,
            /** 신고당한 사람이 지금까지 받은 경고 수 (취소한 것 빼고) — 다음 경고가 몇 단계인지 */
            int memberStrikes,
            /** 신고당한 사람의 기간 정지가 끝나는 때 (정지 중이 아니면 null) */
            Instant memberSuspendedUntil) {
    }

    public record ResolveRequest(@NotNull Boolean resolved) {
    }
}
