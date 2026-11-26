package com.anam3.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.anam3.member.MemberRepository;
import com.anam3.school.DepartmentRepository;
import com.anam3.school.SchoolRepository;
import com.anam3.support.ApiTestSupport;

/** 학교별 시험 계정: SCHOOL_TEST_PASSWORD 가 있으면 학교마다 하나씩 만든다 */
class SchoolTestAccountsBootstrapTest extends ApiTestSupport {

    @Autowired
    MemberRepository members;

    @Autowired
    SchoolRepository schools;

    @Autowired
    DepartmentRepository departments;

    @Autowired
    PlatformTransactionManager transactionManager;

    private void startWith(String password) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                new SchoolTestAccountsBootstrap(password, members, schools, departments, clock).run(null));
    }

    private ResultActions login(String email, String password) throws Exception {
        return post("/api/auth/email/login", null, """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    @Test
    void 학교마다_시험_계정을_만들고_이메일과_비밀번호로_들어간다() throws Exception {
        startWith("test-pass-1234");
        assertThat(jdbc.queryForObject("select count(*) from members where email like '%@test.anam3'", Long.class))
                .isEqualTo(jdbc.queryForObject("select count(*) from schools", Long.class));

        login("yonsei@test.anam3", "test-pass-1234").andExpect(status().isOk())
                .andExpect(jsonPath("$.member.name").value("연세대 시험계정"))
                .andExpect(jsonPath("$.member.school.code").value("YONSEI"))
                .andExpect(jsonPath("$.member.rankingHidden").value(true));
        login("ku@test.anam3", "wrong-password").andExpect(status().isBadRequest());

        // 다시 시작해도 하나씩, 비밀번호를 바꾸면 바뀐다
        startWith("another-pass-99");
        assertThat(jdbc.queryForObject("select count(*) from members where email = 'ku@test.anam3'", Long.class))
                .isEqualTo(1);
        login("ku@test.anam3", "another-pass-99").andExpect(status().isOk());
    }

    @Test
    void 비밀번호가_없거나_짧으면_만들지_않는다() {
        startWith("");
        startWith("short");
        assertThat(jdbc.queryForObject("select count(*) from members where email like '%@test.anam3'", Long.class))
                .isZero();
    }
}
