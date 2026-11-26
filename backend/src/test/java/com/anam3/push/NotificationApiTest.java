package com.anam3.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.anam3.support.ApiTestSupport;

/** 알림함: 기기가 없는 웹 사용자도 내 3교시 소식을 본다 */
class NotificationApiTest extends ApiTestSupport {

    @Autowired
    NotificationService notificationService;

    private static final String LIST = "/api/me/notifications";
    private static final String UNREAD = "/api/me/notifications/unread-count";

    @Test
    void 참여하면_연_사람_알림함에_쌓이고_열어_보면_읽음이_된다() throws Exception {
        String host = signup("godae", "김고대", 61); // 앱 없이 웹만 쓴다 (기기 등록 없음)
        String anam = signup("anam", "이안암", 61);
        long id = createMeetup(host, 4, 6, Duration.ofDays(2), Duration.ofDays(1), null);

        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());

        get(UNREAD, host).andExpect(jsonPath("$.count").value(1));
        get(LIST, host)
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("JOIN"))
                .andExpect(jsonPath("$.items[0].title").value("치맥 번개"))
                .andExpect(jsonPath("$.items[0].body").value("한 명이 참여했어요 · 성사까지 2명"))
                .andExpect(jsonPath("$.items[0].path").value("/meetups/" + id))
                .andExpect(jsonPath("$.items[0].read").value(false));
        // 참여한 본인에게는 없다
        get(LIST, anam).andExpect(jsonPath("$.items", hasSize(0)));

        post("/api/me/notifications/read-all", host, null).andExpect(status().isNoContent());
        get(UNREAD, host).andExpect(jsonPath("$.count").value(0));
        get(LIST, host).andExpect(jsonPath("$.items[0].read").value(true));

        get(LIST, null).andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_3교시의_참여_알림은_한_줄로_합치고_최신_내용으로_바꾼다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        String seoktap = signup("seoktap", "최석탑", 61);
        long id = createMeetup(host, 5, 8, Duration.ofDays(2), Duration.ofDays(1), null);

        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(5));
        post("/api/meetups/" + id + "/join", horang, null).andExpect(status().isOk());
        get(LIST, host)
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].body").value("한 명이 참여했어요 · 성사까지 2명"));

        // 읽은 뒤에 온 알림도 같은 줄을 최신 내용으로 바꿔 다시 안 읽음으로 올린다 (지난 숫자가 남지 않게)
        post("/api/me/notifications/read-all", host, null).andExpect(status().isNoContent());
        post("/api/meetups/" + id + "/join", seoktap, null).andExpect(status().isOk());
        get(LIST, host)
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].body").value("한 명이 참여했어요 · 성사까지 1명"))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void 알림함을_불러온_뒤에_온_알림은_읽음으로_바꾸지_않는다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        String horang = signup("horang", "박호랑", 61);
        long first = createMeetup(host, 4, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        long second = createMeetup(host, 4, 6, Duration.ofDays(3), Duration.ofDays(2), null);

        post("/api/meetups/" + first + "/join", anam, null).andExpect(status().isOk());
        String seenUpTo = read(get(LIST, host), "$.items[0].createdAt");
        // 목록을 받은 뒤, 읽음 요청이 닿기 전에 새 알림이 온다
        clock.advance(Duration.ofSeconds(1));
        post("/api/meetups/" + second + "/join", horang, null).andExpect(status().isOk());

        post("/api/me/notifications/read-all?upTo=" + seenUpTo, host, null).andExpect(status().isNoContent());
        get(UNREAD, host).andExpect(jsonPath("$.count").value(1));
        get(LIST, host)
                .andExpect(jsonPath("$.items[0].path").value("/meetups/" + second))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].read").value(true));
    }

    @Test
    void 푸시를_꺼도_알림함에는_담고_학교_전체_알림은_담지_않는다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        put("/api/me/push/settings", host, "{\"off\":[\"JOIN\"]}").andExpect(status().isOk());
        long id = createMeetup(host, 4, 6, Duration.ofDays(2), Duration.ofDays(1), null);

        // 새 3교시 알림(우리 학교 전체)은 알림함에 담지 않는다 — 피드에서 보인다
        get(LIST, anam).andExpect(jsonPath("$.items", hasSize(0)));

        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        get(LIST, host).andExpect(jsonPath("$.items[0].type").value("JOIN"));
    }

    @Test
    void 지운_3교시의_알림과_탈퇴한_사람의_알림은_지운다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        long id = createMeetup(host, 4, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        get(LIST, host).andExpect(jsonPath("$.items", hasSize(1)));

        // 지우면(대학원장): 연 사람의 참여 알림은 사라지고, 참여자에게는 취소 알림이 3교시 목록으로 간다
        delete("/api/meetups/" + id, ownerToken()).andExpect(status().isNoContent());
        get(LIST, host) // 참여 알림은 지우고, 대학원장이 지웠다는 취소 알림만 남는다
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("CHANGED"));
        get(LIST, anam)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("CHANGED"))
                .andExpect(jsonPath("$.items[0].path").value("/"));

        long anamId = memberId("anam");
        delete("/api/me", anam).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from notifications where member_id = ?", Integer.class, anamId))
                .isZero();
    }

    @Test
    void 삼십일_지난_알림은_지운다() throws Exception {
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        long id = createMeetup(host, 4, 6, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());

        clock.advance(Duration.ofDays(29));
        assertThat(notificationService.purgeOld()).isZero();
        clock.advance(Duration.ofDays(2));
        assertThat(notificationService.purgeOld()).isEqualTo(1);
        // 31일 뒤라 로그인 토큰이 만료됐다 — DB 로 본다
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isZero();
    }
}
