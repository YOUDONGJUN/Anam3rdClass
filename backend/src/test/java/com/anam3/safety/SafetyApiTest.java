package com.anam3.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.push.PushMessage;
import com.anam3.push.PushType;
import com.anam3.support.ApiTestSupport;

/** 앱스토어 UGC 정책: 신고 · 차단 · 부적절한 표현 거르기 */
class SafetyApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);

    private String godae;   // 고려대
    private String horang;  // 고려대
    private String sinchon; // 연세대

    @BeforeEach
    void users() throws Exception {
        godae = signup("godae", "김고대", 61);
        horang = signup("horang", "박호랑", 61);
        sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3);
    }

    private String report(String type, long targetId, String reason) {
        return """
                {"targetType":"%s","targetId":%d,"reason":"%s","detail":"확인 부탁드려요"}
                """.formatted(type, targetId, reason);
    }

    @Test
    void 신고는_그_학교_교수와_대학원장이_보고_처리한다() throws Exception {
        long id = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);
        String kuManager = managerToken("anam", "이안암", 60);
        String yonseiManager = managerToken("prof@yonsei.ac.kr", "정교수", 1);

        post("/api/reports", horang, report("MEETUP", id, "SPAM")).andExpect(status().isNoContent());
        post("/api/reports", horang, report("MEETUP", id, "SPAM"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));

        get("/api/admin/stats", kuManager).andExpect(jsonPath("$.openReports").value(1));
        long reportId = ((Number) read(get("/api/admin/reports", kuManager)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetType").value("MEETUP"))
                .andExpect(jsonPath("$[0].reason").value("SPAM"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].meetupId").value(id))
                .andExpect(jsonPath("$[0].memberName").value("김고대")) // 관리 화면이라 실명
                .andExpect(jsonPath("$[0].memberAffiliation").value("고려대 · 61기 · 인융"))
                .andExpect(jsonPath("$[0].memberRole").value("USER"))
                // 신고 당시 제목·내용 (나중에 고치거나 지워도 남는다)
                .andExpect(jsonPath("$[0].targetSnapshot").value("치맥 번개\n수업 끝나고 한잔\n장소: 안암역 앞"))
                .andExpect(jsonPath("$[0].reporter").doesNotExist()), "$[0].id")).longValue();
        get("/api/admin/reports", yonseiManager).andExpect(jsonPath("$", hasSize(0)));
        get("/api/admin/reports", ownerToken()).andExpect(jsonPath("$", hasSize(1)));

        put("/api/admin/reports/" + reportId, yonseiManager, "{\"resolved\":true}").andExpect(status().isForbidden());
        put("/api/admin/reports/" + reportId, kuManager, "{\"resolved\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        get("/api/admin/stats", kuManager).andExpect(jsonPath("$.openReports").value(0));
    }

    @Test
    void 사람은_3교시_참여_번호로_신고한다() throws Exception {
        long id = createMeetup(sinchon, 2, 6, IN_2H, IN_1H, null, "ALL");
        post("/api/meetups/" + id + "/join", godae, null).andExpect(status().isOk());
        long sinchonRef = participantRef(godae, id, 0);
        long myRef = participantRef(godae, id, 1);

        post("/api/reports", godae, report("MEMBER", sinchonRef, "ABUSE")).andExpect(status().isNoContent());
        post("/api/reports", godae, report("MEMBER", myRef, "ABUSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_REPORT"));

        // 사람 신고는 그 사람 학교(연세대) 교수에게 간다
        String yonseiManager = managerToken("prof@yonsei.ac.kr", "정교수", 1);
        get("/api/admin/reports", yonseiManager)
                .andExpect(jsonPath("$[0].targetType").value("MEMBER"))
                .andExpect(jsonPath("$[0].memberName").value("이신촌"))
                .andExpect(jsonPath("$[0].meetupId").value(id));
    }

    @Test
    void 볼_수_없는_3교시와_공지는_신고할_수_없다() throws Exception {
        long kuOnly = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);
        post("/api/reports", sinchon, report("MEETUP", kuOnly, "SPAM")).andExpect(status().isNotFound());
        long ref = participantRef(godae, kuOnly, 0);
        post("/api/reports", sinchon, report("MEMBER", ref, "SPAM")).andExpect(status().isNotFound());

        String kuManager = managerToken("prof", "최교수", 61);
        long notice = ((Number) read(post("/api/notices", kuManager, "{\"title\":\"고려대 공지\",\"content\":\"내용\"}"),
                "$.id")).longValue();
        post("/api/reports", sinchon, report("NOTICE", notice, "SPAM")).andExpect(status().isNotFound());
        post("/api/reports", horang, report("NOTICE", notice, "OTHER")).andExpect(status().isNoContent());
        post("/api/reports", horang, "{\"targetType\":\"MEETUP\",\"targetId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("신고 이유를 골라 주세요"));
    }

    @Test
    void 차단하면_서로의_3교시가_안_보이고_참여할_수_없다() throws Exception {
        long horangMeetup = createMeetup(horang, 2, 6, IN_2H, IN_1H, null);
        long godaeMeetup = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);
        post("/api/meetups/" + godaeMeetup + "/join", horang, null).andExpect(status().isOk());
        long horangRef = participantRef(godae, godaeMeetup, 1);

        long blockId = ((Number) read(post("/api/blocks", godae, "{\"ref\":" + horangRef + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("박*랑"))
                .andExpect(jsonPath("$.school.shortName").value("고려대")), "$.id")).longValue();
        post("/api/blocks", godae, "{\"ref\":" + horangRef + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BLOCKED"));
        post("/api/blocks", godae, "{\"ref\":" + participantRef(godae, godaeMeetup, 0) + "}")
                .andExpect(jsonPath("$.code").value("SELF_BLOCK"));

        // 나에게서도, 차단당한 사람에게서도 서로의 3교시가 목록에서 빠진다
        get("/api/meetups", godae).andExpect(jsonPath("$[*].id", contains((int) godaeMeetup)));
        get("/api/meetups", horang).andExpect(jsonPath("$[*].id", contains((int) horangMeetup)));
        get("/api/meetups/" + horangMeetup, godae).andExpect(jsonPath("$.canJoin").value(false));
        post("/api/meetups/" + horangMeetup + "/join", godae, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED"));

        get("/api/blocks", godae).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("박*랑"));
        get("/api/blocks", horang).andExpect(jsonPath("$", hasSize(0))); // 차단당한 사람에게는 알리지 않는다

        delete("/api/blocks/" + blockId, horang).andExpect(status().isNotFound());
        delete("/api/blocks/" + blockId, godae).andExpect(status().isNoContent());
        post("/api/meetups/" + horangMeetup + "/join", godae, null).andExpect(status().isOk());
    }

    @Test
    void 볼_수_없는_3교시_참여자는_차단할_수_없다() throws Exception {
        long kuOnly = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);
        post("/api/blocks", sinchon, "{\"ref\":" + participantRef(godae, kuOnly, 0) + "}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 심한_욕설은_올릴_수_없다() throws Exception {
        String bad = meetupJson("씨1발 오늘 한잔", 2, 4, T0.plus(IN_2H), T0.plus(IN_1H), null);
        post("/api/meetups", godae, bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INAPPROPRIATE"));
        post("/api/notices", managerToken("prof", "최교수", 61), "{\"title\":\"공지\",\"content\":\"이 병신들아\"}")
                .andExpect(jsonPath("$.code").value("INAPPROPRIATE"));
        // 평범한 말은 걸리지 않는다
        post("/api/meetups", godae, meetupJson("맥주병 신나게 시발점에서", 2, 4, T0.plus(IN_2H), T0.plus(IN_1H), null))
                .andExpect(status().isCreated());
    }

    private List<PushMessage> reportPushes(String device) {
        return pushes.to(device).stream().filter(m -> m.type() == PushType.REPORT).toList();
    }

    private int reportInbox(String key) {
        Integer n = jdbc.queryForObject("select count(*) from notifications where member_id = ? and type = 'REPORT'",
                Integer.class, memberId(key));
        return n == null ? 0 : n;
    }

    @Test
    void 새_신고는_처리할_수_있는_교수와_대학원장에게_알리고_처리하면_신고한_사람에게만_알린다() throws Exception {
        String kuManager = managerToken("anam", "이안암", 60);
        String yonseiManager = managerToken("seoktap@yonsei.ac.kr", "최석탑", 1);
        String owner = ownerToken();
        registerDevice(kuManager, "anam-phone");
        registerDevice(yonseiManager, "seoktap-phone");
        registerDevice(owner, "owner-phone");
        registerDevice(godae, "godae-phone");
        registerDevice(horang, "horang-phone");
        long meetup = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);

        post("/api/reports", horang, report("MEETUP", meetup, "SPAM")).andExpect(status().isNoContent());
        PushMessage filed = reportPushes("anam-phone").getFirst();
        assertThat(filed.body()).isEqualTo("새 신고가 들어왔어요 · 스팸·광고");
        assertThat(filed.path()).isEqualTo("/admin?tab=reports");
        assertThat(reportPushes("owner-phone")).hasSize(1);
        // 다른 학교 교수, 신고한 사람, 신고당한 사람은 받지 않는다
        assertThat(reportPushes("seoktap-phone")).isEmpty();
        assertThat(reportPushes("horang-phone")).isEmpty();
        assertThat(reportPushes("godae-phone")).isEmpty();
        assertThat(reportInbox("godae")).isZero();
        assertThat(reportInbox("anam")).isEqualTo(1);

        // 교수가 대상인 신고(교수가 쓴 공지)는 대학원장에게만 — 신고당한 교수는 받지 않는다
        long notice = ((Number) read(post("/api/notices", kuManager, "{\"title\":\"고려대 공지\",\"content\":\"내용\"}"),
                "$.id")).longValue();
        pushes.clear();
        post("/api/reports", horang, report("NOTICE", notice, "ABUSE")).andExpect(status().isNoContent());
        assertThat(reportPushes("anam-phone")).isEmpty();
        assertThat(reportPushes("owner-phone")).extracting(PushMessage::body)
                .containsExactly("새 신고가 들어왔어요 · 욕설·괴롭힘·혐오");

        // 처리하면 신고한 사람에게만 (어떻게 처리했는지는 알리지 않는다). 다시 열 때는 알리지 않는다
        pushes.clear();
        long reportId = ((Number) read(get("/api/admin/reports", kuManager), "$[0].id")).longValue();
        put("/api/admin/reports/" + reportId, kuManager, "{\"resolved\":false}").andExpect(status().isOk());
        assertThat(pushes.all()).isEmpty();
        put("/api/admin/reports/" + reportId, kuManager, "{\"resolved\":true}").andExpect(status().isOk());
        assertThat(reportPushes("horang-phone")).extracting(PushMessage::body)
                .containsExactly("신고를 처리했어요. 알려 줘서 고마워요");
        assertThat(reportPushes("godae-phone")).isEmpty();
        assertThat(reportInbox("godae")).isZero();
        // 이미 처리한 신고를 또 처리해도 다시 알리지 않는다
        put("/api/admin/reports/" + reportId, kuManager, "{\"resolved\":true}").andExpect(status().isOk());
        assertThat(reportPushes("horang-phone")).hasSize(1);
    }

    @Test
    void 신고_알림은_끌_수_있고_알림함에는_남는다() throws Exception {
        String kuManager = managerToken("anam", "이안암", 60);
        registerDevice(kuManager, "anam-phone");
        put("/api/me/push/settings", kuManager, "{\"off\":[\"REPORT\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushOff", contains("REPORT")));
        long meetup = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);
        post("/api/reports", horang, report("MEETUP", meetup, "SPAM")).andExpect(status().isNoContent());
        assertThat(reportPushes("anam-phone")).isEmpty();
        assertThat(reportInbox("anam")).isEqualTo(1);
    }
}
