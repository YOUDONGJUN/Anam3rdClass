package com.anam3.admin;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.anam3.common.Emails;
import com.anam3.member.Member;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;
import com.anam3.school.SchoolDtos.SchoolTag;

public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * 원우 그리드 한 줄. 이메일은 대학원장에게만 그대로, 교수에게는 가려서 보낸다 (ho**@korea.ac.kr).
     * 로그인 방법: kakaoLinked(카카오) · hasPassword(학교 이메일 + 비밀번호). 둘 다 없으면 아직 안 들어온 예전 회원이다
     * (탈퇴한 원우도 둘 다 false).
     */
    public record AdminMemberView(Long id, String name, String email, SchoolTag school, int cohort,
            String cohortLabel, Long departmentId, String departmentLabel, String departmentShortLabel,
            MemberRole role, MemberStatus status, boolean kakaoLinked, boolean hasPassword, Instant createdAt) {

        public static AdminMemberView from(Member m, boolean showEmail) {
            return new AdminMemberView(m.getId(), m.getName(), showEmail ? m.getEmail() : Emails.mask(m.getEmail()),
                    SchoolTag.from(m.getSchool()), m.getCohort(), m.cohortLabel(),
                    m.getDepartment() == null ? null : m.getDepartment().getId(), m.departmentLabel(),
                    m.departmentShortLabel(), m.getRole(), m.getStatus(), m.getKakaoId() != null, m.hasEmailPassword(),
                    m.getCreatedAt());
        }
    }

    /** 교수 임명(MANAGER)·해임(USER). 대학원장은 API 로 정할 수 없다 */
    public record RoleRequest(@NotNull(message = "직책을 골라 주세요") MemberRole role) {
    }

    /** 예전 회원에게 학교 이메일을 미리 넣어 둔다 */
    public record EmailRequest(
            @NotBlank(message = "학교 이메일을 입력해 주세요")
            @Size(max = 120, message = "이메일이 너무 길어요")
            @Pattern(regexp = Emails.PATTERN, message = "이메일 형식을 확인해 주세요")
            String email) {
    }

    /** 현황. 대학원장은 모든 학교, 교수는 자기 학교 숫자 (탈퇴한 원우는 빼고 센다) */
    public record StatsView(long members, long meetups, long confirmedMeetups, long notices, long openReports) {
    }

    /**
     * 관리자 조치 기록 한 줄 (대학원장만 본다). 이름은 지금 이름 — 탈퇴했으면 "탈퇴한 원우".
     * actorRole 은 그때 직책. target… 은 대상 원우가 없으면 null, targetAffiliation 은 탈퇴했으면 null
     */
    public record AdminActionView(Long id, AdminAction.Type action, String label, String actorName,
            MemberRole actorRole, Long targetMemberId, String targetName, String targetAffiliation,
            Long targetMeetupId, String detail, Instant createdAt) {

        static AdminActionView from(AdminAction a) {
            Member t = a.getTargetMember();
            String affiliation = t == null || t.isWithdrawn() ? null
                    : t.getSchool().getShortName() + " · " + t.cohortLabel();
            return new AdminActionView(a.getId(), a.getAction(), a.getAction().label(), a.getActor().getName(),
                    a.getActorRole(), t == null ? null : t.getId(), t == null ? null : t.getName(), affiliation,
                    a.getTargetMeetupId(), a.getDetail(), a.getCreatedAt());
        }
    }
}
