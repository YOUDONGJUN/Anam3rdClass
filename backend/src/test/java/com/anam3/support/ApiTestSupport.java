package com.anam3.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

/**
 * API 통합 테스트 공통 도우미. H2 인메모리 DB + 실제 스프링 컨텍스트로 HTTP 요청을 흉내 낸다.
 * 카카오는 FakeKakaoClient, 메일은 CapturingMailer 로 바꿔 끼운다.
 * 매 테스트 전에 대학원장만 남기고 데이터를 지운다. 학교·학과·도메인은 마이그레이션이 넣은 상태(V8)로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class ApiTestSupport {

    /** 테스트 기준 시각: 2026-03-02 18:00 KST */
    public static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");
    protected static final String OWNER_EMAIL = "owner@korea.ac.kr";
    protected static final String REDIRECT_URI = "http://localhost:5173/auth/kakao/callback";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected CapturingMailer mailer;

    @Autowired
    protected CapturingPushSender pushes;

    @Autowired
    protected JdbcTemplate jdbc;

    /** 마이그레이션이 넣은 학교·도메인·학과 (처음 한 번 기억해 두고, 테스트가 더한 것만 지운다) */
    private static Set<Long> seededSchools;
    private static Set<Long> seededDomains;
    private static Set<Long> seededDepartments;

    @BeforeEach
    void resetState() {
        clock.set(T0);
        mailer.clear();
        pushes.clear();
        if (seededSchools == null) {
            seededSchools = ids("select id from schools");
            seededDomains = ids("select id from school_email_domains");
            seededDepartments = ids("select id from departments");
        }
        jdbc.update("delete from push_devices");
        jdbc.update("update members set push_off = null");
        jdbc.update("delete from reports");
        jdbc.update("delete from member_blocks");
        jdbc.update("delete from email_verifications");
        jdbc.update("delete from participations");
        jdbc.update("delete from meetups");
        jdbc.update("delete from notices");
        jdbc.update("delete from app_settings");
        jdbc.update("delete from members where role <> 'OWNER'");
        jdbc.update("delete from departments where id not in (" + joined(seededDepartments) + ")");
        jdbc.update("delete from school_email_domains where id not in (" + joined(seededDomains) + ")");
        jdbc.update("delete from schools where id not in (" + joined(seededSchools) + ")");
        jdbc.update("update departments set active = true");
        jdbc.update("update schools set latest_cohort = case when code = 'KU' then 62 else 99 end");
    }

    private Set<Long> ids(String sql) {
        return new HashSet<>(jdbc.queryForList(sql, Long.class));
    }

    private static String joined(Set<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    // ---------- 학교 · 학과 ----------

    protected long schoolId(String code) {
        return jdbc.queryForObject("select id from schools where code = ?", Long.class, code);
    }

    /** 학교 코드와 짧은 학과 이름으로 학과 번호 (예: KU, 인융) */
    protected long departmentId(String schoolCode, String shortName) {
        return jdbc.queryForObject("""
                select d.id from departments d join schools s on s.id = d.school_id
                where s.code = ? and d.short_name = ?
                """, Long.class, schoolCode, shortName);
    }

    // ---------- 로그인 · 가입 ----------

    /** 고려대 이메일. 다른 학교는 key 에 @ 를 넣는다 (예: sinchon@yonsei.ac.kr) */
    protected static String email(String key) {
        return key.contains("@") ? key : key + "@korea.ac.kr";
    }

    /** 카카오 로그인 (key 마다 다른 카카오 계정) */
    protected ResultActions kakaoLogin(String key) throws Exception {
        return post("/api/auth/kakao", null, """
                {"code":"%s","redirectUri":"%s"}
                """.formatted(FakeKakaoClient.code(key), REDIRECT_URI));
    }

    protected ResultActions requestCode(String signupToken, String email) throws Exception {
        return post("/api/auth/email/code", null, """
                {"signupToken":"%s","email":"%s"}
                """.formatted(signupToken, email));
    }

    protected ResultActions verifyCode(String signupToken, String email, String code) throws Exception {
        return post("/api/auth/email/verify", null, """
                {"signupToken":"%s","email":"%s","code":"%s"}
                """.formatted(signupToken, email, code));
    }

    protected static String profileJson(String signupToken, String name, int cohort, long departmentId) {
        return """
                {"signupToken":"%s","name":"%s","cohort":%d,"departmentId":%d,"agreeTerms":true}
                """.formatted(signupToken, name, cohort, departmentId);
    }

    /** 카카오 로그인 → 처음이면 가입 토큰 */
    protected String startSignup(String key) throws Exception {
        return read(kakaoLogin(key).andExpect(status().isOk()).andExpect(jsonPath("$.step").value("EMAIL_REQUIRED")),
                "$.signupToken");
    }

    /** 가입 토큰 → 학교 이메일 인증 → 프로필 입력용 가입 토큰 */
    protected String verifyEmail(String signupToken, String email) throws Exception {
        requestCode(signupToken, email).andExpect(status().isOk());
        return read(verifyCode(signupToken, email, mailer.lastCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PROFILE_REQUIRED")), "$.signupToken");
    }

    /**
     * 카카오 로그인 → 학교 이메일({key}@korea.ac.kr) 인증 → 이름·기수·학과 입력까지 마치고 로그인 토큰.
     * 학과는 그 학교의 학과 하나 (고려대는 인융)
     */
    protected String signup(String key, String name, int cohort) throws Exception {
        String mail = email(key);
        long department = mail.endsWith("@yonsei.ac.kr") ? departmentId("YONSEI", "인컴")
                : mail.endsWith("skku.edu") ? departmentId("SKKU", "정보")
                : mail.endsWith("@sogang.ac.kr") ? departmentId("SOGANG", "데사")
                : mail.endsWith("@hanyang.ac.kr") ? departmentId("HANYANG", "인시")
                : departmentId("KU", "인융");
        return signup(key, name, cohort, department);
    }

    /** key 에 @ 가 있으면 그 이메일로 (다른 학교 원우) */
    protected String signup(String key, String name, int cohort, long departmentId) throws Exception {
        String profileToken = verifyEmail(startSignup(key), email(key));
        return read(post("/api/auth/signup", null, profileJson(profileToken, name, cohort, departmentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.step").value("DONE")), "$.token");
    }

    /** 이미 가입한 카카오 계정으로 로그인 */
    protected String login(String key) throws Exception {
        return read(kakaoLogin(key).andExpect(status().isOk()).andExpect(jsonPath("$.step").value("DONE")),
                "$.token");
    }

    /** 대학원장 (owner@korea.ac.kr). 테스트 사이에 지우지 않으니 처음 한 번만 가입한다 */
    protected String ownerToken() throws Exception {
        Integer count = jdbc.queryForObject("select count(*) from members where email = ?", Integer.class,
                OWNER_EMAIL);
        return count != null && count > 0 ? login("owner") : signup("owner", "박관리", 61);
    }

    /** 가입시킨 뒤 대학원장이 교수로 임명 */
    protected String managerToken(String key, String name, int cohort) throws Exception {
        String token = signup(key, name, cohort);
        put("/api/admin/members/" + memberId(key) + "/role", ownerToken(), "{\"role\":\"MANAGER\"}")
                .andExpect(status().isOk());
        return token;
    }

    protected long memberId(String key) {
        Long id = jdbc.queryForObject("select id from members where email = ?", Long.class, email(key));
        if (id == null) {
            throw new IllegalStateException(key + " 회원이 없어요");
        }
        return id;
    }

    /** 앱을 연 것처럼 이 기기 토큰을 알린다 */
    protected void registerDevice(String token, String deviceToken) throws Exception {
        post("/api/me/push/devices", token, """
                {"token":"%s","platform":"android"}
                """.formatted(deviceToken)).andExpect(status().isNoContent());
    }

    // ---------- 3교시 ----------

    /** min/max 가 null 이면 각각 "최소 인원 없음" / "인원 제한 없음". 우리 학교 3교시 */
    protected long createMeetup(String token, Integer min, Integer max, Duration meetIn, Duration deadlineIn,
            String openChatUrl) throws Exception {
        return createMeetup(token, min, max, meetIn, deadlineIn, openChatUrl, "SCHOOL");
    }

    /** audience: SCHOOL(우리 학교) · COHORT(우리 기수) · ALL(연합) */
    protected long createMeetup(String token, Integer min, Integer max, Duration meetIn, Duration deadlineIn,
            String openChatUrl, String audience) throws Exception {
        String body = meetupJson("치맥 번개", min, max, T0.plus(meetIn), T0.plus(deadlineIn), openChatUrl, audience);
        ResultActions r = request(MockMvcRequestBuilders.post("/api/meetups"), token, body).andExpect(status().isCreated());
        return ((Number) read(r, "$.id")).longValue();
    }

    protected static String meetupJson(String title, Integer min, Integer max, Instant meetAt, Instant deadlineAt,
            String openChatUrl) {
        return meetupJson(title, min, max, meetAt, deadlineAt, openChatUrl, "SCHOOL");
    }

    protected static String meetupJson(String title, Integer min, Integer max, Instant meetAt, Instant deadlineAt,
            String openChatUrl, String audience) {
        return """
                {"title":"%s","description":"수업 끝나고 한잔","place":"안암역 앞","categories":["BEER"],
                 "purposes":["친목"],"meetAt":"%s","deadlineAt":"%s","minPeople":%s,"maxPeople":%s,"openChatUrl":%s,
                 "audience":"%s"}
                """.formatted(title, meetAt, deadlineAt, min, max,
                openChatUrl == null ? "null" : "\"" + openChatUrl + "\"", audience);
    }

    /** 3교시 상세의 참여자 참여 번호(ref). 들어온 순서 (0 = 연 사람) */
    protected long participantRef(String token, long meetupId, int index) throws Exception {
        return ((Number) read(get("/api/meetups/" + meetupId, token).andExpect(status().isOk()),
                "$.participants[" + index + "].ref")).longValue();
    }

    // ---------- HTTP 도우미 ----------

    protected ResultActions get(String url, String token) throws Exception {
        return request(MockMvcRequestBuilders.get(url), token, null);
    }

    protected ResultActions post(String url, String token, String body) throws Exception {
        return request(MockMvcRequestBuilders.post(url), token, body);
    }

    protected ResultActions put(String url, String token, String body) throws Exception {
        return request(MockMvcRequestBuilders.put(url), token, body);
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return request(MockMvcRequestBuilders.delete(url), token, null);
    }

    private ResultActions request(MockHttpServletRequestBuilder builder, String token, String body)
            throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }

    protected static <T> T read(ResultActions result, String jsonPath) throws Exception {
        String content = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(content, jsonPath);
    }
}
