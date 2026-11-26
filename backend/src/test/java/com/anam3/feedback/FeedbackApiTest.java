package com.anam3.feedback;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/**
 * 3교시 후기. T0 = 3월 2일 18:00 (한국 시간). 모임은 20:00, 후기는 다음 날 0시(= T0 + 6시간)부터 7일 동안 받는다.
 */
class FeedbackApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);
    /** 3월 3일 01:00 (한국 시간): 다음 날이라 후기를 받는다 */
    private static final Instant NEXT_DAY = T0.plus(Duration.ofHours(7));
    /** 3월 10일 0시 (한국 시간): 후기 받는 기간이 끝난다 */
    private static final Instant CLOSES = Instant.parse("2026-03-09T15:00:00Z");

    private static final String GOOD = "{\"met\":true,\"rating\":\"GOOD\"}";
    private static final String BAD = "{\"met\":true,\"rating\":\"BAD\"}";

    private String host;     // 김고대
    private String anam;     // 이안암
    private String horang;   // 박호랑
    private String outsider; // 최석탑

    @BeforeEach
    void users() throws Exception {
        host = signup("godae", "김고대", 61);
        anam = signup("anam", "이안암", 60);
        horang = signup("horang", "박호랑", 61);
        outsider = signup("seoktap", "최석탑", 59);
    }

    /** 2명이 모이면 성사되는 3교시 (연 사람 + people) */
    private long reachedMeetup(String... people) throws Exception {
        long id = createMeetup(host, 2, 6, IN_2H, IN_1H, null);
        for (String p : people) {
            post("/api/meetups/" + id + "/join", p, null).andExpect(status().isOk());
        }
        return id;
    }

    private static String reportJson(long ref, boolean fromFeedback) {
        return """
                {"targetType":"MEMBER","targetId":%d,"reason":"ABUSE","detail":"계속 2차를 강요했어요","fromFeedback":%s}
                """.formatted(ref, fromFeedback);
    }

    @Test
    void 성사된_3교시는_다음_날부터_7일_동안_함께한_사람에게만_묻는다() throws Exception {
        long id = reachedMeetup(anam);

        // 모임 당일 밤: 아직 묻지 않는다
        clock.set(T0.plus(Duration.ofHours(4)));
        get("/api/feedback/pending", anam).andExpect(jsonPath("$", hasSize(0)));
        post("/api/meetups/" + id + "/feedback", anam, GOOD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ASKING"));

        clock.set(NEXT_DAY);
        get("/api/feedback/pending", anam)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].meetupId").value(id))
                .andExpect(jsonPath("$[0].participantCount").value(2))
                .andExpect(jsonPath("$[0].until").value(CLOSES.toString()));
        get("/api/feedback/pending", host).andExpect(jsonPath("$", hasSize(1))); // 연 사람에게도
        get("/api/feedback/pending", outsider).andExpect(jsonPath("$", hasSize(0)));

        get("/api/meetups/" + id, anam)
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.feedback.answered").value(false))
                .andExpect(jsonPath("$.feedback.until").value(CLOSES.toString()));
        get("/api/meetups/" + id, outsider).andExpect(jsonPath("$.feedback").isEmpty());
        post("/api/meetups/" + id + "/feedback", outsider, GOOD).andExpect(status().isForbidden());

        // 기간이 끝나면 더 묻지 않는다
        clock.set(CLOSES);
        get("/api/feedback/pending", anam).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.feedback").isEmpty());
        post("/api/meetups/" + id + "/feedback", anam, GOOD).andExpect(status().isBadRequest());
    }

    @Test
    void 한_번_답하면_다시_묻지_않고_바꿀_수_없다() throws Exception {
        long id = reachedMeetup(anam);
        clock.set(NEXT_DAY);

        post("/api/meetups/" + id + "/feedback", anam, "{\"met\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RATING_REQUIRED"));
        post("/api/meetups/" + id + "/feedback", anam, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MET_REQUIRED"));

        post("/api/meetups/" + id + "/feedback", anam, GOOD).andExpect(status().isNoContent());
        get("/api/feedback/pending", anam).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.feedback.answered").value(true));
        post("/api/meetups/" + id + "/feedback", anam, BAD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ANSWERED"));
        post("/api/meetups/" + id + "/feedback", anam, "{\"skip\":true}").andExpect(status().isConflict());

        // 안 모였다고 하면 만족도는 남지 않는다
        post("/api/meetups/" + id + "/feedback", host, "{\"met\":false,\"rating\":\"GOOD\"}")
                .andExpect(status().isNoContent());
        Integer rated = jdbc.queryForObject(
                "select count(*) from meetup_feedbacks where meetup_id = ? and rating is not null", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(rated).isEqualTo(1);
    }

    @Test
    void 닫으면_다시_묻지_않지만_기간_안에는_상세에서_답할_수_있다() throws Exception {
        long id = reachedMeetup(anam);
        clock.set(NEXT_DAY);

        post("/api/meetups/" + id + "/feedback", anam, "{\"skip\":true}").andExpect(status().isNoContent());
        post("/api/meetups/" + id + "/feedback", anam, "{\"skip\":true}").andExpect(status().isNoContent());
        get("/api/feedback/pending", anam).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.feedback.answered").value(false));

        post("/api/meetups/" + id + "/feedback", anam, GOOD).andExpect(status().isNoContent());
        get("/api/meetups/" + id, anam).andExpect(jsonPath("$.feedback.answered").value(true));
    }

    @Test
    void 무산되거나_취소된_3교시는_묻지_않는다() throws Exception {
        long failed = createMeetup(host, 3, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + failed + "/join", anam, null);
        long canceled = reachedMeetup(horang);
        post("/api/meetups/" + canceled + "/cancel", host, null).andExpect(status().isOk());

        clock.set(NEXT_DAY);
        get("/api/feedback/pending", host).andExpect(jsonPath("$", hasSize(0)));
        get("/api/meetups/" + failed, anam).andExpect(jsonPath("$.feedback").isEmpty());
        post("/api/meetups/" + failed + "/feedback", anam, GOOD).andExpect(status().isBadRequest());
    }

    @Test
    void 최소_인원_없이_열어_혼자_끝난_3교시는_후기도_정산도_묻지_않는다() throws Exception {
        long alone = createMeetup(host, null, 6, IN_2H, IN_1H, null); // 열자마자 성사, 아무도 안 옴

        clock.set(NEXT_DAY);
        get("/api/meetups/" + alone, host)
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.feedback").isEmpty())
                .andExpect(jsonPath("$.canSettle").value(false));
        get("/api/feedback/pending", host).andExpect(jsonPath("$", hasSize(0)));
        post("/api/meetups/" + alone + "/feedback", host, GOOD).andExpect(status().isBadRequest());
    }

    @Test
    void 아쉬웠어요_뒤_신고는_대학원장에게만_후기에서_표시가_보인다() throws Exception {
        long id = reachedMeetup(anam, horang);
        long horangRef = participantRef(anam, id, 2);
        long hostRef = participantRef(anam, id, 0);
        clock.set(NEXT_DAY);

        post("/api/meetups/" + id + "/feedback", anam, BAD).andExpect(status().isNoContent());
        post("/api/reports", anam, reportJson(horangRef, true)).andExpect(status().isNoContent());
        // '좋았어요'라고 답한 사람이 표시를 붙여 보내도 붙지 않는다
        post("/api/meetups/" + id + "/feedback", horang, GOOD).andExpect(status().isNoContent());
        post("/api/reports", horang, reportJson(hostRef, true)).andExpect(status().isNoContent());

        get("/api/admin/reports", ownerToken())
                .andExpect(jsonPath("$[?(@.memberName=='박호랑')].fromFeedback", contains(true)))
                .andExpect(jsonPath("$[?(@.memberName=='김고대')].fromFeedback", contains(false)));

        String kuManager = managerToken("prof", "정교수", 61);
        get("/api/admin/reports", kuManager)
                .andExpect(jsonPath("$[?(@.memberName=='박호랑')].fromFeedback", contains(false)));
    }

    @Test
    void 통계는_대학원장만_보고_응답이_적은_묶음은_숫자를_감춘다() throws Exception {
        String imeil = signup("imeil", "정이메", 62);
        long ku = reachedMeetup(anam, horang, outsider, imeil); // 고려대 5명
        long failed = createMeetup(host, 4, 6, IN_2H, IN_1H, null); // 무산
        long united = createMeetup(host, 2, 6, IN_2H, IN_1H, null, "ALL"); // 연합
        post("/api/meetups/" + united + "/join", anam, null);

        clock.set(NEXT_DAY);
        post("/api/meetups/" + ku + "/feedback", host, GOOD);
        post("/api/meetups/" + ku + "/feedback", anam, GOOD);
        post("/api/meetups/" + ku + "/feedback", horang, "{\"met\":true,\"rating\":\"OK\"}");
        post("/api/meetups/" + ku + "/feedback", outsider, BAD);
        post("/api/meetups/" + ku + "/feedback", imeil, "{\"met\":false}");
        post("/api/meetups/" + united + "/feedback", anam, GOOD).andExpect(status().isNoContent());
        long horangRef = participantRef(outsider, ku, 2);
        post("/api/reports", outsider, reportJson(horangRef, true)).andExpect(status().isNoContent());

        get("/api/admin/feedback-stats", host).andExpect(status().isForbidden());
        get("/api/admin/feedback-stats", managerToken("prof", "정교수", 61)).andExpect(status().isForbidden());

        String owner = ownerToken();
        get("/api/admin/feedback-stats?days=30", owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.minAnswers").value(5))
                .andExpect(jsonPath("$.feedbackReports").value(1))
                .andExpect(jsonPath("$.total.meetups").value(3))
                .andExpect(jsonPath("$.total.reached").value(2))
                .andExpect(jsonPath("$.total.participants").value(7))
                .andExpect(jsonPath("$.total.answers").value(6))
                // 전체 만족도·모임은 숫자를 보여주는 칸(고려대)만 더한다 — 감춘 연합 1명의 답을 빼서 알아낼 수 없게
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.total.enough").value(true))
                .andExpect(jsonPath("$.total.good").value(2))
                .andExpect(jsonPath("$.total.ok").value(1))
                .andExpect(jsonPath("$.total.bad").value(1))
                .andExpect(jsonPath("$.total.metKnown").value(1))
                .andExpect(jsonPath("$.total.metMeetups").value(1))
                .andExpect(jsonPath("$.groups[?(@.school.code=='KU')].meetups", contains(2)))
                .andExpect(jsonPath("$.groups[?(@.school.code=='KU')].answers", contains(5)))
                .andExpect(jsonPath("$.groups[?(@.school.code=='KU')].enough", contains(true)))
                .andExpect(jsonPath("$.groups[?(@.school.code=='KU')].good", contains(2)))
                .andExpect(jsonPath("$.groups[?(@.school.code=='YONSEI')].meetups", contains(0)))
                .andExpect(jsonPath("$.groups[-1].federation").value(true))
                .andExpect(jsonPath("$.groups[-1].answers").value(1))
                .andExpect(jsonPath("$.groups[-1].enough").value(false))
                .andExpect(jsonPath("$.groups[-1].good").isEmpty())
                .andExpect(jsonPath("$.groups[-1].metMeetups").isEmpty());

        get("/api/admin/feedback-stats", owner).andExpect(jsonPath("$.total.meetups").value(3));
        get("/api/admin/feedback-stats?days=0", owner).andExpect(status().isBadRequest());

        // 후기가 남은 3교시도 지울 수 있다 (지난 3교시는 대학원장·교수만)
        delete("/api/meetups/" + failed, owner).andExpect(status().isNoContent());
        delete("/api/meetups/" + ku, owner).andExpect(status().isNoContent());
    }
}
