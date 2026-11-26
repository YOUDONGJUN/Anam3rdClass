package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.anam3.push.PushMessage;
import com.anam3.support.ApiTestSupport;

/** 취소·무산·정리: 대기자 알림, 무산 문구, 혼자일 때 최소 인원, 해임된 교수의 공지 */
class MeetupCleanupApiTest extends ApiTestSupport {

    private static final String EDIT = """
            {"title":"치맥 번개","description":"","place":"안암역 앞","categories":["BEER"],"purposes":[],
             "openChatUrl":"","people":{"minPeople":%s,"maxPeople":6}}
            """;

    @Test
    void 취소하면_빈자리_대기자에게도_알리고_대기를_지운다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        registerDevice(horang, "horang-phone");
        long id = createMeetup(host, 2, 2, Duration.ofHours(2), Duration.ofHours(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk()); // 만석
        post("/api/meetups/" + id + "/wait", horang, null).andExpect(status().isOk());

        post("/api/meetups/" + id + "/cancel", host, null).andExpect(status().isOk());
        assertThat(pushes.to("horang-phone")).extracting(PushMessage::body).last().asString().startsWith("취소됐어요");
        assertThat(jdbc.queryForObject("select count(*) from meetup_waits", Integer.class)).isZero();
    }

    @Test
    void 무산된_3교시를_고치려_하면_무산이라고_알린다() throws Exception {
        String host = signup("godae", "김고대", 61);
        long id = createMeetup(host, 3, 6, Duration.ofHours(3), Duration.ofHours(1), null);
        clock.advance(Duration.ofMinutes(90)); // 혼자라 무산
        put("/api/meetups/" + id, host, EDIT.formatted(3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAILED"));
    }

    @Test
    void 혼자_연_3교시에는_나중에_최소_인원을_걸_수_있다() throws Exception {
        String host = signup("godae", "김고대", 61);
        long id = createMeetup(host, null, 6, Duration.ofHours(3), Duration.ofHours(1), null); // 열자마자 확정
        put("/api/meetups/" + id, host, EDIT.formatted(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPeople").value(3))
                .andExpect(jsonPath("$.status").value("RECRUITING"));
    }

    @Test
    void 해임된_교수는_예전에_쓴_공지를_고치거나_지울_수_없다() throws Exception {
        String manager = managerToken("horang", "박호랑", 61);
        Number id = read(post("/api/notices", manager, "{\"title\":\"공지\",\"content\":\"내용\"}")
                .andExpect(status().isCreated()), "$.id");
        put("/api/admin/members/" + memberId("horang") + "/role", ownerToken(), "{\"role\":\"USER\"}")
                .andExpect(status().isOk());
        String demoted = login("horang");
        delete("/api/notices/" + id, demoted).andExpect(status().isForbidden());
        get("/api/notices/" + id, demoted).andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void 아직_모이기_전인_3교시는_한_사람이_10개까지_연다() throws Exception {
        String host = signup("godae", "김고대", 61);
        for (int i = 0; i < 10; i++) {
            createMeetup(host, 2, 6, Duration.ofHours(3 + i), Duration.ofHours(1), null);
        }
        post("/api/meetups", host, meetupJson("하나 더", 2, 6, T0.plus(Duration.ofHours(20)), T0.plus(Duration.ofHours(1)), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_OPEN"));
    }
}
