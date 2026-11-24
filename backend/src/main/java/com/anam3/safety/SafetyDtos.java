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
            String detail) {
    }

    /** 차단하기: 3교시 상세 화면의 참여 번호(PersonView.ref) */
    public record BlockRequest(@NotNull(message = "차단할 사람을 골라 주세요") Long ref) {
    }

    /** 내가 차단한 사람 (이름은 가려서) */
    public record BlockView(Long id, String name, String cohortLabel, String departmentLabel, SchoolTag school,
            Instant createdAt) {
    }

    /** 관리 화면 신고 한 줄. 신고한 사람은 보여주지 않는다 */
    public record ReportView(Long id, Report.TargetType targetType, Report.Reason reason, String detail,
            /** 신고 당시 제목·내용 (3교시·공지). 사람 신고는 null */
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
            SchoolTag school) {
    }

    public record ResolveRequest(@NotNull Boolean resolved) {
    }
}
