package com.anam3.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
 * 매 테스트 전에 운영자 계정만 남기고 데이터를 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSupport.ClockConfig.class)
public abstract class ApiTestSupport {

    /** 테스트 기준 시각: 2026-03-02 18:00 KST */
    protected static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");
    protected static final String INVITE_CODE = "test-code";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(T0);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        clock.set(T0);
        jdbc.update("delete from participations");
        jdbc.update("delete from meetups");
        jdbc.update("delete from notices");
        jdbc.update("delete from app_settings");
        jdbc.update("delete from members where role <> 'ADMIN'");
    }

    // ---------- 인증 ----------

    protected String signup(String loginId, String name, int admissionYear) throws Exception {
        String body = """
                {"inviteCode":"%s","loginId":"%s","password":"password1234","name":"%s","admissionYear":%d}
                """.formatted(INVITE_CODE, loginId, name, admissionYear);
        ResultActions r = request(MockMvcRequestBuilders.post("/api/auth/signup"), null, body).andExpect(status().isCreated());
        return read(r, "$.token");
    }

    protected String login(String loginId, String password) throws Exception {
        String body = """
                {"loginId":"%s","password":"%s"}
                """.formatted(loginId, password);
        return read(request(MockMvcRequestBuilders.post("/api/auth/login"), null, body).andExpect(status().isOk()), "$.token");
    }

    protected String adminToken() throws Exception {
        return login("testadmin", "testadmin1234");
    }

    // ---------- 번개 ----------

    protected long createMeetup(String token, int min, int max, Duration meetIn, Duration deadlineIn,
            String openChatUrl) throws Exception {
        String body = meetupJson("치맥 번개", min, max, T0.plus(meetIn), T0.plus(deadlineIn), openChatUrl);
        ResultActions r = request(MockMvcRequestBuilders.post("/api/meetups"), token, body).andExpect(status().isCreated());
        return ((Number) read(r, "$.id")).longValue();
    }

    protected static String meetupJson(String title, int min, int max, Instant meetAt, Instant deadlineAt,
            String openChatUrl) {
        return """
                {"title":"%s","description":"수업 끝나고 한잔","place":"안암역 앞","category":"BEER",
                 "meetAt":"%s","deadlineAt":"%s","minPeople":%d,"maxPeople":%d,"openChatUrl":%s}
                """.formatted(title, meetAt, deadlineAt, min, max,
                openChatUrl == null ? "null" : "\"" + openChatUrl + "\"");
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
