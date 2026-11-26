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
import com.anam3.meetup.MeetupDtos.SeatsRequest;

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
            @RequestParam(defaultValue = "school") String board,
            @RequestParam(defaultValue = "30") int limit) {
        return meetupService.list(me, scope, board, limit);
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

    /** 지금 인원으로 확정 (무산된 3교시도 모임 전이면 되살린다): 연 사람 · 대학원장 */
    @PostMapping("/{id}/confirm")
    public MeetupDetail confirm(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.confirm(me, id);
    }

    @PostMapping("/{id}/join")
    public MeetupDetail join(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.join(me, id);
    }

    @DeleteMapping("/{id}/join")
    public MeetupDetail leave(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.leave(me, id);
    }

    /** 빈자리 대기: 정원이 찬 3교시에 "자리 나면 알림" */
    @PostMapping("/{id}/wait")
    public MeetupDetail waitForSeat(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.waitForSeat(me, id);
    }

    @DeleteMapping("/{id}/wait")
    public MeetupDetail cancelWait(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return meetupService.cancelWait(me, id);
    }

    /** 내보내기 (연 사람 · 그 학교 교수 · 대학원장). ref: 참여 번호 */
    @DeleteMapping("/{id}/participants/{ref}")
    public MeetupDetail kick(@AuthenticationPrincipal LoginMember me, @PathVariable Long id, @PathVariable Long ref) {
        return meetupService.kick(me, id, ref);
    }

    /** 연 사람 넘기기 (연 사람 · 대학원장) */
    @PostMapping("/{id}/host")
    public MeetupDetail transferHost(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody com.anam3.safety.SafetyDtos.BlockRequest req) {
        return meetupService.transferHost(me, id, req.ref());
    }

    /** 정원 늘리기 (연 사람 · 대학원장) */
    @PutMapping("/{id}/seats")
    public MeetupDetail addSeats(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody SeatsRequest req) {
        return meetupService.addSeats(me, id, req.maxPeople());
    }

    /** 3교시 열기 화면의 장소 추천: 우리 학교 3교시에서 많이 쓴 장소 */
    @GetMapping("/places")
    public List<String> places(@AuthenticationPrincipal LoginMember me) {
        return meetupService.popularPlaces(me);
    }
}
