package com.anam3.place;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.anam3.common.AppProperties;
import com.anam3.common.GeoPoint;
import com.anam3.place.PlaceDtos.PlaceHit;

/**
 * 카카오 로컬 키워드 검색 (dapi.kakao.com/v2/local/search/keyword.json). 카카오 로그인과 같은 REST API 키를 쓴다.
 * 카카오 디벨로퍼스 > 앱 > 제품 설정 > 카카오맵 을 "사용"으로 켜야 한다 (안 켜면 403 → 검색을 쓸 수 없다고 안내).
 * 원우의 위치는 보내지 않는다 — 근처 기준은 학교 캠퍼스다.
 */
@Component
public class KakaoPlaceSearch implements PlaceSearch {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceSearch.class);
    private static final String URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    /** 캠퍼스에서 이 거리(m) 안을 먼저 찾는다 (카카오 최대 20km) */
    private static final int NEAR_RADIUS = 20_000;
    private static final int SIZE = 10;
    /** 3교시 장소 칸 길이 */
    private static final int NAME_MAX = 60;
    private static final int REGION_MAX = 30;
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;
    private final String restApiKey;

    public KakaoPlaceSearch(AppProperties props) {
        String key = props.kakao() == null ? null : props.kakao().restApiKey();
        this.restApiKey = key == null || key.isBlank() ? null : key.strip();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean available() {
        return restApiKey != null;
    }

    @Override
    public List<PlaceHit> search(String query, GeoPoint near) {
        if (near != null) {
            List<PlaceHit> nearby = request(query, near);
            if (!nearby.isEmpty()) {
                return nearby;
            }
        }
        return request(query, null); // 근처에 없으면 전국에서 (다른 동네에서 모이는 3교시)
    }

    private List<PlaceHit> request(String query, GeoPoint near) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(URL)
                .queryParam("query", query)
                .queryParam("size", SIZE);
        if (near != null) {
            uri.queryParam("x", near.lng()).queryParam("y", near.lat()).queryParam("radius", NEAR_RADIUS);
        }
        URI target = uri.encode().build().toUri();
        Map<String, Object> body;
        try {
            body = rest.get()
                    .uri(target)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(JSON);
        } catch (RestClientException e) {
            log.warn("카카오 장소 검색 실패: {}", e.getMessage());
            throw new PlaceSearchUnavailable("카카오 장소 검색 실패", e);
        }
        List<PlaceHit> hits = new ArrayList<>();
        if (body != null && body.get("documents") instanceof List<?> docs) {
            for (Object doc : docs) {
                if (doc instanceof Map<?, ?> d) {
                    PlaceHit hit = toHit(d);
                    if (hit != null) {
                        hits.add(hit);
                    }
                }
            }
        }
        return hits;
    }

    private static PlaceHit toHit(Map<?, ?> d) {
        String name = text(d.get("place_name"));
        Double lng = number(d.get("x"));
        Double lat = number(d.get("y"));
        if (name == null || lat == null || lng == null) {
            return null;
        }
        String road = text(d.get("road_address_name"));
        String lot = text(d.get("address_name"));
        return new PlaceHit(cut(name, NAME_MAX), road != null ? road : lot, lastCategory(text(d.get("category_name"))),
                new GeoPoint(lat, lng), regionOf(lot != null ? lot : road));
    }

    /** "서울 성북구 안암동5가 1-2" → "서울 성북구" (시·도 + 시·군·구) */
    static String regionOf(String address) {
        if (address == null) {
            return null;
        }
        String[] parts = address.strip().split("\\s+");
        if (parts.length < 2) {
            return parts[0].isEmpty() ? null : cut(parts[0], REGION_MAX);
        }
        return cut(parts[0] + " " + parts[1], REGION_MAX);
    }

    /** "음식점 > 술집 > 호프,요리주점" → "호프,요리주점" */
    static String lastCategory(String category) {
        if (category == null) {
            return null;
        }
        int at = category.lastIndexOf('>');
        String last = (at < 0 ? category : category.substring(at + 1)).strip();
        return last.isEmpty() ? null : last;
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private static Double number(Object value) {
        try {
            return value == null ? null : Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
