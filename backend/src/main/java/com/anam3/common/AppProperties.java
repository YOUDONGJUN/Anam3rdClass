package com.anam3.common;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 설정. 대부분 환경변수로 덮어쓴다 (README 참고).
 */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, String inviteCode, Admin admin, Cors cors) {

    public record Jwt(String secret, Duration ttl) {
    }

    /** 서버 시작 시 자동으로 만들어지는 운영자 계정 */
    public record Admin(String loginId, String password, String name, Integer admissionYear) {
    }

    /** 프론트를 다른 도메인에 따로 배포할 때만 필요 */
    public record Cors(List<String> allowedOrigins) {
    }

    public List<String> corsAllowedOrigins() {
        if (cors == null || cors.allowedOrigins() == null) {
            return List.of();
        }
        return cors.allowedOrigins().stream().filter(s -> s != null && !s.isBlank()).toList();
    }
}
