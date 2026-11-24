package com.anam3.push;

/** 알림 받을 기기 하나: 누구의 기기인지, 그 사람이 끈 알림 종류, 토큰 */
public record PushTarget(Long memberId, String pushOff, String token) {
}
