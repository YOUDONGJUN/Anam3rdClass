package com.anam3.support;

import java.util.ArrayList;
import java.util.List;

import com.anam3.common.GeoPoint;
import com.anam3.place.PlaceDtos.PlaceHit;
import com.anam3.place.PlaceSearch;

/**
 * 테스트용 장소 검색: 이름에 찾는 말이 들어간 장소를 돌려준다. near 로 무엇을 받았는지 남긴다.
 * failing=true 면 카카오가 안 되는 것처럼, available=false 면 키가 없는 것처럼
 */
public class FakePlaceSearch implements PlaceSearch {

    public static final PlaceHit CHAMSARI = new PlaceHit("참살이 치킨", "서울 성북구 안암로 1", "치킨",
            new GeoPoint(37.5868, 127.0297), "서울 성북구");
    public static final PlaceHit SINCHON = new PlaceHit("신촌 치킨", "서울 서대문구 연세로 1", "치킨",
            new GeoPoint(37.5597, 126.9423), "서울 서대문구");

    public boolean available = true;
    public boolean failing = false;
    public final List<GeoPoint> nearSeen = new ArrayList<>();

    public void reset() {
        available = true;
        failing = false;
        nearSeen.clear();
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public List<PlaceHit> search(String query, GeoPoint near) {
        nearSeen.add(near);
        if (failing) {
            throw new PlaceSearchUnavailable("가짜 실패", null);
        }
        return List.of(CHAMSARI, SINCHON).stream().filter(h -> h.name().contains(query)).toList();
    }
}
