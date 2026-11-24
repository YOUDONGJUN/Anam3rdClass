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
import com.anam3.auth.AuthDtos.EmailLoginRequest;
import com.anam3.auth.AuthDtos.EmailVerifyRequest;
import com.anam3.auth.AuthDtos.KakaoAppLoginRequest;
import com.anam3.auth.AuthDtos.KakaoLoginRequest;
import com.anam3.auth.AuthDtos.LegacyClaimRequest;
import com.anam3.auth.AuthDtos.PasswordSetRequest;
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

    /** 앱(안드로이드): 카카오톡으로 로그인해서 받은 카카오 액세스 토큰으로 로그인 */
    @PostMapping("/kakao/app")
    public AuthStep kakaoApp(@Valid @RequestBody KakaoAppLoginRequest req) {
        return authService.kakaoAppLogin(req);
    }

    /** 카카오 없이 시작 (가입 · 비밀번호 찾기 · 비밀번호 만들기): 학교 이메일 인증용 가입 토큰 */
    @PostMapping("/email/start")
    public AuthStep emailStart() {
        return authService.emailStart();
    }

    /** 학교 이메일 + 비밀번호로 로그인 */
    @PostMapping("/email/login")
    public AuthStep emailLogin(@Valid @RequestBody EmailLoginRequest req) {
        return authService.emailLogin(req);
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

    /** 이미 가입한 학교 이메일을 인증한 뒤 새 비밀번호 (비밀번호 찾기 · 카카오 가입자의 비밀번호 만들기) */
    @PostMapping("/password")
    public AuthStep password(@Valid @RequestBody PasswordSetRequest req) {
        return authService.setPassword(req);
    }

    /** 예전(아이디·비밀번호) 회원: 학교 이메일 인증 뒤 예전 아이디·비밀번호로 예전 계정을 잇는다 */
    @PostMapping("/legacy")
    public AuthStep legacy(@Valid @RequestBody LegacyClaimRequest req) {
        return authService.claimLegacy(req);
    }
}
