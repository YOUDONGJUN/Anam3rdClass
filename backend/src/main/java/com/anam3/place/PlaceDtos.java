package com.anam3.place;

import java.util.List;

import com.anam3.common.GeoPoint;

public final class PlaceDtos {

    private PlaceDtos() {
    }

    /**
     * 검색된 장소 하나.
     * name: 장소 이름 (참살이 치킨) · address: 도로명 주소(없으면 지번) · category: 마지막 분류 (호프,요리주점)
     * region: 시·구 (서울 성북구) — 목록의 지역 거르기에 쓴다
     */
    public record PlaceHit(String name, String address, String category, GeoPoint location, String region) {
    }

    /** available=false 면 장소 검색을 쓸 수 없다 (화면이 직접 쓰기 · 지도 앱 검색으로 안내) */
    public record PlaceSearchResult(boolean available, List<PlaceHit> places) {

        public static PlaceSearchResult unavailable() {
            return new PlaceSearchResult(false, List.of());
        }
    }
}
