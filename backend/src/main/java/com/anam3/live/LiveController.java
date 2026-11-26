package com.anam3.live;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.anam3.auth.LoginMember;

/** 실시간 연결. 화면은 Authorization 머리글을 실어야 해서 EventSource 대신 fetch 로 읽는다 (lib/live.ts) */
@RestController
public class LiveController {

    private final LiveHub hub;

    public LiveController(LiveHub hub) {
        this.hub = hub;
    }

    @GetMapping(value = "/api/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@AuthenticationPrincipal LoginMember me, HttpServletResponse response) {
        // 중간 프록시가 모아 두지 말고 바로 넘기게
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return hub.connect(me.id());
    }
}
