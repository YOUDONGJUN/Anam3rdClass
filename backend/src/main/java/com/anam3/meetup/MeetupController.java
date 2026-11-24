package com.anam3.meetup;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupCreateRequest;
import com.anam3.meetup.MeetupDtos.MeetupDetail;
import com.anam3.meetup.MeetupDtos.MeetupSummary;
import com.anam3.meetup.MeetupDtos.MeetupUpdateRequest;

@RestController
@RequestMapping("/api/meetups")
public class MeetupController {

    private final MeetupService meetupService;

    public MeetupController(MeetupService meetupService) {
        this.meetupService = meetupService;
    }

    /** scope = upcoming | past, board = school(우리 학교) | all(연합) */
    @GetMapping
    public List<MeetupSummary> list(@AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "upcoming") String scope,
            @RequestParam(defaultValue = "school") String board) {
        return meetupService.list(me, scope, board);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetupDetail create(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody MeetupCreateRequest req) {
        return meetupService.create(me, req);
    }

    @GetMapping("/{id}")
    public MeetupDetail detail(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.detail(me, id);
    }

    @PutMapping("/{id}")
    public MeetupDetail update(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody MeetupUpdateRequest req) {
        return meetupService.update(me, id, req);
    }

    @PostMapping("/{id}/cancel")
    public MeetupDetail cancel(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.cancel(me, id);
    }

    /** 지우기: 개설자 · 그 학교 교수 · 대학원장 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        meetupService.delete(me, id);
    }

    @PostMapping("/{id}/join")
    public MeetupDetail join(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.join(me, id);
    }

    @DeleteMapping("/{id}/join")
    public MeetupDetail leave(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.leave(me, id);
    }
}
