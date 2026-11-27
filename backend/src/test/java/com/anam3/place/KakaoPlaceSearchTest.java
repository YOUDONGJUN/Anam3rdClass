package com.anam3.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 카카오 장소 결과 다듬기: 지역(시·도 + 시·군·구) · 마지막 분류 */
class KakaoPlaceSearchTest {

    @Test
    void 주소에서_지역을_뽑는다() {
        assertThat(KakaoPlaceSearch.regionOf("서울 성북구 안암동5가 1-2")).isEqualTo("서울 성북구");
        assertThat(KakaoPlaceSearch.regionOf("경기 성남시 분당구 정자동 1")).isEqualTo("경기 성남시");
        assertThat(KakaoPlaceSearch.regionOf("  세종특별자치시   조치원읍 1 ")).isEqualTo("세종특별자치시 조치원읍");
        assertThat(KakaoPlaceSearch.regionOf("제주")).isEqualTo("제주");
        assertThat(KakaoPlaceSearch.regionOf(null)).isNull();
    }

    @Test
    void 분류는_마지막_것만() {
        assertThat(KakaoPlaceSearch.lastCategory("음식점 > 술집 > 호프,요리주점")).isEqualTo("호프,요리주점");
        assertThat(KakaoPlaceSearch.lastCategory("카페")).isEqualTo("카페");
        assertThat(KakaoPlaceSearch.lastCategory(" ")).isNull();
        assertThat(KakaoPlaceSearch.lastCategory(null)).isNull();
    }
}
