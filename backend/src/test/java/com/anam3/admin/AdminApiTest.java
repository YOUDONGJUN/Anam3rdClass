package com.anam3.admin;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

class AdminApiTest extends ApiTestSupport {

    private String owner;   // 대학원장 박관리 (고려대)
    private String manager; // 고려대 교수 이안암
    private String godae;   // 고려대 원우 김고대

    @BeforeEach
    void users() throws Exception {
        owner = ownerToken();
        manager = managerToken("anam", "이안암", 60);
        godae = signup("godae", "김고대", 61);
    }

    private String member(long id) {
        return "/api/admin/members/" + id;
    }

    private String profile(String name, int cohort, long departmentId) {
        return "{\"name\":\"%s\",\"cohort\":%d,\"departmentId\":%d}".formatted(name, cohort, departmentId);
    }

    @Test
    void 원우는_관리_API에_못_들어온다() throws Exception {
        get("/api/admin/members", godae).andExpect(status().isForbidden());
        get("/api/admin/schools", godae).andExpect(status().isForbidden());
        get("/api/admin/reports", godae).andExpect(status().isForbidden());
        get("/api/admin/members", null).andExpect(status().isUnauthorized());
    }

    @Test
    void 교수는_자기_학교_원우만_보고_이메일은_가려진다() throws Exception {
        signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));

        get("/api/admin/members", manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '김고대')].email").value("go***@korea.ac.kr"))
                .andExpect(jsonPath("$[?(@.name == '김고대')].school.shortName").value("고려대"))
                .andExpect(jsonPath("$[?(@.name == '김고대')].departmentShortLabel").value("인융"))
                .andExpect(jsonPath("$[?(@.name == '김고대')].kakaoLinked").value(true))
                .andExpect(jsonPath("$[?(@.name == '이안암')].role").value("MANAGER"))
                .andExpect(jsonPath("$[*].name").value(not(hasItem("이신촌"))));

        get("/api/admin/members", owner)
                .andExpect(jsonPath("$[?(@.name == '김고대')].email").value("godae@korea.ac.kr"))
                .andExpect(jsonPath("$[?(@.name == '이신촌')].school.code").value("YONSEI"));
    }

    @Test
    void 교수는_원우를_차단하고_차단된_원우는_로그인할_수_없다() throws Exception {
        long id = memberId("godae");

        post(member(id) + "/ban", manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BANNED"));

        get("/api/me", godae).andExpect(status().isUnauthorized());
        kakaoLogin("godae").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BANNED"));

        post(member(id) + "/unban", manager, null).andExpect(status().isOk());
        get("/api/me", godae).andExpect(status().isOk());
    }

    @Test
    void 이용을_막으면_그_원우가_연_3교시는_취소되고_모집_중인_3교시에서는_빠진다() throws Exception {
        String seoktap = signup("seoktap", "최석탑", 62);
        long hosted = createMeetup(godae, 2, 6, Duration.ofHours(3), Duration.ofHours(2), null);
        long joined = createMeetup(seoktap, 2, 6, Duration.ofHours(3), Duration.ofHours(2), null);
        post("/api/meetups/" + joined + "/join", godae, null).andExpect(status().isOk());
        get("/api/meetups/" + joined, seoktap).andExpect(jsonPath("$.participantCount").value(2));

        post(member(memberId("godae")) + "/ban", manager, null).andExpect(status().isOk());

        get("/api/meetups/" + hosted, seoktap).andExpect(jsonPath("$.status").value("CANCELED"));
        get("/api/meetups/" + joined, seoktap).andExpect(jsonPath("$.participantCount").value(1));
    }

    @Test
    void 교수는_교수와_대학원장과_다른_학교_원우를_관리할_수_없다() throws Exception {
        managerToken("horang", "박호랑", 61);
        signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        long sinchon = memberId("sinchon@yonsei.ac.kr");

        post(member(memberId("horang")) + "/ban", manager, null).andExpect(status().isForbidden());
        put(member(memberId("horang")) + "/profile", manager, profile("박호랑", 60, departmentId("KU", "인융")))
                .andExpect(status().isForbidden());
        post(member(memberId("owner")) + "/ban", manager, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_BAN_OWNER"));
        post(member(memberId("owner")) + "/ban", owner, null)
                .andExpect(jsonPath("$.code").value("CANNOT_BAN_OWNER"));

        post(member(sinchon) + "/ban", manager, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("우리 학교 원우만 관리할 수 있어요"));
        // 대학원장은 모든 학교
        post(member(sinchon) + "/ban", owner, null).andExpect(jsonPath("$.status").value("BANNED"));
    }

    @Test
    void 교수_임명과_해임은_대학원장만() throws Exception {
        long id = memberId("godae");

        put(member(id) + "/role", manager, "{\"role\":\"MANAGER\"}").andExpect(status().isForbidden());
        put(member(id) + "/role", owner, "{\"role\":\"MANAGER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
        get("/api/admin/members", godae).andExpect(status().isOk()); // 바로 관리 화면에 들어온다

        put(member(id) + "/role", owner, "{\"role\":\"USER\"}").andExpect(jsonPath("$.role").value("USER"));
        get("/api/admin/members", godae).andExpect(status().isForbidden());
    }

    @Test
    void 대학원장은_넘기거나_바꿀_수_없다() throws Exception {
        put(member(memberId("godae")) + "/role", owner, "{\"role\":\"OWNER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        put(member(memberId("owner")) + "/role", owner, "{\"role\":\"USER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWNER"));
    }

    @Test
    void 차단하면_교수_직책도_내려놓는다() throws Exception {
        post(member(memberId("anam")) + "/ban", owner, null)
                .andExpect(jsonPath("$.status").value("BANNED"))
                .andExpect(jsonPath("$.role").value("USER"));
        put(member(memberId("anam")) + "/role", owner, "{\"role\":\"MANAGER\"}")
                .andExpect(jsonPath("$.code").value("BANNED_MEMBER"));
    }

    @Test
    void 원우의_이름_기수_학과는_교수가_고쳐준다() throws Exception {
        put(member(memberId("godae")) + "/profile", manager, profile("김고대", 62, departmentId("KU", "빅융")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortLabel").value("62기"))
                .andExpect(jsonPath("$.departmentLabel").value("빅데이터융합학과"));
        get("/api/me", godae).andExpect(jsonPath("$.cohort").value(62));

        put(member(memberId("godae")) + "/profile", manager, profile("김고대", 63, departmentId("KU", "인융")))
                .andExpect(jsonPath("$.code").value("INVALID_COHORT"));
        // 다른 학교 학과는 넣을 수 없다
        put(member(memberId("godae")) + "/profile", manager, profile("김고대", 61, departmentId("HANYANG", "인시")))
                .andExpect(jsonPath("$.code").value("INVALID_DEPARTMENT"));
    }

    @Test
    void 이메일_지정은_대학원장만_카카오로_로그인하기_전_회원에게만() throws Exception {
        put(member(memberId("godae")) + "/email", manager, "{\"email\":\"x@korea.ac.kr\"}")
                .andExpect(status().isForbidden());
        put(member(memberId("godae")) + "/email", owner, "{\"email\":\"x@korea.ac.kr\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
    }

    @Test
    void 학교_정보와_최신_기수는_대학원장만_바꾼다() throws Exception {
        long ku = schoolId("KU");
        String body = """
                {"name":"고려대학교","shortName":"고려대","graduateSchool":"SW·AI융합대학원","latestCohort":63}
                """;
        put("/api/admin/schools/" + ku, manager, body).andExpect(status().isForbidden());
        get("/api/admin/schools", manager).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestCohort").value(62));

        put("/api/admin/schools/" + ku, owner, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestCohort").value(63));
        get("/api/auth/signup-options", null).andExpect(jsonPath("$.schools[0].latestCohort").value(63));
        put(member(memberId("godae")) + "/profile", manager, profile("김고대", 63, departmentId("KU", "인융")))
                .andExpect(status().isOk());
    }

    @Test
    void 학과는_대학원장이_더하고_이름을_바꾸고_숨긴다() throws Exception {
        long yonsei = schoolId("YONSEI");
        String add = "{\"name\":\"데이터사이언스전공\",\"shortName\":\"데사\"}";
        post("/api/admin/schools/" + yonsei + "/departments", manager, add).andExpect(status().isForbidden());
        post("/api/admin/schools/" + yonsei + "/departments", owner, add)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[1].departments[*].shortName").value(contains("인컴", "인공", "데사")));

        long ds = departmentId("YONSEI", "데사");
        String token = signup("sinchon@yonsei.ac.kr", "이신촌", 3, ds);
        get("/api/me", token).andExpect(jsonPath("$.departmentShortLabel").value("데사"));

        // 숨기면 가입 화면에서 빠지고 새로 고를 수 없다. 이미 그 학과인 원우는 그대로
        put("/api/admin/departments/" + ds, owner, "{\"name\":\"데이터사이언스전공\",\"shortName\":\"데사\",\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].departments[2].active").value(false));
        get("/api/auth/signup-options", null)
                .andExpect(jsonPath("$.schools[1].departments[*].shortName").value(contains("인컴", "인공")));
        get("/api/me", token).andExpect(jsonPath("$.departmentShortLabel").value("데사"));
        String profileToken = verifyEmail(startSignup("sinchon2"), "sinchon2@yonsei.ac.kr");
        post("/api/auth/signup", null, profileJson(profileToken, "박신촌", 3, ds))
                .andExpect(jsonPath("$.code").value("INVALID_DEPARTMENT"));

        // 이름 바꾸기 → 이미 그 학과인 원우에게도 바뀐 이름으로 보인다
        put("/api/admin/departments/" + ds, owner, "{\"name\":\"데이터과학전공\",\"shortName\":\"데과\",\"active\":true}")
                .andExpect(status().isOk());
        get("/api/me", token).andExpect(jsonPath("$.departmentShortLabel").value("데과"));
    }

    @Test
    void 이메일_도메인은_대학원장이_더하고_뺀다_하나는_남아야_한다() throws Exception {
        long hanyang = schoolId("HANYANG");
        requestCode(startSignup("wangsip"), "wangsip@hy.ac.kr")
                .andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_REQUIRED"));

        post("/api/admin/schools/" + hanyang + "/domains", manager, "{\"domain\":\"hy.ac.kr\"}")
                .andExpect(status().isForbidden());
        post("/api/admin/schools/" + hanyang + "/domains", owner, "{\"domain\":\"@HY.ac.kr\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[4].emailDomains[*].domain").value(contains("hanyang.ac.kr", "hy.ac.kr")));
        post("/api/admin/schools/" + hanyang + "/domains", owner, "{\"domain\":\"korea.ac.kr\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOMAIN"));

        clock.advance(Duration.ofSeconds(61));
        requestCode(startSignup("wangsip2"), "wangsip@hy.ac.kr")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("한양대학교"));

        Long first = jdbc.queryForObject("select id from school_email_domains where domain = 'hanyang.ac.kr'",
                Long.class);
        Long added = jdbc.queryForObject("select id from school_email_domains where domain = 'hy.ac.kr'", Long.class);
        delete("/api/admin/schools/" + hanyang + "/domains/" + added, owner).andExpect(status().isOk());
        delete("/api/admin/schools/" + hanyang + "/domains/" + first, owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAST_DOMAIN"));
    }

    @Test
    void 학교는_대학원장이_추가하고_그_학교_이메일로_가입할_수_있다() throws Exception {
        String school = """
                {"name":"국민대학교","shortName":"국민대","graduateSchool":"소프트웨어융합대학원","latestCohort":20,
                 "emailDomain":"@Kookmin.ac.kr"}
                """;
        post("/api/admin/schools", manager, school).andExpect(status().isForbidden());

        String signupToken = startSignup("kookmin");
        requestCode(signupToken, "student@kookmin.ac.kr").andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_REQUIRED"));

        post("/api/admin/schools", owner, school)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[5].shortName").value("국민대"))
                .andExpect(jsonPath("$[5].code").doesNotExist())
                .andExpect(jsonPath("$[5].emailDomains[0].domain").value("kookmin.ac.kr"));
        post("/api/admin/schools", owner, school)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOMAIN"));

        requestCode(signupToken, "student@kookmin.ac.kr")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("국민대학교"));
    }

    @Test
    void 통계와_전체_3교시는_교수에게_자기_학교만() throws Exception {
        long id = createMeetup(godae, 2, 4, Duration.ofHours(2), Duration.ofHours(1), null);
        String sinchon = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        long yonseiMeetup = createMeetup(sinchon, 2, 4, Duration.ofHours(3), Duration.ofHours(1), null);

        get("/api/admin/stats", manager)
                .andExpect(jsonPath("$.members").value(3)) // 박관리·이안암·김고대 (이신촌은 연세대)
                .andExpect(jsonPath("$.meetups").value(1))
                .andExpect(jsonPath("$.confirmedMeetups").value(0))
                .andExpect(jsonPath("$.openReports").value(0));
        get("/api/admin/stats", owner)
                .andExpect(jsonPath("$.members").value(4))
                .andExpect(jsonPath("$.meetups").value(2));

        get("/api/admin/meetups", owner)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].host.name").value("김고대"))
                .andExpect(jsonPath("$[?(@.id == " + yonseiMeetup + ")].school.shortName").value("연세대"));
        // 교수에게는 자기 학교 3교시만, 이름도 가려진다
        get("/api/admin/meetups", manager)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].host.name").value("김*대"));
    }

    @Test
    void 대학원장은_원우를_탈퇴시킬_수_있다() throws Exception {
        long id = memberId("godae");
        long hosted = createMeetup(godae, 2, 6, Duration.ofHours(3), Duration.ofHours(2), null);

        post(member(id) + "/withdraw", manager, null).andExpect(status().isForbidden());
        post(member(id) + "/withdraw", godae, null).andExpect(status().isForbidden());
        post(member(id) + "/withdraw", owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.name").value("탈퇴한 원우"));

        get("/api/me", godae).andExpect(status().isUnauthorized());
        get("/api/meetups/" + hosted, manager).andExpect(jsonPath("$.status").value("CANCELED"));
        post(member(id) + "/withdraw", owner, null).andExpect(jsonPath("$.code").value("ALREADY_WITHDRAWN"));
        post(member(memberId("owner")) + "/withdraw", owner, null)
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_WITHDRAW"));

        // 같은 카카오 · 학교 이메일로 다시 가입하면 새 계정 (인증코드는 1분에 한 번)
        clock.advance(Duration.ofMinutes(2));
        signup("godae", "김고대", 61);
    }
}
