package com.anam3.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.anam3.member.MemberResponse;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "입장 코드를 입력해 주세요")
            String inviteCode,

            @NotBlank(message = "아이디를 입력해 주세요")
            @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "아이디는 영문·숫자·_ 4~20자로 입력해 주세요")
            String loginId,

            @NotBlank(message = "비밀번호를 입력해 주세요")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요")
            String password,

            @NotBlank(message = "이름을 입력해 주세요")
            @Pattern(regexp = "^[가-힣a-zA-Z ]{2,20}$", message = "이름은 한글 또는 영문 2~20자로 입력해 주세요")
            String name,

            @NotNull(message = "학번(입학 연도)을 선택해 주세요")
            Integer admissionYear) {
    }

    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요") String loginId,
            @NotBlank(message = "비밀번호를 입력해 주세요") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "현재 비밀번호를 입력해 주세요") String currentPassword,
            @NotBlank(message = "새 비밀번호를 입력해 주세요")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요")
            String newPassword) {
    }

    public record AuthResponse(String token, MemberResponse member) {
    }
}
