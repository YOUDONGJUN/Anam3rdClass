package com.anam3.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.anam3.support.ApiTestSupport;

class AuthApiTest extends ApiTestSupport {

    @Test
    void 처음_온_카카오_계정은_학교_이메일_인증부터() throws Exception {
        kakaoLogin("godae")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("EMAIL_REQUIRED"))
                .andExpect(jsonPath("$.signupToken").isNotEmpty())
                .andExpect(jsonPath("$.token").isEmpty());
    }

    @Test
    void 카카오_인가_코드가_잘못되면_실패() throws Exception {
        post("/api/auth/kakao", null, """
                {"code":"nope","redirectUri":"%s"}
                """.formatted(REDIRECT_URI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KAKAO_LOGIN_FAILED"));
        post("/api/auth/kakao", null, """
                {"code":"code-godae","redirectUri":"https://evil.example.com/steal"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void 가입하면_내_정보에_학교_이메일과_학교가_들어간다() throws Exception {
        String token = signup("godae", "김고대", 61);

        get("/api/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("godae@korea.ac.kr"))
                .andExpect(jsonPath("$.school.name").value("고려대학교"))
                .andExpect(jsonPath("$.school.shortName").value("고려대"))
                .andExpect(jsonPath("$.school.code").value("KU"))
                .andExpect(jsonPath("$.graduateSchool").value("SW·AI융합대학원"))
                .andExpect(jsonPath("$.name").value("김고대"))
                .andExpect(jsonPath("$.cohortLabel").value("61기"))
                .andExpect(jsonPath("$.departmentId").value(departmentId("KU", "인융")))
                .andExpect(jsonPath("$.departmentLabel").value("인공지능융합학과"))
                .andExpect(jsonPath("$.departmentShortLabel").value("인융"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.termsAgreed").value(true));
    }

    @Test
    void 다른_학교_이메일로_가입하면_그_학교_원우가_된다() throws Exception {
        String token = signup("sinchon@yonsei.ac.kr", "이신촌", 3, departmentId("YONSEI", "인컴"));
        get("/api/me", token)
                .andExpect(jsonPath("$.school.code").value("YONSEI"))
                .andExpect(jsonPath("$.school.shortName").value("연세대"))
                .andExpect(jsonPath("$.graduateSchool").value("인공지능융합대학원"))
                .andExpect(jsonPath("$.departmentShortLabel").value("인컴"));

        // 학과에 대학원이 따로 적혀 있으면 그 대학원 (연세대 공학대학원 인공지능전공)
        String engineering = signup("baekyang@yonsei.ac.kr", "박백양", 12, departmentId("YONSEI", "인공"));
        get("/api/me", engineering)
                .andExpect(jsonPath("$.graduateSchool").value("공학대학원"))
                .andExpect(jsonPath("$.departmentLabel").value("인공지능전공"));

        // 하위 도메인도 같은 학교 (성균관대 학생 메일 g.skku.edu, 예전 skku.edu 둘 다)
        String signupToken = startSignup("myeongnyun");
        requestCode(signupToken, "myeongnyun@g.skku.edu")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("성균관대학교"));
        String other = startSignup("other");
        requestCode(other, "staff@mail.skku.edu")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("성균관대학교"));
        // 도메인이 끝만 같은 다른 곳은 안 된다
        requestCode(startSignup("fake"), "x@fakeskku.edu").andExpect(status().isBadRequest());
    }

    @Test
    void 다른_학교_학과는_고를_수_없다() throws Exception {
        String profileToken = verifyEmail(startSignup("godae"), email("godae"));
        post("/api/auth/signup", null, profileJson(profileToken, "김고대", 61, departmentId("YONSEI", "인컴")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEPARTMENT"));
        // 학교마다 최신 기수가 다르다: 연세대는 99기까지 되지만 고려대는 62기까지
        post("/api/auth/signup", null, profileJson(profileToken, "김고대", 70, departmentId("KU", "인융")))
                .andExpect(jsonPath("$.code").value("INVALID_COHORT"));
    }

    @Test
    void 약관에_동의해야_가입된다() throws Exception {
        String profileToken = verifyEmail(startSignup("godae"), email("godae"));
        post("/api/auth/signup", null, """
                {"signupToken":"%s","name":"김고대","cohort":61,"departmentId":%d,"agreeTerms":false}
                """.formatted(profileToken, departmentId("KU", "인융")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이용약관에 동의해 주세요"));
    }

    @Test
    void 가입한_뒤에는_카카오_로그인만으로_들어온다() throws Exception {
        signup("godae", "김고대", 61);

        String token = login("godae");
        get("/api/me", token).andExpect(jsonPath("$.name").value("김고대"));
    }

    @Test
    void 학교_이메일만_인증코드를_받는다() throws Exception {
        String signupToken = startSignup("godae");

        requestCode(signupToken, "godae@gmail.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_REQUIRED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "IT연합회 학교 이메일만 쓸 수 있어요 (@korea.ac.kr, @yonsei.ac.kr")));
        requestCode(signupToken, "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        requestCode(signupToken, " GoDae@Korea.ac.kr ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("godae@korea.ac.kr"))
                .andExpect(jsonPath("$.schoolName").value("고려대학교"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
        assertThat(mailer.lastCode("godae@korea.ac.kr")).matches("\\d{6}");
    }

    @Test
    void 인증코드는_1분에_한_번만_받는다() throws Exception {
        String signupToken = startSignup("godae");
        requestCode(signupToken, email("godae")).andExpect(status().isOk());

        requestCode(signupToken, email("godae"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_SOON"));

        clock.advance(Duration.ofSeconds(61));
        requestCode(signupToken, email("godae")).andExpect(status().isOk());
    }

    @Test
    void 남이_내_이메일로_코드를_요청해도_내_인증은_막히지_않는다() throws Exception {
        String attacker = startSignup("attacker");
        for (int i = 0; i < 5; i++) {
            requestCode(attacker, email("godae")).andExpect(status().isOk());
            clock.advance(Duration.ofSeconds(61));
        }
        requestCode(attacker, email("godae")).andExpect(status().isTooManyRequests());

        String godae = startSignup("godae");
        requestCode(godae, email("godae")).andExpect(status().isOk());
        verifyCode(godae, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(jsonPath("$.step").value("PROFILE_REQUIRED"));
    }

    @Test
    void 인증코드는_한_시간에_다섯_번까지() throws Exception {
        String signupToken = startSignup("godae");
        for (int i = 0; i < 5; i++) {
            requestCode(signupToken, email("godae")).andExpect(status().isOk());
            clock.advance(Duration.ofSeconds(61));
        }
        requestCode(signupToken, email("godae"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY"));
    }

    @Test
    void 인증코드가_틀리면_다섯_번까지만_시도할_수_있다() throws Exception {
        String signupToken = startSignup("godae");
        requestCode(signupToken, email("godae")).andExpect(status().isOk());
        String right = mailer.lastCode(email("godae"));
        String wrong = right.equals("000000") ? "111111" : "000000";

        verifyCode(signupToken, email("godae"), wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CODE"))
                .andExpect(jsonPath("$.message").value("인증코드가 맞지 않아요. 4번 더 시도할 수 있어요"));
        for (int i = 0; i < 4; i++) {
            verifyCode(signupToken, email("godae"), wrong).andExpect(jsonPath("$.code").value("WRONG_CODE"));
        }
        // 다섯 번 틀린 코드는 버려진다 — 맞는 코드를 넣어도 새로 받아야 한다
        verifyCode(signupToken, email("godae"), right)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void 인증코드는_10분_뒤_만료된다() throws Exception {
        String signupToken = startSignup("godae");
        requestCode(signupToken, email("godae")).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(11));
        verifyCode(signupToken, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void 다른_카카오_계정이_받은_코드는_쓸_수_없다() throws Exception {
        String godae = startSignup("godae");
        requestCode(godae, email("godae")).andExpect(status().isOk());
        String code = mailer.lastCode(email("godae"));

        verifyCode(startSignup("someone"), email("godae"), code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void 이메일_인증_없이는_가입할_수_없다() throws Exception {
        String signupToken = startSignup("godae");

        post("/api/auth/signup", null, profileJson(signupToken, "김고대", 61, departmentId("KU", "인융")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        requestCode("garbage", email("godae"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_EXPIRED"));
    }

    @Test
    void 가입_토큰은_30분_뒤_만료된다() throws Exception {
        String profileToken = verifyEmail(startSignup("godae"), email("godae"));

        clock.advance(Duration.ofMinutes(31));
        post("/api/auth/signup", null, profileJson(profileToken, "김고대", 61, departmentId("KU", "인융")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_EXPIRED"));
    }

    @Test
    void 가입_토큰으로는_로그인할_수_없다() throws Exception {
        String signupToken = startSignup("godae");
        String profileToken = verifyEmail(signupToken, email("godae"));

        get("/api/me", signupToken).andExpect(status().isUnauthorized());
        get("/api/me", profileToken).andExpect(status().isUnauthorized());
    }

    @Test
    void 카카오_계정이_바뀌어도_학교_이메일을_인증하면_원래_계정으로_돌아온다() throws Exception {
        signup("godae", "김고대", 61);
        long id = memberId("godae");

        String newKakao = startSignup("godae-new-phone");
        requestCode(newKakao, email("godae")).andExpect(status().isOk());
        String token = read(verifyCode(newKakao, email("godae"), mailer.lastCode(email("godae")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.id").value(id))
                .andExpect(jsonPath("$.member.name").value("김고대")), "$.token");
        get("/api/me", token).andExpect(jsonPath("$.id").value(id));

        // 이제 새 카카오 계정으로 바로 들어오고, 옛 카카오 계정은 처음 온 사람이 된다
        login("godae-new-phone");
        kakaoLogin("godae").andExpect(jsonPath("$.step").value("EMAIL_REQUIRED"));
    }

    @Test
    void 예전_회원은_관리자가_넣어둔_학교_이메일로_인증하면_그대로_이어진다() throws Exception {
        jdbc.update("""
                insert into members (login_id, password_hash, name, cohort, school_id, department_id, role, status,
                                     created_at)
                values ('legacy', 'x', '옛회원', 60, ?, ?, 'USER', 'ACTIVE', current_timestamp)
                """, schoolId("KU"), departmentId("KU", "빅융"));
        Long legacyId = jdbc.queryForObject("select id from members where login_id = 'legacy'", Long.class);
        put("/api/admin/members/" + legacyId + "/email", ownerToken(), "{\"email\":\"legacy@korea.ac.kr\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoLinked").value(false));

        String signupToken = startSignup("legacy");
        requestCode(signupToken, "legacy@korea.ac.kr").andExpect(status().isOk());
        verifyCode(signupToken, "legacy@korea.ac.kr", mailer.lastCode("legacy@korea.ac.kr"))
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.id").value(legacyId))
                .andExpect(jsonPath("$.member.name").value("옛회원"))
                .andExpect(jsonPath("$.member.cohort").value(60))
                .andExpect(jsonPath("$.member.departmentShortLabel").value("빅융"))
                .andExpect(jsonPath("$.member.school.name").value("고려대학교"))
                // 예전 회원은 약관에 아직 동의하지 않았다 → 화면이 동의부터 받는다
                .andExpect(jsonPath("$.member.termsAgreed").value(false));

        String token = login("legacy");
        post("/api/me/terms", token, null).andExpect(status().isOk()).andExpect(jsonPath("$.termsAgreed").value(true));
        get("/api/me", token).andExpect(jsonPath("$.termsAgreed").value(true));
    }

    @Test
    void 대학원장_이메일로_가입하면_대학원장() throws Exception {
        get("/api/me", ownerToken())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void 가입한_뒤에는_이름을_스스로_바꿀_수_없다() throws Exception {
        String token = signup("godae", "김고대", 61);

        put("/api/me/profile", token, "{\"name\":\"김바꿈\",\"cohort\":61,\"departmentId\":"
                + departmentId("KU", "인융") + "}")
                .andExpect(status().isForbidden());
        get("/api/me", token).andExpect(jsonPath("$.name").value("김고대"));
    }

    @Test
    void 프로필_입력값_검증() throws Exception {
        String profileToken = verifyEmail(startSignup("godae"), email("godae"));

        long ai = departmentId("KU", "인융");
        post("/api/auth/signup", null, profileJson(profileToken, "김", 61, ai))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이름은 한글 또는 영문 2~20자로 입력해 주세요"));
        // 최신 기수(기본 62)보다 큰 기수는 아직 없다
        post("/api/auth/signup", null, profileJson(profileToken, "김고대", 63, ai))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COHORT"));
        post("/api/auth/signup", null, """
                {"signupToken":"%s","name":"김고대","cohort":61,"agreeTerms":true}
                """.formatted(profileToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("학과를 선택해 주세요"));

        post("/api/auth/signup", null, profileJson(profileToken, "김고대", 61, ai)).andExpect(status().isCreated());
        post("/api/auth/signup", null, profileJson(profileToken, "김고대", 61, ai))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SIGNED_UP"));
    }

    @Test
    void 가입_화면용_정보는_로그인_없이_조회된다() throws Exception {
        get("/api/auth/signup-options", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schools.length()").value(5))
                .andExpect(jsonPath("$.schools[*].shortName").value(
                        org.hamcrest.Matchers.contains("고려대", "연세대", "성균관대", "서강대", "한양대")))
                .andExpect(jsonPath("$.schools[0].code").value("KU"))
                .andExpect(jsonPath("$.schools[0].latestCohort").value(62))
                .andExpect(jsonPath("$.schools[0].emailDomains[0].domain").value("korea.ac.kr"))
                .andExpect(jsonPath("$.schools[0].departments.length()").value(3))
                .andExpect(jsonPath("$.schools[0].departments[0].name").value("소프트웨어보안학과"))
                .andExpect(jsonPath("$.schools[0].departments[0].shortName").value("소보"))
                .andExpect(jsonPath("$.schools[2].emailDomains[*].domain").value(
                        org.hamcrest.Matchers.contains("g.skku.edu", "skku.edu")));

        get("/api/auth/config", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoClientId").value("test-kakao-key"))
                .andExpect(jsonPath("$.devLogin").value(false))
                .andExpect(jsonPath("$.supportEmail").value("help@example.com"));
    }

    @Test
    void 개발용_로그인은_로컬에서만_있다() throws Exception {
        post("/api/auth/dev/login", null, "{\"memberId\":1}").andExpect(status().is4xxClientError());
    }

    @Test
    void 토큰이_없거나_잘못되면_401() throws Exception {
        get("/api/me", null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        get("/api/me", "garbage.token.value").andExpect(status().isUnauthorized());
    }

    // ---------- 예전(아이디·비밀번호) 회원: 예전 아이디·비밀번호로 잇기 ----------

    private long insertLegacy(String loginId, String password) {
        jdbc.update("""
                insert into members (login_id, password_hash, name, cohort, school_id, department_id, role, status,
                                     created_at)
                values (?, ?, '이참살', 60, ?, ?, 'USER', 'ACTIVE', current_timestamp)
                """, loginId, new BCryptPasswordEncoder().encode(password), schoolId("KU"), departmentId("KU", "소보"));
        return jdbc.queryForObject("select id from members where login_id = ?", Long.class, loginId);
    }

    private static String legacyJson(String signupToken, String loginId, String password) {
        return """
                {"signupToken":"%s","loginId":"%s","password":"%s"}
                """.formatted(signupToken, loginId, password);
    }

    @Test
    void 예전_회원은_예전_아이디_비밀번호로_예전_계정을_잇는다() throws Exception {
        long legacyId = insertLegacy("chamsari", "anam1234");
        get("/api/auth/signup-options", null).andExpect(jsonPath("$.legacySchoolIds[0]").value(schoolId("KU")));

        // 예전 계정은 고려대 — 다른 학교 이메일로 인증한 사람은 이을 수 없다
        String yonsei = verifyEmail(startSignup("sinchon"), "sinchon@yonsei.ac.kr");
        post("/api/auth/legacy", null, legacyJson(yonsei, "chamsari", "anam1234"))
                .andExpect(jsonPath("$.code").value("LEGACY_LOGIN_FAILED"));

        String profileToken = verifyEmail(startSignup("chamsari"), "chamsari@korea.ac.kr");
        post("/api/auth/legacy", null, legacyJson(profileToken, "chamsari", "wrong-pass"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGACY_LOGIN_FAILED"));
        post("/api/auth/legacy", null, legacyJson(profileToken, " ChamSari ", "anam1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.id").value(legacyId))
                .andExpect(jsonPath("$.member.email").value("chamsari@korea.ac.kr"))
                .andExpect(jsonPath("$.member.departmentShortLabel").value("소보"))
                .andExpect(jsonPath("$.member.termsAgreed").value(false));

        // 이은 뒤에는 예전 아이디·비밀번호를 지우고, 다음부터는 카카오 로그인만으로 들어온다
        assertThat(jdbc.queryForObject("select count(*) from members where login_id = 'chamsari'", Integer.class))
                .isZero();
        get("/api/auth/signup-options", null).andExpect(jsonPath("$.legacySchoolIds").isEmpty());
        String token = login("chamsari");

        // 약관에 동의하기 전에는 보기만 된다 (서버에서도 막는다)
        get("/api/meetups", token).andExpect(status().isOk());
        post("/api/meetups", token, meetupJson("치맥 번개", 2, 4, T0.plus(Duration.ofHours(3)),
                T0.plus(Duration.ofHours(2)), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        post("/api/me/terms", token, null).andExpect(status().isOk());
        post("/api/meetups", token, meetupJson("치맥 번개", 2, 4, T0.plus(Duration.ofHours(3)),
                T0.plus(Duration.ofHours(2)), null))
                .andExpect(status().isCreated());
    }

    @Test
    void 예전_계정_잇기는_이메일_인증을_마친_뒤에만_한_시간에_열_번까지_틀릴_수_있다() throws Exception {
        insertLegacy("chamsari", "anam1234");
        String signupToken = startSignup("someone");
        post("/api/auth/legacy", null, legacyJson(signupToken, "chamsari", "anam1234"))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String profileToken = verifyEmail(signupToken, "someone@korea.ac.kr");
        for (int i = 0; i < 10; i++) {
            post("/api/auth/legacy", null, legacyJson(profileToken, "chamsari", "guess" + i))
                    .andExpect(jsonPath("$.code").value("LEGACY_LOGIN_FAILED"));
        }
        post("/api/auth/legacy", null, legacyJson(profileToken, "chamsari", "anam1234"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 예전_계정의_관리_직책은_잇지_않는다() throws Exception {
        long legacyId = insertLegacy("chamsari", "anam1234");
        jdbc.update("update members set role = 'MANAGER' where id = ?", legacyId);
        String profileToken = verifyEmail(startSignup("chamsari"), "chamsari@korea.ac.kr");
        post("/api/auth/legacy", null, legacyJson(profileToken, "chamsari", "anam1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.id").value(legacyId))
                .andExpect(jsonPath("$.member.role").value("USER"));
    }

    @Test
    void 예전_아이디_하나는_여러_카카오_계정으로_나눠도_하루에_스무_번까지만_틀린다() throws Exception {
        insertLegacy("chamsari", "anam1234");
        for (int k = 0; k < 2; k++) {
            String token = verifyEmail(startSignup("guesser" + k), "guesser" + k + "@korea.ac.kr");
            for (int i = 0; i < 10; i++) {
                post("/api/auth/legacy", null, legacyJson(token, "chamsari", "guess" + k + i))
                        .andExpect(jsonPath("$.code").value("LEGACY_LOGIN_FAILED"));
            }
        }
        String third = verifyEmail(startSignup("guesser2"), "guesser2@korea.ac.kr");
        post("/api/auth/legacy", null, legacyJson(third, "chamsari", "anam1234"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 이메일에_더하기_별칭을_붙이면_받지_않는다() throws Exception {
        requestCode(startSignup("godae"), "godae+2@korea.ac.kr")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_ALIAS"));
    }

    @Test
    void 한_이메일로는_하루에_스무_번까지만_틀릴_수_있다() throws Exception {
        String target = "target@korea.ac.kr";
        for (int k = 0; k < 4; k++) {
            String token = startSignup("guesser" + k);
            requestCode(token, target).andExpect(status().isOk());
            String wrong = mailer.lastCode(target).equals("000000") ? "111111" : "000000";
            for (int i = 0; i < 5; i++) {
                verifyCode(token, target, wrong).andExpect(jsonPath("$.code").value("WRONG_CODE"));
            }
        }
        // 다른 카카오 계정으로 새 코드를 받아도, 맞는 코드를 넣어도 오늘은 막힌다
        String token = startSignup("target");
        requestCode(token, target).andExpect(status().isOk());
        verifyCode(token, target, mailer.lastCode(target))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void 헬스체크는_로그인_없이_된다() throws Exception {
        get("/api/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}
