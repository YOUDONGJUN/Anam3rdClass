package com.anam3.meetup;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** 참여자끼리 차단, 마감이 지난 성사 3교시의 시간 옮기기, 종류 개수 */
class MeetupRulesApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);

    private String host;   // 김고대
    private String anam;   // 이안암
    private String horang; // 박호랑

    @BeforeEach
    void users() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
    }

    private static String edit(Instant meetAt, Instant deadlineAt) {
        return """
                {"title":"치맥 번개","description":"","place":"안암역 앞","categories":["BEER"],"purposes":[],
                 "openChatUrl":"","schedule":{"meetAt":"%s","deadlineAt":"%s"}}
                """.formatted(meetAt, deadlineAt);
    }

    @Test
    void 차단한_사이면_상대가_참여한_3교시에도_참여할_수_없다() throws Exception {
        long first = createMeetup(host, 4, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + first + "/join", anam, null).andExpect(status().isOk());
        post("/api/meetups/" + first + "/join", horang, null).andExpect(status().isOk());
        post("/api/blocks", horang, "{\"ref\":" + participantRef(horang, first, 1) + "}") // 박호랑이 이안암을 차단
                .andExpect(status().isCreated());

        // 이안암이 참여한 3교시: 박호랑에게 참여 버튼이 없고, 참여도 막는다
        long withAnam = createMeetup(host, 4, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + withAnam + "/join", anam, null).andExpect(status().isOk());
        get("/api/meetups/" + withAnam, horang).andExpect(jsonPath("$.canJoin").value(false));
        post("/api/meetups/" + withAnam + "/join", horang, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED"));

        // 차단당한 쪽도 마찬가지
        long withHorang = createMeetup(host, 4, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + withHorang + "/join", horang, null).andExpect(status().isOk());
        post("/api/meetups/" + withHorang + "/join", anam, null).andExpect(jsonPath("$.code").value("BLOCKED"));

        // 이미 함께 참여 중인 3교시는 그대로 (참여 버튼 문제만)
        get("/api/meetups/" + first, horang).andExpect(jsonPath("$.joined").value(true));
    }

    @Test
    void 마감이_지난_성사_3교시는_마감은_그대로_두고_모임_시간만_옮긴다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(jsonPath("$.status").value("CONFIRMED"));
        clock.advance(Duration.ofMinutes(90)); // 마감(1시간 뒤)이 지났다 · 모임 30분 전

        // 화면은 모임 시간에 맞춰 마감도 함께 보내지만, 지난 마감은 다시 열지 않는다
        Instant later = T0.plus(Duration.ofHours(3));
        put("/api/meetups/" + id, host, edit(later, later.minus(Duration.ofMinutes(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetAt").value(later.toString()))
                .andExpect(jsonPath("$.deadlineAt").value(T0.plus(IN_1H).toString()));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.canLeave").value(false));
        get("/api/meetups/" + id, horang).andExpect(jsonPath("$.canJoin").value(false));

        // 마감보다 앞으로는 못 옮긴다
        Instant beforeDeadline = T0.plus(Duration.ofMinutes(50));
        put("/api/meetups/" + id, host, edit(beforeDeadline, beforeDeadline))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME"));
    }

    @Test
    void 마실_종류는_3개까지_고른다() throws Exception {
        String four = meetupJson("치맥 번개", 2, 6, T0.plus(IN_2H), T0.plus(IN_1H), null)
                .replace("\"categories\":[\"BEER\"]", "\"categories\":[\"BEER\",\"SOJU\",\"WINE\",\"HIGHBALL\"]");
        post("/api/meetups", host, four)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("무엇을 마실지는 3개까지 고를 수 있어요"));
    }
}
