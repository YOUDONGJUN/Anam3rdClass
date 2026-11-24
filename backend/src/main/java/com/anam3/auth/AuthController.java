package com.anam3.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.AuthDtos.AuthConfig;
import com.anam3.auth.AuthDtos.AuthStep;
import com.anam3.auth.AuthDtos.EmailCodeRequest;
import com.anam3.auth.AuthDtos.EmailCodeSent;
import com.anam3.auth.AuthDtos.EmailVerifyRequest;
import com.anam3.auth.AuthDtos.KakaoLoginRequest;
import com.anam3.auth.AuthDtos.LegacyClaimRequest;
import com.anam3.auth.AuthDtos.SignupOptions;
import com.anam3.auth.AuthDtos.SignupRequest;

/** 로그인 없이 부르는 API. 순서는 AuthService 주석 참고 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 카카오 REST API 키(공개값)와 개발용 로그인 사용 여부 */
    @GetMapping("/config")
    public AuthConfig config() {
        return authService.config();
    }

    /** 가입 화면용: 고를 수 있는 기수·학과, 가입할 수 있는 학교 */
    @GetMapping("/signup-options")
    public SignupOptions signupOptions() {
        return authService.signupOptions();
    }

    @PostMapping("/kakao")
    public AuthStep kakao(@Valid @RequestBody KakaoLoginRequest req) {
        return authService.kakaoLogin(req);
    }

    @PostMapping("/email/code")
    public EmailCodeSent emailCode(@Valid @RequestBody EmailCodeRequest req) {
        return authService.sendEmailCode(req);
    }

    @PostMapping("/email/verify")
    public AuthStep emailVerify(@Valid @RequestBody EmailVerifyRequest req) {
        return authService.verifyEmail(req);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthStep signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    /** 예전(아이디·비밀번호) 회원: 학교 이메일 인증 뒤 예전 아이디·비밀번호로 예전 계정을 잇는다 */
    @PostMapping("/legacy")
    public AuthStep legacy(@Valid @RequestBody LegacyClaimRequest req) {
        return authService.claimLegacy(req);
    }
}
