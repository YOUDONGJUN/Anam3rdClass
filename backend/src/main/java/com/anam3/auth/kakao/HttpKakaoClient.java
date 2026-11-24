package com.anam3.auth.kakao;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.anam3.common.ApiException;
import com.anam3.common.AppProperties;

/**
 * 카카오 REST API 로 로그인한다. 동의항목 없이 받을 수 있는 <b>회원번호</b>만 쓴다
 * (이름·학과·기수는 가입할 때 본인이 입력하고, 학교 소속은 학교 이메일로 확인한다).
 * <ol>
 *   <li>인가 코드 → 카카오 토큰 (kauth.kakao.com/oauth/token, 클라이언트 시크릿 포함)</li>
 *   <li>카카오 토큰 → 회원번호 (kapi.kakao.com/v2/user/me)</li>
 * </ol>
 */
@Component
public class HttpKakaoClient implements KakaoClient {

    private static final Logger log = LoggerFactory.getLogger(HttpKakaoClient.class);
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;
    private final String restApiKey;
    private final String clientSecret;

    public HttpKakaoClient(AppProperties props) {
        this.restApiKey = props.kakao() == null ? null : blankToNull(props.kakao().restApiKey());
        this.clientSecret = props.kakao() == null ? null : blankToNull(props.kakao().clientSecret());

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String clientId() {
        return restApiKey;
    }

    @Override
    public long fetchUserId(String code, String redirectUri) {
        if (restApiKey == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_NOT_CONFIGURED",
                    "카카오 로그인이 아직 준비되지 않았어요. 관리자에게 알려 주세요");
        }
        try {
            String accessToken = requestAccessToken(code, redirectUri);
            Map<String, Object> user = rest.get()
                    .uri(USER_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JSON);
            if (user == null || !(user.get("id") instanceof Number id)) {
                throw loginFailed();
            }
            return id.longValue();
        } catch (RestClientResponseException e) {
            // 인가 코드 재사용(KOE320), Redirect URI 불일치(KOE006), 시크릿 불일치(KOE010) 등
            log.warn("카카오 로그인 실패: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw loginFailed();
        } catch (RestClientException e) {
            log.warn("카카오 서버에 연결하지 못했습니다", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_UNAVAILABLE",
                    "카카오 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
        }
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null) {
            form.add("client_secret", clientSecret);
        }
        Map<String, Object> token = rest.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JSON);
        if (token == null || !(token.get("access_token") instanceof String accessToken)) {
            throw loginFailed();
        }
        return accessToken;
    }

    private static ApiException loginFailed() {
        return ApiException.badRequest("KAKAO_LOGIN_FAILED", "카카오 로그인에 실패했어요. 처음부터 다시 시도해 주세요");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
