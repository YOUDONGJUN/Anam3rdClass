package com.anam3.bootstrap;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.common.AppProperties;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.school.Department;
import com.anam3.school.DepartmentRepository;
import com.anam3.school.School;
import com.anam3.school.SchoolRepository;

/**
 * 스토어 심사용 계정. 심사관은 학교 메일이 없어서 가입을 못 하니, REVIEW_EMAIL + REVIEW_PASSWORD 를 넣으면
 * 서버가 시작할 때 그 이메일 + 비밀번호로 로그인할 수 있는 일반 원우 계정(고려대, "심사계정")을 만든다.
 * 비밀번호를 바꾸면 다음 시작 때 바뀐다. 심사가 끝나면 두 값을 지우고 관리 화면에서 강제 탈퇴시키면 된다.
 */
@Component
@Order(2)
public class ReviewerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewerBootstrap.class);
    static final String NAME = "심사계정";

    private final AppProperties props;
    private final MemberRepository members;
    private final SchoolRepository schools;
    private final DepartmentRepository departments;
    private final Clock clock;
    private final PasswordEncoder passwords = new BCryptPasswordEncoder();

    public ReviewerBootstrap(AppProperties props, MemberRepository members, SchoolRepository schools,
            DepartmentRepository departments, Clock clock) {
        this.props = props;
        this.members = members;
        this.schools = schools;
        this.departments = departments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.reviewEmail();
        String password = props.reviewPassword();
        if (email == null || password == null) {
            return;
        }
        if (password.length() < 8) {
            log.warn("REVIEW_PASSWORD 는 8자 이상이어야 해서 심사용 계정을 만들지 않았습니다");
            return;
        }
        Instant now = clock.instant();
        Member reviewer = members.findByEmail(email).orElse(null);
        if (reviewer == null) {
            School school = schools.findByCode("KU").orElse(null);
            if (school == null) {
                log.warn("고려대 학교 정보가 없어 심사용 계정을 만들지 않았습니다");
                return;
            }
            Department department = departments.findAllByOrderBySortOrderAscIdAsc().stream()
                    .filter(d -> d.getSchool().getId().equals(school.getId()) && d.isActive())
                    .min(Comparator.comparingInt(Department::getSortOrder))
                    .orElse(null);
            reviewer = members.save(new Member(null, email, school, NAME, school.getLatestCohort(), department,
                    MemberRole.USER, now));
            reviewer.agreeTerms(now);
            log.info("심사용 계정을 만들었습니다 ({})", email);
        }
        if (!reviewer.isActive()) {
            log.warn("심사용 계정({})이 탈퇴·이용 제한 상태라 그대로 둡니다", email);
            return;
        }
        // 같은 비밀번호면 그대로 둔다 (다시 정하면 토큰 버전이 올라가 심사관 로그인이 풀린다)
        if (!reviewer.hasEmailPassword() || !passwords.matches(password, reviewer.getEmailPasswordHash())) {
            reviewer.setEmailPassword(passwords.encode(password));
        }
    }
}
