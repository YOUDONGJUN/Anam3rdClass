package com.anam3.meetup;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.anam3.support.ApiTestSupport;

/**
 * 시간 겹침: 내가 열었거나 참여한 3교시끼리는 모임 시간이 2시간 이상 떨어져야 한다 (취소·무산 제외).
 * T0 = 3월 2일(월) 18:00 (한국 시간)
 */
@TestPropertySource(properties = "app.meetup.overlap-check=true")
class MeetupOverlapApiTest extends ApiTestSupport {

    private String host;   // 김고대
    private String anam;   // 이안암
    private String horang; // 박호랑

    @BeforeEach
    void users() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
    }

    private static Instant at(int hours, int minutes) {
        return T0.plus(Duration.ofHours(hours)).plus(Duration.ofMinutes(minutes));
    }

    private static String meetup(String title, Instant meetAt) {
        return meetupJson(title, 2, 6, meetAt, meetAt.minus(Duration.ofMinutes(30)), null); // 모임 30분 전 마감
    }

    private long open(String token, String title, Instant meetAt) throws Exception {
        Number id = read(post("/api/meetups", token, meetup(title, meetAt)).andExpect(status().isCreated()), "$.id");
        return id.longValue();
    }

    @Test
    void 앞뒤_2시간_안에는_3교시를_또_열_수_없고_2시간_떨어지면_된다() throws Exception {
        open(host, "1차 치맥", at(3, 0)); // 21:00

        post("/api/meetups", host, meetup("겹치는 번개", at(4, 30))) // 22:30
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIME_OVERLAP"))
                .andExpect(jsonPath("$.message").value("앞뒤 2시간 안에 이미 연 3교시가 있어요: '1차 치맥' 3/2(월) 21:00"));
        post("/api/meetups", host, meetup("겹치는 번개", at(1, 30))).andExpect(status().isConflict()); // 19:30

        // 딱 2시간 떨어지면 된다 (1차 · 2차)
        open(host, "2차 노래방", at(5, 0)); // 23:00
        open(host, "저녁", at(1, 0));        // 19:00
    }

    @Test
    void 시간이_겹치는_3교시에는_참여할_수_없고_취소된_3교시는_세지_않는다() throws Exception {
        long horangs = open(horang, "치맥 번개", at(3, 0)); // 21:00
        long hosts = open(host, "와인 한잔", at(4, 0));     // 22:00
        post("/api/meetups/" + horangs + "/join", anam, null).andExpect(status().isOk());

        post("/api/meetups/" + hosts + "/join", anam, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("앞뒤 2시간 안에 이미 참여한 3교시가 있어요: '치맥 번개' 3/2(월) 21:00"));

        // 겹치던 3교시가 취소되면 참여할 수 있다
        post("/api/meetups/" + horangs + "/cancel", horang, null).andExpect(status().isOk());
        post("/api/meetups/" + hosts + "/join", anam, null).andExpect(status().isOk());
    }

    @Test
    void 나가면_다른_3교시를_고를_수_있다() throws Exception {
        long horangs = open(horang, "치맥 번개", at(3, 0)); // 21:00
        long hosts = open(host, "와인 한잔", at(4, 0));     // 22:00
        post("/api/meetups/" + horangs + "/join", anam, null).andExpect(status().isOk());

        delete("/api/meetups/" + horangs + "/join", anam).andExpect(status().isOk());
        post("/api/meetups/" + hosts + "/join", anam, null).andExpect(status().isOk());
    }

    @Test
    void 무산된_3교시는_세지_않는다() throws Exception {
        long horangs = open(horang, "치맥 번개", at(3, 0)); // 21:00
        long hosts = open(host, "와인 한잔", at(4, 0));     // 22:00

        // 박호랑의 21시 3교시는 마감(20:30)까지 혼자라 무산 → 박호랑은 22시 3교시(마감 21:30)에 탈 수 있다
        clock.advance(Duration.ofMinutes(160)); // 20:40
        get("/api/meetups/" + horangs, horang).andExpect(jsonPath("$.status").value("FAILED"));
        post("/api/meetups/" + hosts + "/join", horang, null).andExpect(status().isOk());
    }

    @Test
    void 시간을_바꿔서_내_다른_3교시와_겹치게_할_수_없다() throws Exception {
        open(host, "1차 치맥", at(3, 0)); // 21:00
        long later = open(host, "늦은 번개", at(6, 0)); // 3/3 00:00

        String moved = """
                {"title":"늦은 번개","description":"","place":"참살이길","categories":["BEER"],"purposes":[],"openChatUrl":"",
                 "schedule":{"meetAt":"%s","deadlineAt":"%s"}}
                """.formatted(at(4, 0), T0.plus(Duration.ofMinutes(30)));
        put("/api/meetups/" + later, host, moved)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIME_OVERLAP"));

        // 23:00 으로는 옮길 수 있다 (원래 자리 00:00 과는 1시간 차이지만 자기 자신은 세지 않는다)
        put("/api/meetups/" + later, host, moved.replace(at(4, 0).toString(), at(5, 0).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetAt").value(at(5, 0).toString()));
    }
}
