package com.anam3.push;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.member.MemberResponse;

/** 앱 푸시: 기기 등록·해제, 받을 알림 종류 */
@RestController
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 512) String token,
            @NotBlank @Pattern(regexp = "android|ios") String platform) {
    }

    public record UnregisterRequest(@NotBlank @Size(max = 512) String token) {
    }

    public record SettingsRequest(@NotNull(message = "끌 알림 종류를 보내 주세요") List<PushType> off) {
    }

    /** 앱을 열 때마다: 이 기기로 내 알림을 보내 주세요 */
    @PostMapping("/api/me/push/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody DeviceRequest req) {
        push.register(me, req.token(), req.platform());
    }

    /** 로그아웃: 이 기기로 더는 보내지 마세요 (로그인이 풀린 뒤에도 부를 수 있다 — SecurityConfig) */
    @PostMapping("/api/push/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@Valid @RequestBody UnregisterRequest req) {
        push.unregister(req.token());
    }

    /** 끌 알림 종류 (보낸 목록으로 통째로 바꾼다) */
    @PutMapping("/api/me/push/settings")
    public MemberResponse settings(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody SettingsRequest req) {
        return push.changeSettings(me, req.off());
    }
}
