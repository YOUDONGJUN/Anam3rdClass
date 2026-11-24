package com.anam3.support;

import com.anam3.auth.kakao.KakaoClient;
import com.anam3.common.ApiException;

/**
 * 테스트용 카카오: 인가 코드 "code-{key}" 와 앱 토큰 "app-token-{key}" 를 key 별로 늘 같은 회원번호로 바꿔 준다.
 * "other-app-token-{key}" 는 다른 카카오 앱의 토큰처럼 거절한다
 */
public class FakeKakaoClient implements KakaoClient {

    private static final String PREFIX = "code-";
    private static final String APP_PREFIX = "app-token-";

    public static String code(String key) {
        return PREFIX + key;
    }

    public static String appToken(String key) {
        return APP_PREFIX + key;
    }

    public static long kakaoId(String key) {
        return Math.abs((long) key.hashCode()) + 1;
    }

    @Override
    public String clientId() {
        return "test-kakao-key";
    }

    @Override
    public long fetchUserId(String code, String redirectUri) {
        if (code == null || !code.startsWith(PREFIX)) {
            throw ApiException.badRequest("KAKAO_LOGIN_FAILED", "카카오 로그인에 실패했어요. 처음부터 다시 시도해 주세요");
        }
        return kakaoId(code.substring(PREFIX.length()));
    }

    @Override
    public long fetchUserIdByAccessToken(String accessToken) {
        if (accessToken == null || !accessToken.startsWith(APP_PREFIX)) {
            throw ApiException.badRequest("KAKAO_LOGIN_FAILED", "카카오 로그인에 실패했어요. 처음부터 다시 시도해 주세요");
        }
        return kakaoId(accessToken.substring(APP_PREFIX.length()));
    }
}
