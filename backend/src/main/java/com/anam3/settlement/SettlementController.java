package com.anam3.settlement;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.meetup.MeetupDtos.MeetupDetail;
import com.anam3.meetup.MeetupService;
import com.anam3.settlement.SettlementDtos.SentRequest;
import com.anam3.settlement.SettlementDtos.SettlementDue;
import com.anam3.settlement.SettlementDtos.SettlementRequest;

/** 정산. 바꾸는 요청은 바뀐 3교시 상세를 돌려준다 (다른 3교시 동작과 같다) */
@RestController
public class SettlementController {

    private final SettlementService settlementService;
    private final MeetupService meetupService;

    public SettlementController(SettlementService settlementService, MeetupService meetupService) {
        this.settlementService = settlementService;
        this.meetupService = meetupService;
    }

    /** 내가 보낼 정산 */
    @GetMapping("/api/me/settlements")
    public List<SettlementDue> dues(@AuthenticationPrincipal LoginMember me) {
        return settlementService.dues(me);
    }

    @PostMapping("/api/meetups/{id}/settlement")
    public MeetupDetail create(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody SettlementRequest req) {
        settlementService.create(me, id, req);
        return meetupService.detail(me, id);
    }

    @PutMapping("/api/meetups/{id}/settlement")
    public MeetupDetail update(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody SettlementRequest req) {
        settlementService.update(me, id, req);
        return meetupService.detail(me, id);
    }

    @DeleteMapping("/api/meetups/{id}/settlement")
    public MeetupDetail cancel(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        settlementService.cancel(me, id);
        return meetupService.detail(me, id);
    }

    @PostMapping("/api/meetups/{id}/settlement/remind")
    public MeetupDetail remind(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        settlementService.remind(me, id);
        return meetupService.detail(me, id);
    }

    /** 보냈어요 (내 몫) */
    @PutMapping("/api/meetups/{id}/settlement/sent")
    public MeetupDetail markMine(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @Valid @RequestBody SentRequest req) {
        settlementService.markMine(me, id, req.sent());
        return meetupService.detail(me, id);
    }

    /** 받았어요 (계산한 사람이 한 사람 몫을 표시) */
    @PutMapping("/api/meetups/{id}/settlement/shares/{ref}")
    public MeetupDetail markShare(@AuthenticationPrincipal LoginMember me, @PathVariable Long id,
            @PathVariable Long ref, @Valid @RequestBody SentRequest req) {
        settlementService.markShare(me, id, ref, req.sent());
        return meetupService.detail(me, id);
    }
}
