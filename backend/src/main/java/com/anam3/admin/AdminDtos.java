package com.anam3.admin;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.anam3.member.Member;
import com.anam3.member.MemberRole;
import com.anam3.member.MemberStatus;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminMemberView(Long id, String loginId, String name, int admissionYear, String admissionLabel,
            MemberRole role, MemberStatus status, Instant createdAt) {

        public static AdminMemberView from(Member m) {
            return new AdminMemberView(m.getId(), m.getLoginId(), m.getName(), m.getAdmissionYear(),
                    m.admissionLabel(), m.getRole(), m.getStatus(), m.getCreatedAt());
        }
    }

    public record SettingsView(String inviteCode) {
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
