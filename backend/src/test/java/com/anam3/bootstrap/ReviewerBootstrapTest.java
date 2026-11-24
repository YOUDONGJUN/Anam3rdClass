package com.anam3.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.anam3.common.AppProperties;
import com.anam3.member.MemberRepository;
import com.anam3.school.DepartmentRepository;
import com.anam3.school.SchoolRepository;
import com.anam3.support.ApiTestSupport;

/** 스토어 심사용 계정: REVIEW_EMAIL + REVIEW_PASSWORD 가 있으면 서버가 시작할 때 만든다 */
class ReviewerBootstrapTest extends ApiTestSupport {

    private static final String REVIEW_EMAIL = "review@anam3.app";

    @Autowired
    AppProperties props;

    @Autowired
    MemberRepository members;

    @Autowired
    SchoolRepository schools;

    @Autowired
    DepartmentRepository departments;

    @Autowired
    PlatformTransactionManager transactionManager;

    private void startWith(String email, String password) {
        AppProperties withReview = new AppProperties(props.jwt(), props.owner(), props.kakao(), props.mail(),
                props.cors(), props.support(), props.push(), new AppProperties.Review(email, password),
                props.android());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                new ReviewerBootstrap(withReview, members, schools, departments, clock).run(null));
    }

    private ResultActions reviewerLogin(String password) throws Exception {
        return post("/api/auth/email/login", null, """
                {"email":"%s","password":"%s"}
                """.formatted(REVIEW_EMAIL, password));
    }

    private int tokenVersion() {
        Integer v = jdbc.queryForObject("select token_version from members where email = ?", Integer.class,
                REVIEW_EMAIL);
        return v == null ? -1 : v;
    }

    @Test
    void 심사용_계정을_만들고_이메일과_비밀번호로_로그인한다() throws Exception {
        startWith(REVIEW_EMAIL, "review2026");
        String token = read(reviewerLogin("review2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"))
                .andExpect(jsonPath("$.member.name").value("심사계정"))
                .andExpect(jsonPath("$.member.school.code").value("KU"))
                .andExpect(jsonPath("$.member.termsAgreed").value(true)), "$.token");
        get("/api/meetups", token).andExpect(status().isOk());

        // 같은 비밀번호로 다시 시작하면 그대로 (심사관 로그인이 풀리지 않는다)
        int version = tokenVersion();
        startWith(REVIEW_EMAIL, "review2026");
        assertThat(tokenVersion()).isEqualTo(version);
        get("/api/me", token).andExpect(status().isOk());

        // 비밀번호를 바꾸면 새 비밀번호로
        startWith(REVIEW_EMAIL, "changed2027");
        reviewerLogin("review2026").andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        reviewerLogin("changed2027").andExpect(jsonPath("$.step").value("DONE"));
    }

    @Test
    void 설정이_없거나_비밀번호가_짧으면_만들지_않는다() {
        startWith(null, null);
        startWith(REVIEW_EMAIL, "short");
        assertThat(members.findByEmail(REVIEW_EMAIL)).isEmpty();
    }
}
