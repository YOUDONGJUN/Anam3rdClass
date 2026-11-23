package com.anam3.bootstrap;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.common.AppProperties;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;

/**
 * ADMIN_LOGIN_ID / ADMIN_PASSWORD 환경변수로 운영자 계정을 준비한다.
 * - 계정이 없으면 새로 만든다.
 * - 이미 일반 회원으로 가입한 아이디면 운영자로 올린다 (비밀번호는 그대로).
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(AppProperties props, MemberRepository members, PasswordEncoder passwordEncoder,
            Clock clock) {
        this.props = props;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = props.admin();
        if (admin == null || admin.loginId() == null || admin.loginId().isBlank()) {
            log.info("ADMIN_LOGIN_ID 가 없어 관리자 계정 준비를 건너뜁니다");
            return;
        }
        String loginId = admin.loginId().strip().toLowerCase(Locale.ROOT);
        members.findByLoginId(loginId).ifPresentOrElse(existing -> {
            if (!existing.isAdmin()) {
                existing.promoteToAdmin();
                log.info("기존 회원 '{}' 을(를) 관리자로 지정했습니다", loginId);
            }
        }, () -> {
            if (admin.password() == null || admin.password().length() < 8) {
                log.warn("ADMIN_PASSWORD 가 없거나 8자 미만이라 관리자 계정을 만들지 않았습니다");
                return;
            }
            String name = admin.name() == null || admin.name().isBlank() ? "관리자" : admin.name().strip();
            int cohort = admin.cohort() == null ? 61 : admin.cohort();
            members.save(new Member(loginId, passwordEncoder.encode(admin.password()), name, cohort,
                    MemberRole.ADMIN, clock.instant()));
            log.info("관리자 계정 '{}' 을(를) 만들었습니다", loginId);
        });
    }
}
