package com.anam3.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.anam3.support.ApiTestSupport;

class AuthApiTest extends ApiTestSupport {

    private static String signupJson(String inviteCode, String loginId, String password, String name) {
        return """
                {"inviteCode":"%s","loginId":"%s","password":"%s","name":"%s","admissionYear":2025}
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
        String token = signup("Minsu", "김민수", 2025);

        get("/api/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("minsu"))
                .andExpect(jsonPath("$.name").value("김민수"))
                .andExpect(jsonPath("$.admissionLabel").value("25학번"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void 아이디는_대소문자_구분없이_중복될_수_없다() throws Exception {
        signup("minsu", "김민수", 2025);

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
        signup("minsu", "김민수", 2025);

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
        String token = signup("minsu", "김민수", 2025);

        put("/api/me/password", token, """
                {"currentPassword":"nope-nope","newPassword":"newpassword1"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        put("/api/me/password", token, """
                {"currentPassword":"password1234","newPassword":"newpassword1"}
                """).andExpect(status().isNoContent());

        login("minsu", "newpassword1");
    }

    @Test
    void 헬스체크는_로그인_없이_된다() throws Exception {
        get("/api/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}
