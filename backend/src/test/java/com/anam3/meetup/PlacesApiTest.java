package com.anam3.meetup;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.anam3.support.ApiTestSupport;

/** 3교시 열기 화면의 장소 추천: 우리 학교 3교시에서 많이 쓴 장소 순서 */
class PlacesApiTest extends ApiTestSupport {

    private void open(String token, String place, String audience) throws Exception {
        String body = meetupJson("치맥 번개", null, null, T0.plus(Duration.ofDays(1)), T0.plus(Duration.ofHours(20)), null,
                audience).replace("안암역 앞", place);
        request(MockMvcRequestBuilders.post("/api/meetups"), token, body).andExpect(status().isCreated());
    }

    @Test
    void 많이_쓴_순서로_우리_학교_장소만() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String junior = signup("junior", "최후배", 62);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);

        get("/api/meetups/places", godae).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));

        open(godae, "참살이길 치킨집", "SCHOOL");
        clock.advance(Duration.ofMinutes(1));
        open(anam, "개운사길", "ALL");
        clock.advance(Duration.ofMinutes(1));
        open(anam, "참살이길 치킨집", "SCHOOL");
        clock.advance(Duration.ofMinutes(1));
        open(junior, "62기 비밀 장소", "COHORT"); // 다른 기수 전용은 61기에게 안 보인다
        open(sinchon, "연세로", "SCHOOL"); // 다른 학교

        get("/api/meetups/places", godae)
                .andExpect(jsonPath("$", contains("참살이길 치킨집", "개운사길")));
        get("/api/meetups/places", junior)
                .andExpect(jsonPath("$", contains("참살이길 치킨집", "62기 비밀 장소", "개운사길")));
        get("/api/meetups/places", sinchon).andExpect(jsonPath("$", contains("연세로")));
    }
}
