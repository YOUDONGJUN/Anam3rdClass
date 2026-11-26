package com.anam3.admin;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

/** 관리자 조치 기록: 교수·대학원장이 한 일을 남기고, 대학원장만 본다 */
class AdminActionApiTest extends ApiTestSupport {

    private static final Duration IN_1H = Duration.ofHours(1);
    private static final Duration IN_2H = Duration.ofHours(2);

    private static String member(long id) {
        return "/api/admin/members/" + id;
    }

    private static String profile(String name, int cohort, long departmentId) {
        return """
                {"name":"%s","cohort":%d,"departmentId":%d}
                """.formatted(name, cohort, departmentId);
    }

    private long reportId(String token) throws Exception {
        return ((Number) read(get("/api/admin/reports", token), "$[0].id")).longValue();
    }

    @Test
    void 교수와_대학원장이_한_일이_기록되고_대학원장만_본다() throws Exception {
        String godae = signup("godae", "김고대", 61);
        String horang = signup("horang", "박호랑", 61);
        signup("seoktap", "최석탑", 61);
        String anam = managerToken("anam", "이안암", 60); // 교수 임명
        String owner = ownerToken();
        long godaeId = memberId("godae");
        long horangId = memberId("horang");

        clock.advance(Duration.ofMinutes(1));
        put(member(godaeId) + "/profile", anam, profile("김고려", 60, departmentId("KU", "인융")))
                .andExpect(status().isOk());
        put(member(godaeId) + "/profile", anam, profile("김고려", 60, departmentId("KU", "인융")))
                .andExpect(status().isOk()); // 바뀐 게 없으면 남기지 않는다
        post(member(horangId) + "/ban", anam, null).andExpect(status().isOk());
        post(member(horangId) + "/unban", anam, null).andExpect(status().isOk());

        // 남의 3교시를 지우면 남고, 내 3교시를 지우면 남지 않는다
        long godaeMeetup = createMeetup(godae, 2, 6, IN_2H, IN_1H, null);
        delete("/api/meetups/" + godaeMeetup, anam).andExpect(status().isNoContent());
        long anamMeetup = createMeetup(anam, 2, 6, IN_2H, IN_1H, null);
        delete("/api/meetups/" + anamMeetup, anam).andExpect(status().isNoContent());

        long reported = createMeetup(godae, 2, 6, Duration.ofHours(5), Duration.ofHours(4), null);
        post("/api/reports", horang, """
                {"targetType":"MEETUP","targetId":%d,"reason":"SPAM"}
                """.formatted(reported)).andExpect(status().isNoContent());
        long report = reportId(anam);
        put("/api/admin/reports/" + report, anam, "{\"resolved\":true}").andExpect(status().isOk());
        put("/api/admin/reports/" + report, anam, "{\"resolved\":true}").andExpect(status().isOk()); // 이미 처리됨

        post(member(memberId("seoktap")) + "/withdraw", owner, null).andExpect(status().isOk());
        post("/api/admin/schools/" + schoolId("KU") + "/departments", owner,
                "{\"name\":\"사이버국방학과\",\"shortName\":\"사국\"}").andExpect(status().isCreated());
        put(member(memberId("anam")) + "/role", owner, "{\"role\":\"USER\"}").andExpect(status().isOk());

        get("/api/admin/actions", anam).andExpect(status().isForbidden());
        get("/api/admin/actions", godae).andExpect(status().isForbidden());
        get("/api/admin/actions", owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action").value(contains("DISMISS", "ADD_DEPARTMENT", "WITHDRAW",
                        "RESOLVE_REPORT", "DELETE_MEETUP", "UNBAN", "BAN", "EDIT_PROFILE", "APPOINT")))
                .andExpect(jsonPath("$[0].label").value("교수 해임"))
                .andExpect(jsonPath("$[0].targetName").value("이안암"))
                .andExpect(jsonPath("$[1].detail").value("고려대 · 사이버국방학과"))
                .andExpect(jsonPath("$[1].targetMemberId").value(nullValue()))
                // 탈퇴한 원우는 이름 없이 남는다
                .andExpect(jsonPath("$[2].targetName").value("탈퇴한 원우"))
                .andExpect(jsonPath("$[2].targetAffiliation").value(nullValue()))
                .andExpect(jsonPath("$[2].actorRole").value("OWNER"))
                .andExpect(jsonPath("$[3].detail").value("3교시 신고 · 스팸·광고"))
                .andExpect(jsonPath("$[3].targetMemberId").value(godaeId))
                .andExpect(jsonPath("$[3].targetMeetupId").value(reported))
                .andExpect(jsonPath("$[4].detail").value("치맥 번개"))
                .andExpect(jsonPath("$[4].targetMeetupId").value(godaeMeetup))
                .andExpect(jsonPath("$[4].targetName").value("김고려"))
                // 해임된 뒤에도 그때 직책(교수)으로 보인다
                .andExpect(jsonPath("$[4].actorName").value("이안암"))
                .andExpect(jsonPath("$[4].actorRole").value("MANAGER"))
                .andExpect(jsonPath("$[7].label").value("정보 고치기"))
                // 이름은 바뀌었다는 것만 남긴다
                .andExpect(jsonPath("$[7].detail").value("이름 · 기수 61기 → 60기"))
                .andExpect(jsonPath("$[7].targetAffiliation").value("고려대 · 60기"))
                .andExpect(jsonPath("$[8].actorRole").value("OWNER"));
    }

    @Test
    void 공지를_지우고_신고를_다시_열어도_남는다() throws Exception {
        String anam = managerToken("anam", "이안암", 60);
        String horang = signup("horang", "박호랑", 61);
        String owner = ownerToken();
        long notice = ((Number) read(post("/api/notices", anam, "{\"title\":\"스터디 모집\",\"content\":\"내용\"}")
                .andExpect(status().isCreated()), "$.id")).longValue();
        post("/api/reports", horang, """
                {"targetType":"NOTICE","targetId":%d,"reason":"ABUSE"}
                """.formatted(notice)).andExpect(status().isNoContent());
        long report = reportId(owner);
        put("/api/admin/reports/" + report, owner, "{\"resolved\":true}").andExpect(status().isOk());
        put("/api/admin/reports/" + report, owner, "{\"resolved\":false}").andExpect(status().isOk());
        delete("/api/notices/" + notice, owner).andExpect(status().isNoContent());
        // 내 공지를 지운 건 남지 않는다
        long mine = ((Number) read(post("/api/notices", anam, "{\"title\":\"내 공지\",\"content\":\"내용\"}"),
                "$.id")).longValue();
        delete("/api/notices/" + mine, anam).andExpect(status().isNoContent());

        get("/api/admin/actions", owner)
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].action")
                        .value(contains("DELETE_NOTICE", "REOPEN_REPORT", "RESOLVE_REPORT", "APPOINT")))
                .andExpect(jsonPath("$[0].detail").value("스터디 모집"))
                .andExpect(jsonPath("$[0].targetName").value("이안암"))
                .andExpect(jsonPath("$[1].detail").value("공지 신고 · 욕설·괴롭힘·혐오"));
    }
}
