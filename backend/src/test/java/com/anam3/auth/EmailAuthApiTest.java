package com.anam3.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.anam3.support.ApiTestSupport;

/** 학교 이메일 + 비밀번호: 가입 · 로그인 · 비밀번호 찾기 · 카카오 계정과 하나로 (학교 이메일 하나에 계정 하나) */
class EmailAuthApiTest extends ApiTestSupport {

    private static final String PASSWORD = "anam3rd2026";

    /** 이메일로 시작 → 학교 이메일 인증까지. 응답(다음 단계)을 돌려준다 */
    private ResultActions startAndVerify(String email) throws Exception {
        String start = read(post("/api/auth/email/start", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("EMAIL_REQUIRED")), "$.signupToken");
        requestCode(start, email).andExpect(status().isOk());
        return verifyCode(start, email, mailer.lastCode(email)).andExpect(status().isOk());
    }

    private String emailSignup(String key, String name, String password) throws Exception {
        String token = read(startAndVerify(email(key)).andExpect(jsonPath("$.step").value("PROFILE_REQUIRED")),
                "$.signupToken");
        return read(post("/api/auth/signup", null, signupJson(token, name, password))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.step").value("DONE")), "$.token");
    }

    private String signupJson(String token, String name, String password) {
        return """
                {"signupToken":"%s","name":"%s","cohort":61,"departmentId":%d,"agreeTerms":true%s}
                """.formatted(token, name, departmentId("KU", "인융"),
                password == null ? "" : ",\"password\":\"" + password + "\"");
    }

    private ResultActions emailLogin(String email, String password) throws Exception {
        return post("/api/auth/email/login", null, """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    private ResultActions emailLoginFrom(String ip, String email, String password) throws Exception {
        return postFrom(ip, "/api/auth/email/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    @Test
    void 남이_내_이메일로_일부러_틀려도_나는_내_자리에서_로그인된다() throws Exception {
        emailSignup("victim", "최피해", PASSWORD);
        for (int i = 0; i < AuthService.HOURLY_LOGIN_FAILURES; i++) {
            emailLoginFrom("203.0.113.7", "victim@korea.ac.kr", "wrong" + i + "abc")
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        emailLoginFrom("203.0.113.7", "victim@korea.ac.kr", PASSWORD).andExpect(status().isTooManyRequests());
        emailLoginFrom("198.51.100.20", "victim@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.step").value("DONE"));
    }

    @Test
    void 한_IP에서_몰아치는_로그인_시도는_10분_동안_막는다() throws Exception {
        for (int i = 0; i < AuthService.LOGIN_ATTEMPTS_PER_IP; i++) {
            emailLoginFrom("203.0.113.9", "nobody" + i + "@korea.ac.kr", "whatever1")
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        emailLoginFrom("203.0.113.9", "nobody@korea.ac.kr", "whatever1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY"));
        // 다른 곳의 원우는 그대로
        emailLoginFrom("198.51.100.20", "nobody@korea.ac.kr", "whatever1")
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        clock.advance(Duration.ofMinutes(10));
        emailLoginFrom("203.0.113.9", "nobody@korea.ac.kr", "whatever1")
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    private int memberCount() {
        Integer n = jdbc.queryForObject("select count(*) from members", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void 이메일로_가입하고_로그인한다() throws Exception {
        String token = read(startAndVerify(email("godae")).andExpect(jsonPath("$.step").value("PROFILE_REQUIRED")),
                "$.signupToken");
        post("/api/auth/signup", null, signupJson(token, "김고대", null))
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
        post("/api/auth/signup", null, signupJson(token, "김고대", "abcdefgh")).andExpect(status().isBadRequest());
        post("/api/auth/signup", null, signupJson(token, "김고대", "1234567")).andExpect(status().isBadRequest());
        String session = read(post("/api/auth/signup", null, signupJson(token, "김고대", PASSWORD))
                .andExpect(status().isCreated()), "$.token");

        get("/api/me", session)
                .andExpect(jsonPath("$.email").value("godae@korea.ac.kr"))
                .andExpect(jsonPath("$.kakaoLinked").value(false))
                .andExpect(jsonPath("$.hasPassword").value(true));

        emailLogin(" GoDae@Korea.ac.kr ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.name").value("김고대"));
        // 틀린 비밀번호와 없는 이메일은 같은 말
        emailLogin("godae@korea.ac.kr", "wrong12345")
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("이메일이나 비밀번호가 맞지 않아요"));
        emailLogin("nobody@korea.ac.kr", PASSWORD)
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("이메일이나 비밀번호가 맞지 않아요"));
    }

    @Test
    void 카카오로_가입한_메일로_이메일_가입하면_같은_계정에_비밀번호만_생긴다() throws Exception {
        signup("godae", "김고대", 61); // 카카오로 가입
        long id = memberId("godae");
        int before = memberCount();
        // 카카오 가입자는 비밀번호가 없어서 이메일로는 아직 못 들어온다
        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        String token = read(startAndVerify(email("godae"))
                .andExpect(jsonPath("$.step").value("PASSWORD_REQUIRED"))
                .andExpect(jsonPath("$.email").value("godae@korea.ac.kr")), "$.signupToken");
        // 이미 가입한 메일로는 새로 가입할 수 없다
        post("/api/auth/signup", null, signupJson(token, "김고대", PASSWORD))
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        post("/api/auth/password", null, """
                {"signupToken":"%s","password":"%s"}
                """.formatted(token, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.id").value(id))
                .andExpect(jsonPath("$.member.kakaoLinked").value(true))
                .andExpect(jsonPath("$.member.hasPassword").value(true));

        assertThat(memberCount()).isEqualTo(before);
        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.member.id").value(id));
        login("godae"); // 카카오 로그인도 그대로
    }

    @Test
    void 이메일로_가입한_원우가_카카오로_오면_같은_계정에_카카오가_붙는다() throws Exception {
        emailSignup("godae", "김고대", PASSWORD);
        long id = memberId("godae");
        int before = memberCount();

        String kakao = startSignup("godae-kakao");
        requestCode(kakao, email("godae")).andExpect(status().isOk());
        verifyCode(kakao, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.id").value(id))
                .andExpect(jsonPath("$.member.kakaoLinked").value(true))
                .andExpect(jsonPath("$.member.hasPassword").value(true));
        assertThat(memberCount()).isEqualTo(before);
        login("godae-kakao");
        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.member.id").value(id));
    }

    @Test
    void 비밀번호_찾기는_학교_메일_인증으로_새_비밀번호() throws Exception {
        emailSignup("godae", "김고대", PASSWORD);
        String token = read(startAndVerify(email("godae")).andExpect(jsonPath("$.step").value("PASSWORD_REQUIRED")),
                "$.signupToken");
        post("/api/auth/password", null, """
                {"signupToken":"%s","password":"short1"}
                """.formatted(token)).andExpect(status().isBadRequest());
        post("/api/auth/password", null, """
                {"signupToken":"%s","password":"newpass2027"}
                """.formatted(token)).andExpect(status().isOk());

        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        emailLogin("godae@korea.ac.kr", "newpass2027").andExpect(jsonPath("$.step").value("DONE"));

        // 인증하지 않은 가입 토큰이나, 가입한 적 없는 메일로는 비밀번호를 못 정한다
        String unverified = read(post("/api/auth/email/start", null, null), "$.signupToken");
        post("/api/auth/password", null, """
                {"signupToken":"%s","password":"newpass2027"}
                """.formatted(unverified)).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        String fresh = read(startAndVerify(email("horang")).andExpect(jsonPath("$.step").value("PROFILE_REQUIRED")),
                "$.signupToken");
        post("/api/auth/password", null, """
                {"signupToken":"%s","password":"newpass2027"}
                """.formatted(fresh)).andExpect(jsonPath("$.code").value("NO_ACCOUNT"));
    }

    @Test
    void 비밀번호를_10번_틀리면_1시간_막힌다() throws Exception {
        // 틀린 횟수는 서버 메모리에 남으니 다른 테스트와 겹치지 않는 메일로
        emailSignup("lockout", "최잠금", PASSWORD);
        for (int i = 0; i < AuthService.HOURLY_LOGIN_FAILURES; i++) {
            emailLogin("lockout@korea.ac.kr", "wrong" + i + "abc").andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        emailLogin("lockout@korea.ac.kr", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY"));
        clock.advance(Duration.ofMinutes(61));
        emailLogin("lockout@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.step").value("DONE"));
    }

    private static String change(String current, String next) {
        return """
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(current, next);
    }

    @Test
    void 지금_비밀번호로_바꾸면_다른_기기는_로그아웃되고_이_기기는_이어서_쓴다() throws Exception {
        String phone = emailSignup("godae", "김고대", PASSWORD);
        clock.advance(Duration.ofMinutes(1));
        String web = read(emailLogin("godae@korea.ac.kr", PASSWORD), "$.token");

        String fresh = read(put("/api/me/password", web, change(PASSWORD, "newpass2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.hasPassword").value(true)), "$.token");
        get("/api/me", fresh).andExpect(status().isOk());
        get("/api/me", web).andExpect(status().isUnauthorized());
        get("/api/me", phone).andExpect(status().isUnauthorized());
        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        emailLogin("godae@korea.ac.kr", "newpass2027").andExpect(jsonPath("$.step").value("DONE"));
    }

    @Test
    void 지금_비밀번호를_10번_틀리면_1시간_막힌다() throws Exception {
        String token = emailSignup("godae", "김고대", PASSWORD);
        put("/api/me/password", token, change("wrong1234", "newpass2027"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        // 같은 비밀번호, 규칙에 안 맞는 새 비밀번호는 틀린 횟수에 넣지 않는다
        put("/api/me/password", token, change(PASSWORD, PASSWORD))
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
        put("/api/me/password", token, change(PASSWORD, "short1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AuthDtos.PASSWORD_MESSAGE));
        for (int i = 1; i < AuthService.HOURLY_PASSWORD_CHANGE_FAILURES; i++) {
            put("/api/me/password", token, change("wrong" + i + "abc", "newpass2027"))
                    .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        }
        put("/api/me/password", token, change(PASSWORD, "newpass2027"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY"));
        clock.advance(Duration.ofMinutes(61));
        put("/api/me/password", token, change(PASSWORD, "newpass2027")).andExpect(status().isOk());
    }

    @Test
    void 비밀번호가_없으면_지금_비밀번호로_바꿀_수_없다() throws Exception {
        String token = signup("godae", "김고대", 61); // 카카오로 가입
        put("/api/me/password", token, change("anam3rd2026", "newpass2027"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_PASSWORD"));
    }

    @Test
    void 탈퇴하면_이메일로도_못_들어오고_다시_가입할_수_있다() throws Exception {
        String session = emailSignup("godae", "김고대", PASSWORD);
        delete("/api/me", session).andExpect(status().isNoContent());
        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        clock.advance(Duration.ofMinutes(2)); // 인증코드는 1분에 한 번
        String again = emailSignup("godae", "김고대", "again2026x");
        get("/api/me", again).andExpect(jsonPath("$.name").value("김고대"));
    }

    @Test
    void 차단된_원우는_비밀번호를_정하거나_로그인할_수_없다() throws Exception {
        emailSignup("godae", "김고대", PASSWORD);
        post("/api/admin/members/" + memberId("godae") + "/ban", ownerToken(), null).andExpect(status().isOk());

        emailLogin("godae@korea.ac.kr", PASSWORD).andExpect(jsonPath("$.code").value("BANNED"));
        String start = read(post("/api/auth/email/start", null, null), "$.signupToken");
        requestCode(start, email("godae")).andExpect(status().isOk());
        verifyCode(start, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANNED"));
    }

    @Test
    void 예전_계정_잇기는_카카오로만() throws Exception {
        String token = read(startAndVerify(email("chamsari")).andExpect(jsonPath("$.step").value("PROFILE_REQUIRED")),
                "$.signupToken");
        post("/api/auth/legacy", null, """
                {"signupToken":"%s","loginId":"chamsari","password":"anam1234"}
                """.formatted(token)).andExpect(jsonPath("$.code").value("LEGACY_NEEDS_KAKAO"));
    }
}
