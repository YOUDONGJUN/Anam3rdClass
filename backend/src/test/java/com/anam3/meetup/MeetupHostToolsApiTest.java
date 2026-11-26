package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.push.PushMessage;
import com.anam3.support.ApiTestSupport;

/** 연 사람 도구: 내보내기 · 연 사람 넘기기, 그리고 빈자리 대기 목록 · 목록 개수 */
class MeetupHostToolsApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_3H = Duration.ofHours(3);

    private String host;   // 김고대
    private String anam;   // 이안암
    private String horang; // 박호랑

    @BeforeEach
    void users() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        horang = signup("horang", "박호랑", 61);
    }

    @Test
    void 연_사람은_참여자를_내보내고_내보낸_원우는_다시_참여할_수_없다() throws Exception {
        registerDevice(anam, "anam-phone");
        long id = createMeetup(host, 2, 6, IN_3H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        long ref = participantRef(host, id, 1);

        delete("/api/meetups/" + id + "/participants/" + ref, horang).andExpect(status().isForbidden());
        delete("/api/meetups/" + id + "/participants/" + participantRef(host, id, 0), host)
                .andExpect(status().isBadRequest()); // 연 사람 자신은 못 내보낸다
        delete("/api/meetups/" + id + "/participants/" + ref, host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1));
        assertThat(pushes.to("anam-phone")).extracting(PushMessage::body).last().asString().startsWith("연 사람이 참여를 취소했어요");

        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.canJoin").value(false));
        post("/api/meetups/" + id + "/join", anam, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KICKED"));
        // 다른 3교시에는 그대로 참여한다
        long other = createMeetup(host, 2, 6, Duration.ofHours(6), IN_1H, null);
        post("/api/meetups/" + other + "/join", anam, null).andExpect(status().isOk());
    }

    @Test
    void 연_사람을_참여자에게_넘기면_넘겨받은_원우가_고치고_취소한다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_3H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        long anamRef = participantRef(host, id, 1);

        post("/api/meetups/" + id + "/host", anam, "{\"ref\":" + anamRef + "}").andExpect(status().isForbidden());
        post("/api/meetups/" + id + "/host", host, "{\"ref\":" + anamRef + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosting").value(false))
                .andExpect(jsonPath("$.joined").value(true)); // 원래 연 사람은 참여자로 남는다
        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.hosting").value(true))
                .andExpect(jsonPath("$.canEdit").value(true));
        post("/api/meetups/" + id + "/cancel", host, null).andExpect(status().isForbidden());
        post("/api/meetups/" + id + "/cancel", anam, null).andExpect(status().isOk());
    }

    @Test
    void 빈자리를_기다리는_3교시를_모아_본다() throws Exception {
        long full = createMeetup(host, 2, 2, IN_3H, IN_1H, null);
        post("/api/meetups/" + full + "/join", anam, null).andExpect(status().isOk());
        post("/api/meetups/" + full + "/wait", horang, null).andExpect(status().isOk());

        get("/api/me/waits", horang)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(full));
        get("/api/me/waits", anam).andExpect(jsonPath("$", hasSize(0)));

        clock.advance(Duration.ofHours(4)); // 모임이 지나면 빠진다
        get("/api/me/waits", horang).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 내_3교시는_limit_만큼_최근_것부터() throws Exception {
        for (int i = 0; i < 3; i++) {
            createMeetup(host, 2, 6, Duration.ofHours(3 + i * 3), IN_1H, null);
        }
        get("/api/me/meetups?limit=2", host).andExpect(jsonPath("$", hasSize(2)));
        get("/api/me/meetups", host).andExpect(jsonPath("$", hasSize(3)));
        get("/api/meetups?scope=past&limit=0", host).andExpect(status().isOk()); // 0이면 1개로 본다
    }

    @Test
    void 성사된_뒤_나간_원우는_그때_함께_있던_원우가_신고하고_차단한다() throws Exception {
        long id = createMeetup(host, 2, 6, IN_3H, IN_1H, null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(jsonPath("$.status").value("CONFIRMED"));
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(5));
        delete("/api/meetups/" + id + "/join", horang).andExpect(status().isOk()); // 실명을 보고 나갔다
        clock.advance(Duration.ofMinutes(5));
        String choi = signup("choi", "최석탑", 61);
        post("/api/meetups/" + id + "/join", choi, null).andExpect(status().isOk()); // 나간 뒤에 들어왔다

        get("/api/meetups/" + id, choi).andExpect(jsonPath("$.leftPeople", hasSize(0)));
        Number leftRef = read(get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.leftPeople", hasSize(1)))
                .andExpect(jsonPath("$.leftPeople[0].name").value("박호랑")), "$.leftPeople[0].ref");

        post("/api/reports", anam, """
                {"targetType":"MEMBER","targetId":%d,"reason":"ABUSE","detail":"","left":true}
                """.formatted(leftRef.longValue())).andExpect(status().isNoContent());
        post("/api/blocks", anam, "{\"ref\":" + leftRef + ",\"left\":true}").andExpect(status().isCreated());
        get("/api/blocks", anam).andExpect(jsonPath("$", hasSize(1))); // 차단 목록의 이름은 가려 보인다
        // 함께 있지 않았던 사람은 그 기록으로 신고할 수 없다
        String outsider = signup("sinchon", "이신촌", 61);
        post("/api/blocks", outsider, "{\"ref\":" + leftRef + ",\"left\":true}").andExpect(status().isNotFound());
    }
}
