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
}
