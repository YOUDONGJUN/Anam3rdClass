package com.anam3.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

class AdminApiTest extends ApiTestSupport {

    private String admin;
    private String minsu;

    @BeforeEach
    void users() throws Exception {
        admin = adminToken();
        minsu = signup("minsu", "김민수", 2025);
    }

    private long memberId(String loginId) throws Exception {
        Integer id = ((java.util.List<Integer>) read(get("/api/admin/members", admin),
                "$[?(@.loginId == '" + loginId + "')].id")).get(0);
        return id.longValue();
    }

    @Test
    void 일반_회원은_운영자_API에_못_들어온다() throws Exception {
        get("/api/admin/members", minsu).andExpect(status().isForbidden());
        get("/api/admin/settings", minsu).andExpect(status().isForbidden());
        get("/api/admin/members", null).andExpect(status().isUnauthorized());
    }

    @Test
    void 운영자는_실명_회원목록을_본다() throws Exception {
        get("/api/admin/members", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.loginId == 'minsu')].name").value("김민수"));
    }

    @Test
    void 차단하면_기존_토큰도_막히고_로그인도_안된다() throws Exception {
        long id = memberId("minsu");

        post("/api/admin/members/" + id + "/ban", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BANNED"));

        get("/api/me", minsu).andExpect(status().isUnauthorized());
        post("/api/auth/login", null, "{\"loginId\":\"minsu\",\"password\":\"password1234\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANNED"));

        post("/api/admin/members/" + id + "/unban", admin, null).andExpect(status().isOk());
        get("/api/me", minsu).andExpect(status().isOk());
    }

    @Test
    void 운영자는_차단할_수_없다() throws Exception {
        post("/api/admin/members/" + memberId("testadmin") + "/ban", admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_BAN_ADMIN"));
    }

    @Test
    void 입장코드를_바꾸면_새_코드로만_가입된다() throws Exception {
        get("/api/admin/settings", admin).andExpect(jsonPath("$.inviteCode").value(INVITE_CODE));

        put("/api/admin/settings/invite-code", admin, "{\"inviteCode\":\"swai2026\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("swai2026"));

        post("/api/auth/signup", null, """
                {"inviteCode":"%s","loginId":"newbie","password":"password1234","name":"신입생","admissionYear":2026}
                """.formatted(INVITE_CODE)).andExpect(status().isBadRequest());
        post("/api/auth/signup", null, """
                {"inviteCode":"swai2026","loginId":"newbie","password":"password1234","name":"신입생","admissionYear":2026}
                """).andExpect(status().isCreated());
    }

    @Test
    void 임시_비밀번호_발급() throws Exception {
        String temp = read(post("/api/admin/members/" + memberId("minsu") + "/reset-password", admin, null)
                .andExpect(status().isOk()), "$.temporaryPassword");

        login("minsu", temp);
    }

    @Test
    void 통계와_전체_번개() throws Exception {
        long id = createMeetup(minsu, 2, 4, Duration.ofHours(2), Duration.ofHours(1), null);

        get("/api/admin/stats", admin)
                .andExpect(jsonPath("$.members").value(2))
                .andExpect(jsonPath("$.meetups").value(1))
                .andExpect(jsonPath("$.confirmedMeetups").value(0));

        get("/api/admin/meetups", admin)
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].host.name").value("김민수"));
    }
}
