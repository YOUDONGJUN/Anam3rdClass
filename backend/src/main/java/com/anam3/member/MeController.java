package com.anam3.member;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.AuthService;
import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupService;

/**
 * 내 정보. 이름·기수·학과는 가입한 뒤 본인이 바꿀 수 없다 (교수·대학원장에게 요청 → AdminController).
 * 약관 동의와 탈퇴도 여기서 한다.
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

    @GetMapping("/meetups")
    public List<MeetupSummary> myMeetups(@AuthenticationPrincipal LoginMember me) {
        return meetupService.mine(me);
    }

    /** 이용약관·개인정보 처리방침 동의 (예전 회원) */
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
