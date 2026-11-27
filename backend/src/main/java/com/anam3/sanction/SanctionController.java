package com.anam3.sanction;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.sanction.SanctionDtos.SanctionNotice;

/** 경고 받은 원우: 앱을 열면 확인할 때까지 경고 창을 보여 준다 */
@RestController
@RequestMapping("/api/me/sanction-notice")
public class SanctionController {

    private final SanctionService sanctionService;

    public SanctionController(SanctionService sanctionService) {
        this.sanctionService = sanctionService;
    }

    /** 아직 확인하지 않은 경고 (없으면 빈 응답) */
    @GetMapping
    public SanctionNotice notice(@AuthenticationPrincipal LoginMember me) {
        return sanctionService.notice(me);
    }

    @PostMapping("/{id}/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        sanctionService.acknowledge(me, id);
    }
}
