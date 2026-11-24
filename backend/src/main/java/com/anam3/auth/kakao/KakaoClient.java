package com.anam3.auth.kakao;

/**
 * 카카오 로그인. 테스트에서는 가짜로 바꿔 끼운다 (FakeKakaoClient).
 */
public interface KakaoClient {

    /** 화면이 카카오 로그인 창을 열 때 쓰는 REST API 키 (설정 안 됐으면 null) */
    String clientId();

    /**
     * 카카오가 돌려준 인가 코드로 카카오 회원번호를 알아낸다.
     * redirectUri 는 로그인 창을 열 때 쓴 주소와 같아야 한다. 실패하면 ApiException.
     */
    long fetchUserId(String code, String redirectUri);

    /**
     * 앱(안드로이드)이 카카오톡으로 로그인해서 받은 카카오 액세스 토큰으로 카카오 회원번호를 알아낸다.
     * 다른 카카오 앱의 토큰은 받지 않는다(앱 ID 확인). 실패하면 ApiException.
     */
    long fetchUserIdByAccessToken(String accessToken);
}
