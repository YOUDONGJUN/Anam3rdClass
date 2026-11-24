package com.anam3.common;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 설정. 대부분 환경변수로 덮어쓴다 (README 참고).
 */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Owner owner, Kakao kakao, Mail mail, Cors cors, Support support) {

    public record Jwt(String secret, Duration ttl) {
    }

    /** 대학원장(최고 관리자). 이 학교 이메일로 인증한 계정이 대학원장이 된다 */
    public record Owner(String email) {
    }

    /** 카카오 디벨로퍼스 > 앱 > 플랫폼 키 > REST API 키 / 클라이언트 시크릿 */
    public record Kakao(String restApiKey, String clientSecret) {
    }

    /** 인증 메일 보내는 사람 주소 (비우면 SMTP 아이디) */
    public record Mail(String from) {
    }

    /** 프론트를 다른 도메인에 따로 배포할 때만 필요 */
    public record Cors(List<String> allowedOrigins) {
    }

    /** 약관·문의 화면에 보이는 연락처 이메일 (앱스토어 심사에 필요) */
    public record Support(String email) {
    }

    /** 문의 이메일 (없으면 null) */
    public String supportEmail() {
        return support == null ? null : Emails.normalizeOrNull(support.email());
    }

    public List<String> corsAllowedOrigins() {
        if (cors == null || cors.allowedOrigins() == null) {
            return List.of();
        }
        return cors.allowedOrigins().stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    /** 대학원장 이메일 (소문자, 없으면 null) */
    public String ownerEmail() {
        return owner == null ? null : Emails.normalizeOrNull(owner.email());
    }
}
