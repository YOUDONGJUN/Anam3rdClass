package com.anam3.push;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Firebase Cloud Messaging (HTTP v1) 으로 안드로이드 앱에 알림을 보낸다.
 * 서비스 계정 키로 구글 접근 토큰을 받아(1시간짜리, 미리 갱신) 기기마다 한 번씩 보낸다.
 */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/{project}/messages:send";
    /** 앱의 알림 채널 (android strings.xml push_channel_id, lib/native.ts 와 같은 값) */
    static final String CHANNEL_ID = "anam3";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final String projectId;
    private final String clientEmail;
    private final String keyId;
    private final String tokenUri;
    private final PrivateKey privateKey;
    private final Clock clock;
    private final RestClient rest;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public FcmPushSender(String serviceAccount, Clock clock) {
        Map<String, Object> json = parse(serviceAccount);
        this.projectId = required(json, "project_id");
        this.clientEmail = required(json, "client_email");
        this.keyId = json.get("private_key_id") instanceof String id ? id : null;
        this.tokenUri = json.get("token_uri") instanceof String uri ? uri : "https://oauth2.googleapis.com/token";
        this.privateKey = privateKey(required(json, "private_key"));
        this.clock = clock;

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(factory).build();
        log.info("앱 푸시: Firebase 프로젝트 {} 로 보냅니다", projectId);
    }

    @Override
    public Result send(String token, PushMessage message) {
        Map<String, Object> androidNotification = new LinkedHashMap<>();
        androidNotification.put("channel_id", CHANNEL_ID);
        if (message.tag() != null) {
            androidNotification.put("tag", message.tag());
        }
        Map<String, Object> body = Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", message.title(), "body", message.body()),
                // 알림을 누르면 앱이 이 화면을 연다 (lib/native.ts)
                "data", Map.of("path", message.path(), "type", message.type().name()),
                "android", Map.of("priority", "high", "notification", androidNotification)));
        try {
            rest.post()
                    .uri(SEND_URL, projectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String detail = e.getResponseBodyAsString();
            // 앱을 지웠거나 토큰이 바뀜 (404 UNREGISTERED, 400 잘못된 토큰)
            if (status == 404 || detail.contains("UNREGISTERED")
                    || (status == 400 && detail.contains("registration token"))) {
                return Result.INVALID_TOKEN;
            }
            if (status == 401) {
                clearAccessToken();
            }
            log.warn("푸시를 보내지 못했습니다: {} {}", status, detail.length() > 300 ? detail.substring(0, 300) : detail);
            return Result.FAILED;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("푸시를 보내지 못했습니다: {}", e.getMessage());
            return Result.FAILED;
        }
    }

    /** 구글 접근 토큰 (만료 5분 전까지 다시 쓴다) */
    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt.minus(Duration.ofMinutes(5)))) {
            return accessToken;
        }
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(clientEmail)
                    .audience(tokenUri)
                    .claim("scope", SCOPE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                    .build();
            JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT);
            if (keyId != null) {
                header.keyID(keyId);
            }
            SignedJWT assertion = new SignedJWT(header.build(), claims);
            assertion.sign(new RSASSASigner(privateKey));

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", assertion.serialize());
            Map<String, Object> res = rest.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON);
            if (res == null || !(res.get("access_token") instanceof String token)) {
                throw new IllegalStateException("구글 접근 토큰 응답에 access_token 이 없어요");
            }
            long expiresIn = res.get("expires_in") instanceof Number n ? n.longValue() : 3600;
            this.accessToken = token;
            this.accessTokenExpiresAt = now.plusSeconds(expiresIn);
            return token;
        } catch (JOSEException e) {
            throw new IllegalStateException("구글 접근 토큰 요청을 만들지 못했어요", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("구글 접근 토큰을 받지 못했어요: " + e.getMessage(), e);
        }
    }

    private synchronized void clearAccessToken() {
        this.accessToken = null;
    }

    /** 환경변수에 JSON 을 그대로 넣거나 base64 로 넣을 수 있다 */
    static Map<String, Object> parse(String raw) {
        String text = raw.strip();
        try {
            if (!text.startsWith("{")) {
                text = new String(Base64.getDecoder().decode(text.replaceAll("\\s", "")), StandardCharsets.UTF_8);
            }
            return JSONObjectUtils.parse(text);
        } catch (ParseException | IllegalArgumentException e) {
            throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT 는 Firebase 서비스 계정 키(JSON) 여야 해요", e);
        }
    }

    private static String required(Map<String, Object> json, String key) {
        if (!(json.get(key) instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT 에 " + key + " 가 없어요");
        }
        return value;
    }

    private static PrivateKey privateKey(String pem) {
        String base64 = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT 의 private_key 를 읽지 못했어요", e);
        }
    }
}
