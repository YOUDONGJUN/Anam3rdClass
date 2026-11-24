package com.anam3.auth;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.anam3.common.Emails;
import com.anam3.member.MemberResponse;
import com.anam3.school.SchoolDtos.SchoolView;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * 화면이 카카오 로그인 창을 열 때 필요한 값. supportEmail 은 약관·문의 화면에 보이는 연락처 (없으면 null)
     */
    public record AuthConfig(String kakaoClientId, boolean devLogin, String supportEmail) {
    }

    /** 카카오가 /auth/kakao/callback?code=… 로 돌려준 인가 코드 */
    public record KakaoLoginRequest(
            @NotBlank(message = "카카오 로그인을 다시 시도해 주세요")
            @Size(max = 500, message = "카카오 로그인을 다시 시도해 주세요")
            String code,

            @NotBlank(message = "카카오 로그인을 다시 시도해 주세요")
            @Size(max = 300, message = "카카오 로그인을 다시 시도해 주세요")
            @Pattern(regexp = "^https?://[^\\s/]+/auth/kakao/callback$", message = "카카오 로그인을 다시 시도해 주세요")
            String redirectUri) {
    }

    public record EmailCodeRequest(
            @NotBlank(message = "카카오 로그인부터 다시 해 주세요")
            String signupToken,

            @NotBlank(message = "학교 이메일을 입력해 주세요")
            @Size(max = 120, message = "이메일이 너무 길어요")
            @Pattern(regexp = Emails.PATTERN, message = "이메일 형식을 확인해 주세요")
            String email) {
    }

    public record EmailVerifyRequest(
            @NotBlank(message = "카카오 로그인부터 다시 해 주세요")
            String signupToken,

            @NotBlank(message = "학교 이메일을 입력해 주세요")
            @Size(max = 120, message = "이메일이 너무 길어요")
            String email,

            @NotBlank(message = "인증코드를 입력해 주세요")
            @Pattern(regexp = "^\\d{6}$", message = "인증코드 숫자 6자리를 입력해 주세요")
            String code) {
    }

    /** 학교 이메일 인증까지 마친 뒤 이름·기수·학과 입력 + 약관 동의. 가입한 뒤에는 본인이 바꿀 수 없다 */
    public record SignupRequest(
            @NotBlank(message = "카카오 로그인부터 다시 해 주세요")
            String signupToken,

            @NotBlank(message = "이름을 입력해 주세요")
            @Pattern(regexp = "^[가-힣a-zA-Z ]{2,20}$", message = "이름은 한글 또는 영문 2~20자로 입력해 주세요")
            String name,

            @NotNull(message = "기수를 선택해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer cohort,

            /** 인증한 학교의 학과 */
            @NotNull(message = "학과를 선택해 주세요")
            Long departmentId,

            @NotNull(message = "이용약관에 동의해 주세요")
            @AssertTrue(message = "이용약관에 동의해 주세요")
            Boolean agreeTerms) {
    }

    /** 교수·대학원장이 원우의 이름·기수·학과를 고칠 때 */
    public record ProfileRequest(
            @NotBlank(message = "이름을 입력해 주세요")
            @Pattern(regexp = "^[가-힣a-zA-Z ]{2,20}$", message = "이름은 한글 또는 영문 2~20자로 입력해 주세요")
            String name,

            @NotNull(message = "기수를 선택해 주세요")
            @Min(value = 1, message = "기수를 확인해 주세요")
            @Max(value = 999, message = "기수를 확인해 주세요")
            Integer cohort,

            /** 그 원우 학교의 학과 */
            @NotNull(message = "학과를 선택해 주세요")
            Long departmentId) {
    }

    /**
     * 가입할 수 있는 학교와 학교마다 고를 수 있는 기수(1 ~ 최신)·학과 (숨긴 학과는 빠진다).
     * legacySchoolIds: 아직 카카오로 이어지지 않은 예전(아이디·비밀번호) 회원이 있는 학교 — 그 학교 가입 화면에 "예전 계정 잇기"
     */
    public record SignupOptions(List<SchoolView> schools, List<Long> legacySchoolIds) {
    }

    /** 예전 아이디·비밀번호로 예전 계정 잇기 (학교 이메일 인증을 마친 뒤) */
    public record LegacyClaimRequest(
            @NotBlank(message = "가입 정보가 없어요. 카카오 로그인부터 다시 해 주세요")
            String signupToken,

            @NotBlank(message = "예전 아이디를 입력해 주세요")
            @Size(max = 30, message = "아이디를 확인해 주세요")
            String loginId,

            @NotBlank(message = "예전 비밀번호를 입력해 주세요")
            @Size(max = 100, message = "비밀번호를 확인해 주세요")
            String password) {
    }

    /** 인증코드를 보냈다 */
    public record EmailCodeSent(String email, String schoolName, long expiresInSeconds, long resendAfterSeconds) {
    }

    /**
     * 로그인·가입 단계의 응답.
     * <ul>
     *   <li>DONE: 로그인 끝 — token, member</li>
     *   <li>EMAIL_REQUIRED: 처음 온 카카오 계정 — 학교 이메일 인증이 필요하다 (signupToken)</li>
     *   <li>PROFILE_REQUIRED: 이메일 인증 끝 — 이름·기수·학과 입력이 필요하다 (signupToken, email, schoolId, schoolName)</li>
     * </ul>
     */
    public record AuthStep(Step step, String token, MemberResponse member, String signupToken, String email,
            Long schoolId, String schoolName) {

        public enum Step {
            DONE, EMAIL_REQUIRED, PROFILE_REQUIRED
        }

        static AuthStep done(String token, MemberResponse member) {
            return new AuthStep(Step.DONE, token, member, null, null, null, null);
        }

        static AuthStep emailRequired(String signupToken) {
            return new AuthStep(Step.EMAIL_REQUIRED, null, null, signupToken, null, null, null);
        }

        static AuthStep profileRequired(String signupToken, String email, Long schoolId, String schoolName) {
            return new AuthStep(Step.PROFILE_REQUIRED, null, null, signupToken, email, schoolId, schoolName);
        }
    }
}
