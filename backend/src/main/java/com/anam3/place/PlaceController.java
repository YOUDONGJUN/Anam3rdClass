package com.anam3.place;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.common.ApiException;
import com.anam3.common.GeoPoint;
import com.anam3.common.RateLimiter;
import com.anam3.common.RateLimits;
import com.anam3.place.PlaceDtos.PlaceSearchResult;
import com.anam3.place.PlaceSearch.PlaceSearchUnavailable;
import com.anam3.school.SchoolRepository;

/**
 * 장소 검색: 3교시 열기 화면에서 장소를 찾아 고르면 위치(좌표)와 지역이 같이 저장된다 → 목록의 가까운 순 · 지역 거르기.
 * 학교 캠퍼스 근처부터 찾는다.
 */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    static final int QUERY_MAX = 40;

    private final PlaceSearch search;
    private final SchoolRepository schools;
    private final Clock clock;
    /** 한 사람이 1분에 30번까지 (카카오 하루 호출 한도를 한 사람이 다 쓰지 않게) */
    private final RateLimiter limit;

    public PlaceController(PlaceSearch search, SchoolRepository schools, Clock clock, RateLimits limits) {
        this.search = search;
        this.schools = schools;
        this.clock = clock;
        this.limit = limits.create(30, Duration.ofMinutes(1));
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public PlaceSearchResult search(@AuthenticationPrincipal LoginMember me, @RequestParam(defaultValue = "") String q) {
        String query = q.strip();
        if (query.isEmpty()) {
            throw ApiException.badRequest("EMPTY_QUERY", "찾을 장소 이름을 적어 주세요");
        }
        if (query.length() > QUERY_MAX) {
            query = query.substring(0, QUERY_MAX);
        }
        if (!search.available()) {
            return PlaceSearchResult.unavailable();
        }
        Instant now = clock.instant();
        if (!limit.tryAcquire(me.id().toString(), now)) {
            throw ApiException.badRequest("TOO_FAST", "너무 빨리 찾고 있어요. 잠시 후 다시 찾아 주세요");
        }
        GeoPoint campus = schools.findById(me.schoolId()).map(s -> s.getCampus()).orElse(null);
        try {
            return new PlaceSearchResult(true, search.search(query, campus));
        } catch (PlaceSearchUnavailable e) {
            return PlaceSearchResult.unavailable();
        }
    }
}
