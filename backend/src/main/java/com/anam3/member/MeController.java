package com.anam3.member;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.AuthDtos.ChangePasswordRequest;
import com.anam3.auth.AuthService;
import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupService;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService authService;
    private final MeetupService meetupService;

    public MeController(AuthService authService, MeetupService meetupService) {
        this.authService = authService;
        this.meetupService = meetupService;
    }

    @GetMapping
    public MemberResponse me(@AuthenticationPrincipal LoginMember me) {
        return authService.me(me);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(me, req);
    }

    @GetMapping("/meetups")
    public List<MeetupSummary> myMeetups(@AuthenticationPrincipal LoginMember me) {
        return meetupService.mine(me);
    }
}
