package com.anam3.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/** 톡방 메시지 신고·차단: 모임 전에도, 보낸 원우가 나갔어도 그 사람이 대상이고 메시지 내용이 기록에 남는다 */
class ChatReportApiTest extends ApiTestSupport {

    private String host;
    private String anam;
    private String outsider;
    private long meetupId;

    /** 김고대가 연 3교시(최소 3명)에 이안암이 참여 — 아직 성사 전 */
    @BeforeEach
    void room() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 61);
        outsider = signup("outside", "최밖에", 61);
        meetupId = createMeetup(host, 3, null, Duration.ofHours(5), Duration.ofHours(3), null);
        post("/api/meetups/" + meetupId + "/join", anam, null).andExpect(status().isOk());
    }

    private long say(String token, String text) throws Exception {
        return ((Number) read(post("/api/meetups/" + meetupId + "/chat", token, "{\"body\":\"" + text + "\"}")
                .andExpect(status().isOk()), "$.id")).longValue();
    }

    private static String chatReport(long messageId) {
        return """
                {"targetType":"MEMBER","targetId":%d,"reason":"ABUSE","detail":"","chat":true}
                """.formatted(messageId);
    }

    @Test
    void 모임_전_톡방_메시지를_신고하면_보낸_원우와_메시지가_기록에_남고_교수에게_알린다() throws Exception {
        String prof = managerToken("prof", "정교수", 50);
        registerDevice(prof, "prof-phone");
        long message = say(anam, "여기 말고 내 가게로 와요");

        post("/api/reports", host, chatReport(message)).andExpect(status().isNoContent());
        assertThat(pushes.to("prof-phone")).anyMatch(p -> p.type() == PushType.REPORT);

        // 보낸 원우가 지워도 신고 기록에는 남는다
        delete("/api/meetups/" + meetupId + "/chat/" + message, anam).andExpect(status().isNoContent());
        get("/api/admin/reports", prof).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetType").value("MEMBER"))
                .andExpect(jsonPath("$[0].memberName").value("이안암"))
                .andExpect(jsonPath("$[0].meetupId").value(meetupId))
                .andExpect(jsonPath("$[0].targetSnapshot", containsString("톡방 메시지")))
                .andExpect(jsonPath("$[0].targetSnapshot", containsString("여기 말고 내 가게로 와요")));

        // 같은 사람을 하루에 또 신고하면 막는다 (다른 메시지여도)
        long another = say(anam, "또 보내요");
        post("/api/reports", host, chatReport(another)).andExpect(status().isConflict());
    }

    @Test
    void 보낸_원우가_나갔어도_그_사람을_신고하고_차단한다() throws Exception {
        long message = say(anam, "저 빠질게요 ㅋ");
        delete("/api/meetups/" + meetupId + "/join", anam).andExpect(status().isOk());

        post("/api/reports", host, chatReport(message)).andExpect(status().isNoContent());
        get("/api/admin/reports", ownerToken())
                .andExpect(jsonPath("$[0].memberName").value("이안암")) // 연 사람(김고대)이 아니라 보낸 원우
                .andExpect(jsonPath("$[0].targetSnapshot", containsString("저 빠질게요")));

        post("/api/blocks", host, "{\"ref\":" + message + ",\"chat\":true}").andExpect(status().isCreated());
        get("/api/blocks", host).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("이*암"));
    }

    @Test
    void 톡방에_없는_사람은_못_하고_내_메시지는_신고할_수_없다() throws Exception {
        long message = say(anam, "안녕하세요");
        post("/api/reports", outsider, chatReport(message)).andExpect(status().isNotFound());
        post("/api/blocks", outsider, "{\"ref\":" + message + ",\"chat\":true}").andExpect(status().isNotFound());
        post("/api/reports", anam, chatReport(message)).andExpect(status().isBadRequest());
    }
}
