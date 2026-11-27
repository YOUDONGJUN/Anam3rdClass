package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.anam3.common.GeoPoint;
import com.anam3.support.ApiTestSupport;

/** 장소 위치: 장소 검색 → 위치·지역과 함께 열기 → 목록·상세에 실린다 (가까운 순 · 지역 거르기) */
class MeetupLocationApiTest extends ApiTestSupport {

    private static final String CHAMSARI = "{\"lat\":37.5868,\"lng\":127.0297}";

    private static String extra(String location, String region) {
        return (location == null ? "" : ",\"location\":" + location)
                + (region == null ? "" : ",\"region\":\"" + region + "\"");
    }

    /** 장소·위치·지역을 바꾼 3교시 열기 본문 */
    private static String openBody(String place, String location, String region) {
        String base = meetupJson("치맥 번개", null, null, T0.plus(Duration.ofDays(1)), T0.plus(Duration.ofHours(20)),
                null);
        return base.replace("\"place\":\"안암역 앞\"", "\"place\":\"" + place + "\"" + extra(location, region));
    }

    /** 수정 본문 (위치를 보내지 않으면 location 칸이 없다) */
    private static String editBody(String place, String location, String region) {
        return "{\"title\":\"치맥 번개\",\"description\":\"\",\"place\":\"" + place
                + "\",\"categories\":[\"BEER\"],\"purposes\":[],\"openChatUrl\":\"\"" + extra(location, region) + "}";
    }

    private long open(String token, String body) throws Exception {
        return ((Number) read(request(MockMvcRequestBuilders.post("/api/meetups"), token, body)
                .andExpect(status().isCreated()), "$.id")).longValue();
    }

    @Test
    void 장소_검색은_우리_학교_캠퍼스_근처부터() throws Exception {
        String godae = signup("godae", "김고대", 61);
        get("/api/places/search?q=치킨", godae).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.places[*].name", contains("참살이 치킨", "신촌 치킨")))
                .andExpect(jsonPath("$.places[0].region").value("서울 성북구"))
                .andExpect(jsonPath("$.places[0].location.lat").value(37.5868));
        assertThat(placeSearch.nearSeen).containsExactly(new GeoPoint(37.5894, 127.0322));

        get("/api/places/search?q=", godae).andExpect(status().isBadRequest());
    }

    @Test
    void 카카오_키가_없거나_안_되면_쓸_수_없다고_알린다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        placeSearch.failing = true;
        get("/api/places/search?q=치킨", godae).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.places", hasSize(0)));
        placeSearch.available = false;
        get("/api/places/search?q=치킨", godae).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 위치와_함께_열면_목록과_상세에_실리고_수정에서_장소를_바꾸면_지운다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        long id = open(godae, openBody("참살이 치킨", CHAMSARI, "서울 성북구"));

        get("/api/meetups?scope=upcoming&board=school", godae)
                .andExpect(jsonPath("$[0].location.lat").value(37.5868))
                .andExpect(jsonPath("$[0].location.lng").value(127.0297))
                .andExpect(jsonPath("$[0].region").value("서울 성북구"));
        get("/api/meetups/" + id, godae).andExpect(jsonPath("$.region").value("서울 성북구"));

        // 위치를 보내지 않고 장소 이름이 그대로면 위치도 그대로
        put("/api/meetups/" + id, godae, editBody("참살이 치킨", null, null)).andExpect(status().isOk());
        get("/api/meetups/" + id, godae).andExpect(jsonPath("$.location.lat").value(37.5868));

        // 장소 이름을 바꾸고 위치가 없으면(직접 쓴 장소) 지운다
        put("/api/meetups/" + id, godae, editBody("개운사길 어딘가", null, null)).andExpect(status().isOk());
        get("/api/meetups/" + id, godae).andExpect(jsonPath("$.location").value(nullValue()))
                .andExpect(jsonPath("$.region").value(nullValue()));

        // 새로 고르면 그 위치
        put("/api/meetups/" + id, godae, editBody("신촌 치킨", "{\"lat\":37.5597,\"lng\":126.9423}", "서울 서대문구"))
                .andExpect(status().isOk());
        get("/api/meetups/" + id, godae).andExpect(jsonPath("$.region").value("서울 서대문구"))
                .andExpect(jsonPath("$.location.lng").value(126.9423));
    }

    @Test
    void 직접_쓴_장소는_위치가_없고_우리나라_밖이나_반쪽_위치는_받지_않는다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        long id = open(godae, openBody("안암역 앞", null, null));
        get("/api/meetups/" + id, godae).andExpect(jsonPath("$.location").value(nullValue()));

        request(MockMvcRequestBuilders.post("/api/meetups"), godae,
                openBody("도쿄", "{\"lat\":35.68,\"lng\":139.76}", "도쿄")).andExpect(status().isBadRequest());
        request(MockMvcRequestBuilders.post("/api/meetups"), godae,
                openBody("반쪽 위치", "{\"lat\":37.5}", null)).andExpect(status().isBadRequest());
    }

    @Test
    void 장소_추천에_고른_적_있는_위치가_실린다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        open(godae, openBody("안암역 앞", null, null));
        clock.advance(Duration.ofMinutes(1));
        open(godae, openBody("참살이 치킨", CHAMSARI, "서울 성북구"));

        get("/api/meetups/places", godae)
                .andExpect(jsonPath("$[*].name", contains("참살이 치킨", "안암역 앞")))
                .andExpect(jsonPath("$[0].location.lat").value(37.5868))
                .andExpect(jsonPath("$[0].region").value("서울 성북구"))
                .andExpect(jsonPath("$[1].location").value(nullValue()));
    }

    @Test
    void 내_정보에_캠퍼스가_실리고_대학원장이_바꾼다() throws Exception {
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        get("/api/me", sinchon).andExpect(jsonPath("$.campus.lat").value(37.5658));

        String owner = ownerToken();
        String url = "/api/admin/schools/" + schoolId("YONSEI");
        String school = "{\"name\":\"연세대학교\",\"shortName\":\"연세대\",\"graduateSchool\":\"인공지능융합대학원\","
                + "\"latestCohort\":99";
        put(url, owner, school + ",\"campus\":{\"lat\":37.56,\"lng\":126.94}}").andExpect(status().isOk());
        get("/api/me", sinchon).andExpect(jsonPath("$.campus.lng").value(126.94));
        get("/api/admin/schools", owner).andExpect(jsonPath("$[?(@.code == 'YONSEI')].campus.lat", contains(37.56)));

        // 캠퍼스를 보내지 않으면(예전 화면) 그대로
        put(url, owner, school + "}").andExpect(status().isOk());
        get("/api/me", sinchon).andExpect(jsonPath("$.campus.lat").value(37.56));

        put(url, owner, school + ",\"campus\":{\"lat\":10,\"lng\":126.94}}").andExpect(status().isBadRequest());
    }
}
