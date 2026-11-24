package com.anam3.auth.email;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.anam3.common.AppProperties;

/**
 * 6자리 인증코드 만들기와 해시.
 * 코드 경우의 수가 100만 개뿐이라 일반 해시는 DB 가 새면 금방 풀린다.
 * 그래서 서버 비밀키(JWT_SECRET)로 HMAC 을 걸어 저장한다 — 비밀키 없이는 맞춰 볼 수 없다.
 */
@Component
public class VerificationCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public VerificationCodes(AppProperties props) {
        String secret = props.jwt() == null ? null : props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 환경변수를 설정해 주세요");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 000000 ~ 999999 */
    public String newCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    public String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 걸리는 시간이 같도록 비교한다 */
    public boolean matches(String email, String code, String storedHash) {
        byte[] expected = storedHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(email, code).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
