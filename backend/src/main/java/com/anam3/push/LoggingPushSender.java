package com.anam3.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Firebase 설정(FIREBASE_SERVICE_ACCOUNT)이 없을 때: 보내지 않고 로그만 남긴다 (로컬 개발) */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public Result send(String token, PushMessage message) {
        log.info("[푸시 설정 없음 · 보내지 않음] {} | {} | {} → {}", message.type(), message.title(), message.body(),
                message.path());
        return Result.SENT;
    }
}
