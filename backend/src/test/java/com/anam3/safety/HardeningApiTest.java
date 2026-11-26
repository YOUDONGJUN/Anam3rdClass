package com.anam3.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.anam3.support.ApiTestSupport;

/** 점검에서 나온 권한·남용 문제가 다시 생기지 않게 */
class HardeningApiTest extends ApiTestSupport {

    private static final String PASSWORD = "anam3rd2026";

    private String emailStart() throws Exception {
        return read(post("/api/auth/email/start", null, null).andExpect(status().isOk()), "$.signupToken");
    }

    /** 카카오 없이 학교 메일 + 비밀번호로 가입 → 로그인 토큰 */
    private String emailSignup(String key, String name) throws Exception {
        String start = emailStart();
        requestCode(start, email(key)).andExpect(status().isOk());
        String token = read(verifyCode(start, email(key), mailer.lastCode(email(key)))
                .andExpect(jsonPath("$.step").value("PROFILE_REQUIRED")), "$.signupToken");
        return read(post("/api/auth/signup", null, """
                {"signupToken":"%s","name":"%s","cohort":61,"departmentId":%d,"agreeTerms":true,"password":"%s"}
                """.formatted(token, name, departmentId("KU", "인융"), PASSWORD)).andExpect(status().isCreated()),
                "$.token");
    }

    private ResultActions emailLogin(String email, String password) throws Exception {
        return post("/api/auth/email/login", null, """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    @Test
    void 교수는_자기나_다른_교수에_대한_신고를_보거나_닫지_못한다() throws Exception {
        String owner = ownerToken();
        String manager = managerToken("horang", "박호랑", 61);
        String godae = signup("godae", "김고대", 61);
        long meetup = createMeetup(manager, null, null, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + meetup + "/join", godae, null).andExpect(status().isOk());
        post("/api/reports", godae, """
                {"targetType":"MEMBER","targetId":%d,"reason":"ABUSE","detail":"교수님이 반말을 해요"}
                """.formatted(participantRef(godae, meetup, 0))).andExpect(status().isNoContent());

        get("/api/admin/reports", manager).andExpect(jsonPath("$", hasSize(0)));
        get("/api/admin/stats", manager).andExpect(jsonPath("$.openReports").value(0));
        long reportId = ((Number) read(get("/api/admin/reports", owner).andExpect(jsonPath("$", hasSize(1))),
                "$[0].id")).longValue();
        put("/api/admin/reports/" + reportId, manager, "{\"resolved\":true}").andExpect(status().isForbidden());
        put("/api/admin/reports/" + reportId, owner, "{\"resolved\":true}").andExpect(status().isOk());
    }

    @Test
    void 대학원장이_연_3교시는_교수가_취소하거나_지우지_못한다() throws Exception {
        String owner = ownerToken();
        String manager = managerToken("horang", "박호랑", 61);
        long id = createMeetup(owner, null, null, Duration.ofDays(2), Duration.ofDays(1), null);

        get("/api/meetups/" + id, manager)
                .andExpect(jsonPath("$.canCancel").value(false))
                .andExpect(jsonPath("$.canDelete").value(false));
        post("/api/meetups/" + id + "/cancel", manager, null).andExpect(status().isForbidden());
        delete("/api/meetups/" + id, manager).andExpect(status().isForbidden());

        // 일반 원우가 연 3교시는 그 학교 교수가 취소할 수 있다
        String godae = signup("godae", "김고대", 61);
        long byMember = createMeetup(godae, null, null, Duration.ofDays(2), Duration.ofDays(1), null);
        post("/api/meetups/" + byMember + "/cancel", manager, null).andExpect(status().isOk());
        post("/api/meetups/" + id + "/cancel", owner, null).andExpect(status().isOk());
    }

    @Test
    void 기수가_바뀐_참여자도_우리_기수만_3교시에서_나갈_수_있다() throws Exception {
        String owner = ownerToken();
        String host = signup("godae", "김고대", 61);
        String anam = signup("anam", "이안암", 61);
        long id = createMeetup(host, 3, null, Duration.ofDays(2), Duration.ofDays(1), null, "COHORT");
        post("/api/meetups/" + id + "/join", anam, null).andExpect(status().isOk());
        put("/api/admin/members/" + memberId("anam") + "/profile", owner, """
                {"name":"이안암","cohort":62,"departmentId":%d}
                """.formatted(departmentId("KU", "인융"))).andExpect(status().isOk());

        delete("/api/meetups/" + id + "/join", anam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false));
        get("/api/meetups/" + id, anam).andExpect(status().isForbidden()); // 이제는 볼 수 없다
    }

    @Test
    void 대학원장은_이메일로_가입한_원우의_이메일을_바꾸지_못한다() throws Exception {
        String owner = ownerToken();
        emailSignup("godae", "김고대");
        put("/api/admin/members/" + memberId("godae") + "/email", owner, "{\"email\":\"other@korea.ac.kr\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
    }

    @Test
    void 이용_제한된_원우의_메일을_새_카카오로_인증해도_아무것도_바뀌지_않는다() throws Exception {
        String owner = ownerToken();
        signup("godae", "김고대", 61);
        Long before = jdbc.queryForObject("select kakao_id from members where email = ?", Long.class, email("godae"));
        post("/api/admin/members/" + memberId("godae") + "/ban", owner, null).andExpect(status().isOk());

        String other = startSignup("godae-new-kakao");
        requestCode(other, email("godae")).andExpect(status().isOk());
        verifyCode(other, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANNED"));
        assertThat(jdbc.queryForObject("select kakao_id from members where email = ?", Long.class, email("godae")))
                .isEqualTo(before);
    }

    @Test
    void 너무_긴_비밀번호는_400으로_막는다() throws Exception {
        String longPassword = "가".repeat(30) + "a1"; // 32자, 92바이트 (BCrypt 는 72바이트까지)
        String start = emailStart();
        requestCode(start, email("godae")).andExpect(status().isOk());
        String token = read(verifyCode(start, email("godae"), mailer.lastCode(email("godae"))), "$.signupToken");
        post("/api/auth/signup", null, """
                {"signupToken":"%s","name":"김고대","cohort":61,"departmentId":%d,"agreeTerms":true,"password":"%s"}
                """.formatted(token, departmentId("KU", "인융"), longPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
        emailLogin(email("nobody"), longPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기의_로그인이_풀린다() throws Exception {
        String first = emailSignup("godae", "김고대");
        String second = read(emailLogin(email("godae"), PASSWORD).andExpect(status().isOk()), "$.token");
        get("/api/me", first).andExpect(status().isOk());

        // 비밀번호 찾기로 새 비밀번호
        String start = emailStart();
        requestCode(start, email("godae")).andExpect(status().isOk());
        String token = read(verifyCode(start, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(jsonPath("$.step").value("PASSWORD_REQUIRED")), "$.signupToken");
        String fresh = read(post("/api/auth/password", null, """
                {"signupToken":"%s","password":"newpass2027"}
                """.formatted(token)).andExpect(status().isOk()), "$.token");

        get("/api/me", first).andExpect(status().isUnauthorized());
        get("/api/me", second).andExpect(status().isUnauthorized());
        get("/api/me", fresh).andExpect(status().isOk());
    }

    @Test
    void 이메일로_시작한_인증코드는_한_IP에서_한_시간에_스무_번까지라_모두의_메일_한도를_못_쓴다() throws Exception {
        for (int i = 0; i < 20; i++) {
            postFrom("203.0.113.5", "/api/auth/email/code", codeJson(emailStart(), "guess" + i + "@korea.ac.kr"))
                    .andExpect(status().isOk());
        }
        postFrom("203.0.113.5", "/api/auth/email/code", codeJson(emailStart(), "guess99@korea.ac.kr"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY"));
        // 다른 곳의 원우는 그대로 받는다
        postFrom("198.51.100.20", "/api/auth/email/code", codeJson(emailStart(), email("godae")))
                .andExpect(status().isOk());
    }

    private static String codeJson(String signupToken, String email) {
        return """
                {"signupToken":"%s","email":"%s"}
                """.formatted(signupToken, email);
    }

    @Test
    void 이메일로_시작한_요청은_따로_세서_카카오_가입을_막지_못한다() throws Exception {
        // 남이 "이메일로 시작"을 여러 번 해서 내 메일로 코드를 보내도 (이메일 하나에 시간당 5번까지)
        for (int i = 0; i < 5; i++) {
            requestCode(emailStart(), email("godae")).andExpect(status().isOk());
        }
        requestCode(emailStart(), email("godae"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY"));
        // 나는 카카오로 가입할 수 있다
        String kakao = startSignup("godae");
        requestCode(kakao, email("godae")).andExpect(status().isOk());
    }

    @Test
    void 공용_메일_도메인은_학교_도메인으로_넣을_수_없다() throws Exception {
        String owner = ownerToken();
        long ku = schoolId("KU");
        for (String domain : new String[] { "gmail.com", "naver.com", "ac.kr", "@Gmail.com" }) {
            post("/api/admin/schools/" + ku + "/domains", owner, "{\"domain\":\"" + domain + "\"}")
                    .andExpect(status().isBadRequest());
        }
        post("/api/admin/schools/" + ku + "/domains", owner, "{\"domain\":\"grad.korea.ac.kr\"}")
                .andExpect(status().isCreated());
    }
}
