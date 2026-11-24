package com.anam3.common;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 설정. 대부분 환경변수로 덮어쓴다 (README 참고).
 */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Owner owner, Kakao kakao, Mail mail, Cors cors, Support support, Push push,
        Review review, Android android) {

    public record Jwt(String secret, Duration ttl) {
    }

    /** 대학원장(최고 관리자). 이 학교 이메일로 인증한 계정이 대학원장이 된다 */
    public record Owner(String email) {
    }

    /**
     * 카카오 디벨로퍼스 > 앱 > 플랫폼 키 > REST API 키 / 클라이언트 시크릿.
     * appId(앱 ID, 숫자)는 앱(안드로이드)에서 카카오톡으로 로그인할 때 그 토큰이 우리 앱 것인지 확인하는 데 쓴다
     */
    public record Kakao(String restApiKey, String clientSecret, String appId) {
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

    /**
     * 앱 푸시 알림. firebaseServiceAccount: Firebase 서비스 계정 키(JSON 그대로 또는 base64). 없으면 보내지 않고 로그만.
     * reminders: 모집 마감 30분 전 알림을 1분마다 찾을지 (테스트에서는 끈다)
     */
    public record Push(String firebaseServiceAccount, Boolean reminders) {
    }

    /** 스토어 심사용 계정 (이메일 + 비밀번호 로그인). 둘 다 있을 때만 서버가 시작하면서 만든다 */
    public record Review(String email, String password) {
    }

    /** 안드로이드 앱 링크(공유 링크를 앱으로 열기) 확인용: 앱 패키지와 서명 인증서 SHA-256 지문 */
    public record Android(String packageName, List<String> certSha256) {
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

    /** 카카오 앱 ID (없거나 숫자가 아니면 null) */
    public Long kakaoAppId() {
        if (kakao == null || kakao.appId() == null || kakao.appId().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(kakao.appId().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Firebase 서비스 계정 키 (없으면 null) */
    public String firebaseServiceAccount() {
        return push == null || push.firebaseServiceAccount() == null || push.firebaseServiceAccount().isBlank()
                ? null : push.firebaseServiceAccount();
    }

    /** 심사용 계정 이메일 (소문자, 없으면 null) */
    public String reviewEmail() {
        return review == null ? null : Emails.normalizeOrNull(review.email());
    }

    public String reviewPassword() {
        return review == null || review.password() == null || review.password().isBlank() ? null : review.password();
    }

    public String androidPackage() {
        return android == null || android.packageName() == null || android.packageName().isBlank()
                ? "kr.anam3.app" : android.packageName().strip();
    }

    /** 앱 서명 인증서 SHA-256 지문. 콜론 유무·대소문자와 상관없이 받아 "AB:CD:…" 꼴로 맞춘다 (잘못된 값은 뺀다) */
    public List<String> androidCertSha256() {
        if (android == null || android.certSha256() == null) {
            return List.of();
        }
        return android.certSha256().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.replace(":", "").strip().toUpperCase(Locale.ROOT))
                .filter(s -> s.matches("[0-9A-F]{64}"))
                .map(s -> String.join(":", s.split("(?<=\\G..)")))
                .distinct()
                .toList();
    }
}
