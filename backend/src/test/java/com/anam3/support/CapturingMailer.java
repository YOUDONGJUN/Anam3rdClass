package com.anam3.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.anam3.auth.email.VerificationMailer;

/** 테스트용 메일: 보내는 대신 이메일별 마지막 인증코드를 기억한다 */
public class CapturingMailer implements VerificationMailer {

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();

    @Override
    public void sendCode(String to, String code) {
        lastCodes.put(to, code);
    }

    public String lastCode(String email) {
        String code = lastCodes.get(email);
        if (code == null) {
            throw new IllegalStateException(email + " 로 보낸 인증코드가 없어요");
        }
        return code;
    }

    public boolean sentTo(String email) {
        return lastCodes.containsKey(email);
    }

    public void clear() {
        lastCodes.clear();
    }
}
