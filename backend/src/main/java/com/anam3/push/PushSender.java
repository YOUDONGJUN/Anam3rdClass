package com.anam3.push;

/**
 * 기기 하나에 알림 보내기. 운영은 FcmPushSender (Firebase), 설정이 없으면 LoggingPushSender,
 * 테스트는 CapturingPushSender.
 */
public interface PushSender {

    enum Result {
        SENT,
        /** 앱을 지웠거나 토큰이 바뀌어 더는 못 받는 기기 → 지운다 */
        INVALID_TOKEN,
        FAILED
    }

    Result send(String token, PushMessage message);
}
