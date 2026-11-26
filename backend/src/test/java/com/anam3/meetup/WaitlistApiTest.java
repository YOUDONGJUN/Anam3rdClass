package com.anam3.meetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.anam3.push.PushMessage;
import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/**
 * 빈자리 대기: 정원이 차면 "자리 나면 알림"을 신청하고, 누가 나가거나 정원이 늘면 알림을 받는다.
 * 연 사람은 몇 명이 기다리는지만 보고 정원을 늘린다 (거절은 없다).
 */
class WaitlistApiTest extends ApiTestSupport {

    private List<PushType> types(String deviceToken) {
        return pushes.to(deviceToken).stream().map(PushMessage::type).toList();
    }

    /** 2명 정원 3교시를 열고 한 명이 타서 꽉 찬 상태 */
    private long fullMeetup(String host, String joiner) throws Exception {
        long id = createMeetup(host, 2, 2, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", joiner, null).andExpect(status().isOk());
        return id;
    }

    @Test
    void 정원이_차면_대기를_신청하고_연_사람은_대기_수만_본다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        long id = fullMeetup(host, anam);

        get("/api/meetups/" + id, horang)
                .andExpect(jsonPath("$.canJoin").value(false))
                .andExpect(jsonPath("$.canWait").value(true))
                .andExpect(jsonPath("$.waiting").value(false))
                .andExpect(jsonPath("$.waitCount").doesNotExist());
        post("/api/meetups/" + id + "/join", horang, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULL"));

        post("/api/meetups/" + id + "/wait", horang, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(true));
        // 두 번 눌러도 한 번
        post("/api/meetups/" + id + "/wait", horang, null).andExpect(status().isOk());

        get("/api/meetups/" + id, host)
                .andExpect(jsonPath("$.waitCount").value(1))
                .andExpect(jsonPath("$.canAddSeats").value(true))
                .andExpect(jsonPath("$.canWait").value(false));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.waitCount").doesNotExist());
        get("/api/meetups/" + id, ownerToken()).andExpect(jsonPath("$.waitCount").value(1));

        // 취소
        delete("/api/meetups/" + id + "/wait", horang)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(false));
        get("/api/meetups/" + id, host).andExpect(jsonPath("$.waitCount").value(0));
    }

    @Test
    void 대기할_수_없는_경우() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 30);

        long open = createMeetup(host, 2, 3, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + open + "/wait", horang, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_FULL"));

        long id = fullMeetup(host, anam);
        post("/api/meetups/" + id + "/wait", anam, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        post("/api/meetups/" + id + "/wait", sinchon, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ONLY"));
        post("/api/meetups/999999/wait", horang, null).andExpect(status().isNotFound());

        // 마감이 지나면 대기도 못 한다
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        post("/api/meetups/" + id + "/wait", horang, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED"));
        get("/api/meetups/" + id, horang).andExpect(jsonPath("$.canWait").value(false));
    }

    @Test
    void 누가_나가면_기다리던_사람에게_알리고_먼저_탄_사람이_탄다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        String seoktap = signup("seoktap", "최석탑", 61);
        registerDevice(host, "host-phone");
        registerDevice(horang, "horang-phone");
        registerDevice(seoktap, "seoktap-phone");
        long id = fullMeetup(host, anam);
        post("/api/meetups/" + id + "/wait", horang, null).andExpect(status().isOk());
        post("/api/meetups/" + id + "/wait", seoktap, null).andExpect(status().isOk());
        pushes.clear();

        delete("/api/meetups/" + id + "/join", anam).andExpect(status().isOk());
        assertThat(types("horang-phone")).containsExactly(PushType.SPOT);
        assertThat(types("seoktap-phone")).containsExactly(PushType.SPOT);
        PushMessage spot = pushes.to("horang-phone").getFirst();
        assertThat(spot.title()).isEqualTo("치맥 번개");
        assertThat(spot.body()).startsWith("자리가 났어요!");
        assertThat(spot.path()).isEqualTo("/meetups/" + id);
        assertThat(types("host-phone")).containsExactly(PushType.JOIN); // 연 사람은 "한 명이 나갔어요"

        // 먼저 누른 사람이 탄다. 탄 사람의 대기는 지워진다
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());
        post("/api/meetups/" + id + "/join", seoktap, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULL"));
        get("/api/meetups/" + id, host).andExpect(jsonPath("$.waitCount").value(1));
        get("/api/meetups/" + id, seoktap).andExpect(jsonPath("$.waiting").value(true));
    }

    @Test
    void 연_사람이_정원을_늘리면_기다리던_사람에게_알린다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        registerDevice(horang, "horang-phone");
        long id = fullMeetup(host, anam);
        post("/api/meetups/" + id + "/wait", horang, null).andExpect(status().isOk());
        pushes.clear();

        put("/api/meetups/" + id + "/seats", anam, "{\"maxPeople\":3}").andExpect(status().isForbidden());
        put("/api/meetups/" + id + "/seats", host, "{\"maxPeople\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEATS_NOT_MORE"));
        put("/api/meetups/" + id + "/seats", host, "{\"maxPeople\":31}").andExpect(status().isBadRequest());
        assertThat(pushes.to("horang-phone")).isEmpty();

        put("/api/meetups/" + id + "/seats", host, "{\"maxPeople\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPeople").value(3))
                .andExpect(jsonPath("$.minPeople").value(2))
                .andExpect(jsonPath("$.waitCount").value(1));
        assertThat(types("horang-phone")).containsExactly(PushType.SPOT);
        get("/api/meetups/" + id, horang)
                .andExpect(jsonPath("$.canJoin").value(true))
                .andExpect(jsonPath("$.waiting").value(false));

        // 인원 제한이 없는 3교시는 늘릴 게 없다
        long unlimited = createMeetup(host, 2, null, Duration.ofDays(2), Duration.ofDays(1), null);
        get("/api/meetups/" + unlimited, host).andExpect(jsonPath("$.canAddSeats").value(false));
        put("/api/meetups/" + unlimited + "/seats", host, "{\"maxPeople\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNLIMITED"));
    }

    @Test
    void 대기가_있어도_3교시를_지우거나_탈퇴할_수_있다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        long id = fullMeetup(host, anam);
        post("/api/meetups/" + id + "/wait", horang, null).andExpect(status().isOk());
        long other = fullMeetup(host, anam);
        post("/api/meetups/" + other + "/wait", horang, null).andExpect(status().isOk());

        delete("/api/meetups/" + id, ownerToken()).andExpect(status().isNoContent());
        delete("/api/me", horang).andExpect(status().isNoContent());
        get("/api/meetups/" + other, host).andExpect(jsonPath("$.waitCount").value(0));
    }
}
