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

    /** 이메일 로그인 비밀번호 규칙: 영문과 숫자를 섞어 8~64자 */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 섞어 8자 이상으로 정해 주세요";

    /**
     * 화면이 카카오 로그인 창을 열 때 필요한 값. supportEmail 은 약관·문의 화면에 보이는 연락처 (없으면 null)
     */
    public record AuthConfig(String kakaoClientId, boolean devLogin, String supportEmail) {
    }

    /** 앱(안드로이드)이 카카오톡으로 로그인해서 받은 카카오 액세스 토큰 */
    public record KakaoAppLoginRequest(
            @NotBlank(message = "카카오 로그인을 다시 시도해 주세요")
            @Size(max = 1000, message = "카카오 로그인을 다시 시도해 주세요")
            String accessToken) {
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
            Boolean agreeTerms,

            /** 이메일로 가입할 때만 (카카오 없이): 이메일 로그인 비밀번호 */
            @Size(max = 64, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String password) {
    }

    /** 학교 이메일 + 비밀번호로 로그인 */
    public record EmailLoginRequest(
            @NotBlank(message = "학교 이메일을 입력해 주세요")
            @Size(max = 120, message = "이메일이 너무 길어요")
            String email,

            @NotBlank(message = "비밀번호를 입력해 주세요")
            @Size(max = 64, message = "비밀번호를 확인해 주세요")
            String password) {
    }

    /** 이미 가입한 학교 이메일을 인증한 뒤 이메일 로그인 비밀번호를 새로 정한다 (비밀번호 찾기 · 카카오 가입자의 비밀번호 만들기) */
    public record PasswordSetRequest(
            @NotBlank(message = "학교 이메일 인증부터 다시 해 주세요")
            String signupToken,

            @NotBlank(message = PASSWORD_MESSAGE)
            @Size(max = 64, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String password) {
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
     *   <li>PASSWORD_REQUIRED: 이메일로 시작했는데 이미 가입한 학교 이메일 — 새 비밀번호를 정하면 로그인된다</li>
     * </ul>
     */
    public record AuthStep(Step step, String token, MemberResponse member, String signupToken, String email,
            Long schoolId, String schoolName) {

        public enum Step {
            DONE, EMAIL_REQUIRED, PROFILE_REQUIRED, PASSWORD_REQUIRED
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

        static AuthStep passwordRequired(String signupToken, String email, Long schoolId, String schoolName) {
            return new AuthStep(Step.PASSWORD_REQUIRED, null, null, signupToken, email, schoolId, schoolName);
        }
    }
}
