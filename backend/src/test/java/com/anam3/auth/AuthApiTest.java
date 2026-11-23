package com.anam3.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

class AuthApiTest extends ApiTestSupport {

    private static String signupJson(String inviteCode, String loginId, String password, String name) {
        return """
                {"inviteCode":"%s","loginId":"%s","password":"%s","name":"%s","cohort":61,"department":"BIG_DATA"}
                """.formatted(inviteCode, loginId, password, name);
    }

    @Test
    void 입장코드가_틀리면_가입할_수_없다() throws Exception {
        post("/api/auth/signup", null, signupJson("wrong", "minsu", "password1234", "김민수"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
    }

    @Test
    void 입장코드는_대소문자와_공백을_무시한다() throws Exception {
        post("/api/auth/signup", null, signupJson(" TEST-CODE ", "minsu", "password1234", "김민수"))
                .andExpect(status().isCreated());
    }

    @Test
    void 가입하면_토큰과_내정보를_돌려준다() throws Exception {
        String token = signup("Minsu", "김민수", 61);

        get("/api/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("AI"))
                .andExpect(jsonPath("$.departmentLabel").value("인공지능융합학과"))
                .andExpect(jsonPath("$.loginId").value("minsu"))
                .andExpect(jsonPath("$.name").value("김민수"))
                .andExpect(jsonPath("$.cohortLabel").value("61기"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void 아이디는_대소문자_구분없이_중복될_수_없다() throws Exception {
        signup("minsu", "김민수", 61);

        post("/api/auth/signup", null, signupJson(INVITE_CODE, "MINSU", "password1234", "김민수"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    void 입력값_검증_메시지를_한국어로_준다() throws Exception {
        post("/api/auth/signup", null, signupJson(INVITE_CODE, "ab", "password1234", "김민수"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("아이디는 영문·숫자·_ 4~20자로 입력해 주세요"));

        post("/api/auth/signup", null, signupJson(INVITE_CODE, "minsu", "short", "김민수"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호가_틀리면_로그인_실패() throws Exception {
        signup("minsu", "김민수", 61);

        post("/api/auth/login", null, """
                {"loginId":"minsu","password":"wrong-password"}
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 토큰이_없거나_잘못되면_401() throws Exception {
        get("/api/me", null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        get("/api/me", "garbage.token.value").andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호_변경() throws Exception {
        String token = signup("minsu", "김민수", 61);

        put("/api/me/password", token, """
                {"currentPassword":"nope-nope","newPassword":"newpassword1"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        put("/api/me/password", token, """
                {"currentPassword":"password1234","newPassword":"newpassword1"}
                """).andExpect(status().isNoContent());

        login("minsu", "newpassword1");
    }

    @Test
    void 기수는_숫자로_받는다() throws Exception {
        post("/api/auth/signup", null, """
                {"inviteCode":"%s","loginId":"minsu","password":"password1234","name":"김민수","cohort":0,"department":"AI"}
                """.formatted(INVITE_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기수를 확인해 주세요"));
        post("/api/auth/signup", null, """
                {"inviteCode":"%s","loginId":"minsu","password":"password1234","name":"김민수","department":"AI"}
                """.formatted(INVITE_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기수를 선택해 주세요"));
        // 최신 기수(기본 62)보다 큰 기수는 아직 없다
        post("/api/auth/signup", null, """
                {"inviteCode":"%s","loginId":"minsu","password":"password1234","name":"김민수","cohort":63,"department":"AI"}
                """.formatted(INVITE_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COHORT"));
    }

    @Test
    void 학과는_필수() throws Exception {
        post("/api/auth/signup", null, """
                {"inviteCode":"%s","loginId":"minsu","password":"password1234","name":"김민수","cohort":61}
                """.formatted(INVITE_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("학과를 선택해 주세요"));
    }

    @Test
    void 가입_화면용_기수와_학과는_로그인_없이_조회된다() throws Exception {
        get("/api/auth/signup-options", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestCohort").value(62))
                .andExpect(jsonPath("$.departments.length()").value(3))
                .andExpect(jsonPath("$.departments[0].code").value("SOFTWARE_SECURITY"))
                .andExpect(jsonPath("$.departments[0].label").value("소프트웨어보안학과"));
    }

    @Test
    void 내_정보에서_이름과_기수를_바꾼다() throws Exception {
        String token = signup("minsu", "김민수", 61);

        put("/api/me/profile", token, "{\"name\":\"김민수\",\"cohort\":62,\"department\":\"BIG_DATA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohort").value(62))
                .andExpect(jsonPath("$.cohortLabel").value("62기"))
                .andExpect(jsonPath("$.departmentLabel").value("빅데이터융합학과"));
        get("/api/me", token).andExpect(jsonPath("$.cohortLabel").value("62기"));

        put("/api/me/profile", token, "{\"name\":\"김\",\"cohort\":62,\"department\":\"AI\"}")
                .andExpect(status().isBadRequest());
        put("/api/me/profile", token, "{\"name\":\"김민수\",\"cohort\":63,\"department\":\"AI\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 헬스체크는_로그인_없이_된다() throws Exception {
        get("/api/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}
