package com.anam3.member;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.AuthDtos.AuthStep;
import com.anam3.auth.AuthDtos.PasswordChangeRequest;
import com.anam3.auth.AuthService;
import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupService;
import com.anam3.member.MeDtos.ContactView;
import com.anam3.member.MeDtos.MyProfileRequest;
import com.anam3.member.MeDtos.PayLinksRequest;
import com.anam3.member.MeDtos.RankingSettingRequest;

/**
 * 내 정보. 학과·기수는 본인이 고친다 (원우는 30일에 한 번). 이름은 대학원장만 본인이 고치고,
 * 교수·원우는 요청한다 (→ AdminController). 약관 동의, 랭킹 설정, 탈퇴도 여기서 한다.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService authService;
    private final MeetupService meetupService;
    private final AccountService accountService;

    public MeController(AuthService authService, MeetupService meetupService, AccountService accountService) {
        this.authService = authService;
        this.meetupService = meetupService;
        this.accountService = accountService;
    }

    @GetMapping
    public MemberResponse me(@AuthenticationPrincipal LoginMember me) {
        return authService.me(me);
    }

    /** limit: 최근 것부터 몇 개까지 (기본 100, 화면의 '더 보기'가 늘린다) */
    @GetMapping("/meetups")
    public List<MeetupSummary> myMeetups(@AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "100") int limit) {
        return meetupService.mine(me, limit);
    }

    /** 빈자리를 기다리는 3교시 (아직 모이기 전인 것) */
    @GetMapping("/waits")
    public List<MeetupSummary> myWaits(@AuthenticationPrincipal LoginMember me) {
        return meetupService.myWaits(me);
    }

    /** 내 정보 고치기 (AccountService.updateProfile 참고) */
    @PutMapping("/profile")
    public MemberResponse updateProfile(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody MyProfileRequest req) {
        return accountService.updateProfile(me, req);
    }

    /** 이름·기수를 바꿔 달라고 요청할 곳: 우리 학교 교수와 대학원장 */
    @GetMapping("/contacts")
    public List<ContactView> contacts(@AuthenticationPrincipal LoginMember me) {
        return accountService.contacts(me);
    }

    /** 랭킹에 안 나오기 */
    @PutMapping("/ranking")
    public MemberResponse rankingSetting(@AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody RankingSettingRequest req) {
        return accountService.changeRankingHidden(me, req.hidden());
    }

    /** 송금 링크 (정산할 때 함께한 원우에게만 보인다) */
    @PutMapping("/pay-links")
    public MemberResponse payLinks(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody PayLinksRequest req) {
        return accountService.changePayLinks(me, req.tossId(), req.kakaoPayUrl());
    }

    /**
     * 지금 비밀번호로 새 비밀번호 바꾸기 (비밀번호가 있는 회원). 다른 기기는 로그아웃되고,
     * 이 기기는 응답의 새 토큰으로 이어서 쓴다. 잊었으면 학교 메일 인증(/api/auth/password)으로 찾는다
     */
    @PutMapping("/password")
    public AuthStep changePassword(@AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody PasswordChangeRequest req) {
        return authService.changePassword(me, req);
    }

    /** 이용약관·개인정보 처리방침 동의 (예전 회원, 약관이 바뀐 뒤) */
    @PostMapping("/terms")
    public MemberResponse agreeTerms(@AuthenticationPrincipal LoginMember me) {
        return accountService.agreeTerms(me);
    }

    /** 탈퇴 — 바로 처리하고 되돌릴 수 없다 (AccountService 참고) */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal LoginMember me) {
        accountService.withdraw(me);
    }
}
