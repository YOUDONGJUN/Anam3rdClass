package com.anam3.place;

import java.util.List;

import com.anam3.common.GeoPoint;
import com.anam3.place.PlaceDtos.PlaceHit;

/**
 * 장소 검색 (3교시 열기 화면). 진짜는 카카오 로컬(KakaoPlaceSearch), 테스트에서는 가짜로 바꿔 끼운다.
 */
public interface PlaceSearch {

    /** 쓸 수 있는지 (카카오 키가 없으면 false — 화면이 직접 쓰기 · 지도 앱 검색으로 안내한다) */
    boolean available();

    /**
     * 장소 이름으로 찾는다. near 가 있으면 그 근처(학교 캠퍼스)부터 찾고, 근처에 없으면 전국에서.
     * 검색이 안 되면(카카오 오류) 빈 목록이 아니라 PlaceSearchUnavailable
     */
    List<PlaceHit> search(String query, GeoPoint near);

    /** 카카오 장소 검색이 지금 안 될 때 (키·권한 문제, 카카오 장애) */
    class PlaceSearchUnavailable extends RuntimeException {

        public PlaceSearchUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
