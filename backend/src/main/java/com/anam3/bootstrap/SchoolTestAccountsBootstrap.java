package com.anam3.bootstrap;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.school.Department;
import com.anam3.school.DepartmentRepository;
import com.anam3.school.School;
import com.anam3.school.SchoolRepository;

/**
 * 학교마다 하나씩 이메일 + 비밀번호로 들어가는 시험 계정. 대학원장이 학교마다 화면이 어떻게 보이는지 확인하려고 쓴다.
 * SCHOOL_TEST_PASSWORD(8자 이상)를 넣으면 서버가 시작할 때 만든다:
 * <pre>
 * ku@test.anam3 · yonsei@test.anam3 · skku@test.anam3 · sogang@test.anam3 · hanyang@test.anam3
 * </pre>
 * (학교 코드 소문자 @test.anam3, 이름은 "고려대 시험계정"). 랭킹에는 나오지 않는다.
 * 비밀번호를 바꾸면 다음 시작 때 바뀐다. 다 쓰면 값을 지우고 관리 화면에서 강제 탈퇴시키면 된다.
 */
@Component
@Order(3)
public class SchoolTestAccountsBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchoolTestAccountsBootstrap.class);
    static final String DOMAIN = "test.anam3";
    static final String SUFFIX = " 시험계정";

    private final String password;
    private final MemberRepository members;
    private final SchoolRepository schools;
    private final DepartmentRepository departments;
    private final Clock clock;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public SchoolTestAccountsBootstrap(@Value("${app.test-accounts.password:}") String password,
            MemberRepository members, SchoolRepository schools, DepartmentRepository departments, Clock clock) {
        this.password = password == null ? "" : password.strip();
        this.members = members;
        this.schools = schools;
        this.departments = departments;
        this.clock = clock;
    }

    /** 그 학교 시험 계정 이메일 (학교 코드가 없으면 null) */
    static String emailOf(School school) {
        String code = school.getCode();
        return code == null || code.isBlank() ? null : code.toLowerCase(Locale.ROOT) + "@" + DOMAIN;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isEmpty()) {
            return;
        }
        if (password.length() < 8) {
            log.warn("SCHOOL_TEST_PASSWORD 는 8자 이상이어야 해서 학교별 시험 계정을 만들지 않았습니다");
            return;
        }
        Instant now = clock.instant();
        for (School school : schools.findAllByOrderBySortOrderAscIdAsc()) {
            String email = emailOf(school);
            if (email == null) {
                continue;
            }
            Member member = members.findByEmail(email).orElse(null);
            if (member == null) {
                Department department = departments.findAllByOrderBySortOrderAscIdAsc().stream()
                        .filter(d -> d.getSchool().getId().equals(school.getId()) && d.isActive())
                        .min(Comparator.comparingInt(Department::getSortOrder))
                        .orElse(null);
                member = members.save(new Member(null, email, school, school.getShortName() + SUFFIX,
                        school.getLatestCohort(), department, MemberRole.USER, now));
                member.agreeTerms(now);
                member.changeRankingHidden(true);
                log.info("학교별 시험 계정을 만들었습니다 ({})", email);
            }
            if (!member.isActive()) {
                log.warn("시험 계정({})이 탈퇴·이용 제한 상태라 그대로 둡니다", email);
                continue;
            }
            // 같은 비밀번호면 그대로 둔다 (다시 정하면 토큰 버전이 올라가 로그인이 풀린다)
            if (!member.hasEmailPassword() || !encoder.matches(password, member.getEmailPasswordHash())) {
                member.setEmailPassword(encoder.encode(password));
            }
        }
    }
}
