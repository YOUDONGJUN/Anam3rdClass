package com.anam3.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.push.PushMessage;
import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/**
 * 제재: 경고가 쌓이면 1번째 경고 · 2번째 7일 정지 · 3번째 30일 정지 · 4번째 영구 정지.
 * 신고만으로는 제재하지 않고, 서로 다른 원우 3명이 신고하면 "주의"로 알린다 (T0 = 3/2(월) 18:00 한국 시간)
 */
class SanctionApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);

    private String prof;   // 고려대 교수
    private String anam;   // 고려대 원우 (경고 대상)
    private long anamId;

    @BeforeEach
    void people() throws Exception {
        prof = managerToken("prof", "정교수", 50);
        anam = signup("anam", "이안암", 61);
        anamId = memberId("anam");
        registerDevice(anam, "anam-phone");
        registerDevice(prof, "prof-phone");
    }

    private String warnUrl() {
        return "/api/admin/members/" + anamId + "/warn";
    }

    private static String warnBody(String reason, Long reportId) {
        return "{\"reason\":\"" + reason + "\",\"note\":\"톡방에서 욕설\"" + (reportId == null ? "" : ",\"reportId\":" + reportId)
                + "}";
    }

    @Test
    void 경고가_쌓이면_7일_30일_영구_정지로_올라간다() throws Exception {
        // 1번째: 경고만 — 계속 쓸 수 있고, 앱을 열면 경고 창
        post(warnUrl(), prof, warnBody("ABUSE", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("WARNING")).andExpect(jsonPath("$.strike").value(1));
        get("/api/me", anam).andExpect(status().isOk());
        assertThat(pushes.to("anam-phone")).extracting(PushMessage::title).contains("운영진 경고");
        long first = ((Number) read(get("/api/me/sanction-notice", anam)
                .andExpect(jsonPath("$.kind").value("WARNING"))
                .andExpect(jsonPath("$.strikes").value(1))
                .andExpect(jsonPath("$.nextLabel").value("7일 정지"))
                .andExpect(jsonPath("$.note").value("톡방에서 욕설")), "$.id")).longValue();
        post("/api/me/sanction-notice/" + first + "/ack", anam, null).andExpect(status().isNoContent());
        get("/api/me/sanction-notice", anam).andExpect(content().string(""));

        // 2번째: 7일 정지 — 바로 막히고, 연 3교시는 취소된다
        long hosted = createMeetup(anam, 2, 6, IN_2H, IN_1H, null);
        post(warnUrl(), prof, warnBody("ABUSE", null)).andExpect(jsonPath("$.kind").value("SUSPEND_7"))
                .andExpect(jsonPath("$.strike").value(2));
        get("/api/me", anam).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUSPENDED"))
                .andExpect(jsonPath("$.message", containsString("3/9(월) 18:00부터 다시 쓸 수 있어요")));
        kakaoLogin("anam").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SUSPENDED"));
        get("/api/meetups/" + hosted, prof).andExpect(jsonPath("$.status").value("CANCELED"));
        get("/api/admin/members", prof)
                .andExpect(jsonPath("$[?(@.id == " + anamId + ")].strikes").value(2))
                .andExpect(jsonPath("$[?(@.id == " + anamId + ")].suspendedUntil").isNotEmpty());

        // 7일이 지나면 저절로 풀리고, 정지 경고를 확인하는 창이 보인다
        clock.advance(Duration.ofDays(7).plusMinutes(1));
        get("/api/me", anam).andExpect(status().isOk());
        get("/api/me/sanction-notice", anam).andExpect(jsonPath("$.kind").value("SUSPEND_7"))
                .andExpect(jsonPath("$.nextLabel").value("30일 정지"));

        // 3번째: 30일 정지 · 4번째: 영구 정지
        post(warnUrl(), prof, warnBody("SPAM", null)).andExpect(jsonPath("$.kind").value("SUSPEND_30"));
        clock.advance(Duration.ofDays(31)); // 30일 정지도 끝났다 (로그인 토큰도 새로 받는다)
        prof = login("prof");
        anam = login("anam");
        post(warnUrl(), prof, warnBody("SPAM", null)).andExpect(jsonPath("$.kind").value("BAN"));
        get("/api/me", anam).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BANNED"));
        post(warnUrl(), prof, warnBody("SPAM", null)).andExpect(status().isBadRequest());

        get("/api/admin/members/" + anamId + "/sanctions", prof).andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].kind").value("BAN")).andExpect(jsonPath("$[0].strike").value(4))
                .andExpect(jsonPath("$[3].kind").value("WARNING")).andExpect(jsonPath("$[3].strike").value(1))
                .andExpect(jsonPath("$[0].actorName").value("정교수"));
    }

    @Test
    void 경고를_취소하면_단계에서_빠지고_그_정지가_풀린다() throws Exception {
        post(warnUrl(), prof, warnBody("ABUSE", null)).andExpect(status().isOk());
        post(warnUrl(), prof, warnBody("ABUSE", null)).andExpect(jsonPath("$.kind").value("SUSPEND_7"));
        get("/api/me", anam).andExpect(status().isForbidden());

        long latest = ((Number) read(get("/api/admin/members/" + anamId + "/sanctions", prof), "$[0].id")).longValue();
        post("/api/admin/sanctions/" + latest + "/revoke", prof, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revokedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].strike").doesNotExist());
        get("/api/me", anam).andExpect(status().isOk());

        // 취소한 것은 세지 않는다 → 다음 경고는 다시 2번째(7일 정지)
        post(warnUrl(), prof, warnBody("ABUSE", null)).andExpect(jsonPath("$.strike").value(2))
                .andExpect(jsonPath("$.kind").value("SUSPEND_7"));
    }

    @Test
    void 정지_풀기는_기간_정지도_푼다() throws Exception {
        post(warnUrl(), prof, warnBody("ABUSE", null));
        post(warnUrl(), prof, warnBody("ABUSE", null)).andExpect(jsonPath("$.kind").value("SUSPEND_7"));
        post("/api/admin/members/" + anamId + "/unban", prof, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist())
                .andExpect(jsonPath("$.strikes").value(2)); // 경고 기록은 남는다
        get("/api/me", anam).andExpect(status().isOk());
    }

    @Test
    void 교수는_자기_학교_원우만_경고하고_대학원장과_교수에게는_못_준다() throws Exception {
        String yonseiProf = managerToken("yprof@yonsei.ac.kr", "연교수", 1);
        post(warnUrl(), yonseiProf, warnBody("ABUSE", null)).andExpect(status().isForbidden());
        post("/api/admin/members/" + memberId("prof") + "/warn", managerToken("prof2", "두교수", 50),
                warnBody("ABUSE", null)).andExpect(status().isForbidden());
        ownerToken();
        post("/api/admin/members/" + memberId("owner") + "/warn", prof, warnBody("ABUSE", null))
                .andExpect(status().isBadRequest());
        // 대학원장은 다른 학교 원우도
        post(warnUrl(), ownerToken(), warnBody("ABUSE", null)).andExpect(status().isOk());
        // 남의 경고 창은 확인할 수 없다
        long id = ((Number) read(get("/api/admin/members/" + anamId + "/sanctions", prof), "$[0].id")).longValue();
        post("/api/me/sanction-notice/" + id + "/ack", prof, null).andExpect(status().isNotFound());
    }

    @Test
    void 신고만으로는_제재하지_않고_3명이_신고하면_주의로_알린다() throws Exception {
        long meetup = createMeetup(anam, 2, 6, IN_2H, IN_1H, null);
        String report = "{\"targetType\":\"MEETUP\",\"targetId\":" + meetup + ",\"reason\":\"SPAM\",\"detail\":\"\"}";
        String[][] reporters = {{"r1", "강일번"}, {"r2", "강이번"}, {"r3", "강삼번"}};
        for (String[] r : reporters) {
            post("/api/reports", signup(r[0], r[1], 61), report).andExpect(status().isNoContent());
        }
        assertThat(pushes.to("prof-phone")).filteredOn(p -> p.type() == PushType.REPORT)
                .extracting(PushMessage::title).containsExactly("새 신고", "새 신고", "주의 신고");
        get("/api/me", anam).andExpect(status().isOk()); // 자동으로 막지 않는다

        long reportId = ((Number) read(get("/api/admin/reports", prof)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].reporterCount").value(3))
                .andExpect(jsonPath("$[0].memberStrikes").value(0)), "$[0].id")).longValue();

        // 신고를 보고 경고하면 그 신고는 처리 완료
        post(warnUrl(), prof, warnBody("SPAM", reportId)).andExpect(jsonPath("$.kind").value("WARNING"));
        get("/api/admin/reports", prof)
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].memberStrikes").value(1));
        get("/api/admin/members/" + anamId + "/sanctions", prof).andExpect(jsonPath("$[0].reportId").value(reportId));

        // 다른 사람에 대한 신고로는 경고를 줄 수 없다
        String other = signup("other", "김다른", 61);
        post("/api/admin/members/" + memberId("other") + "/warn", prof, warnBody("SPAM", reportId))
                .andExpect(status().isBadRequest());
        assertThat(other).isNotBlank();
    }
}
