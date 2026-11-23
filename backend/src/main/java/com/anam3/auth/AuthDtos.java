package com.anam3.auth;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.anam3.member.Department;
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

            @NotNull(message = "기수를 선택해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer cohort,

            @NotNull(message = "학과를 선택해 주세요")
            Department department) {
    }

    /** 내 정보 수정 (이름, 기수, 학과) */
    public record ProfileRequest(
            @NotBlank(message = "이름을 입력해 주세요")
            @Pattern(regexp = "^[가-힣a-zA-Z ]{2,20}$", message = "이름은 한글 또는 영문 2~20자로 입력해 주세요")
            String name,

            @NotNull(message = "기수를 선택해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer cohort,

            @NotNull(message = "학과를 선택해 주세요")
            Department department) {
    }

    /** 가입 화면에서 고를 수 있는 기수·학과 */
    public record SignupOptions(int latestCohort, List<DepartmentOption> departments) {
    }

    public record DepartmentOption(Department code, String label) {
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
