package com.anam3.admin;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.anam3.member.Member;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminMemberView(Long id, String loginId, String name, int cohort, String cohortLabel,
            String departmentLabel, MemberRole role, MemberStatus status, Instant createdAt) {

        public static AdminMemberView from(Member m) {
            return new AdminMemberView(m.getId(), m.getLoginId(), m.getName(), m.getCohort(),
                    m.cohortLabel(), m.departmentLabel(), m.getRole(), m.getStatus(), m.getCreatedAt());
        }
    }

    public record SettingsView(String inviteCode, int latestCohort) {
    }

    public record LatestCohortRequest(
            @NotNull(message = "최신 기수를 입력해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer latestCohort) {
    }

    public record InviteCodeRequest(
            @NotBlank(message = "입장 코드를 입력해 주세요")
            @Size(min = 4, max = 30, message = "입장 코드는 4~30자로 정해 주세요")
            String inviteCode) {
    }

    public record TemporaryPasswordView(String temporaryPassword) {
    }

    public record StatsView(long members, long meetups, long confirmedMeetups, long notices) {
    }
}
