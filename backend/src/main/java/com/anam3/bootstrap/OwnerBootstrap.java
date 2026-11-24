package com.anam3.bootstrap;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.OwnerPolicy;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;
import com.anam3.school.SchoolService;

/**
 * 서버가 시작할 때 대학원장(OWNER_EMAIL)을 1명으로 맞춘다.
 * <ul>
 *   <li>그 이메일의 회원이 있으면 대학원장으로 올리고, 다른 대학원장은 교수로 내린다</li>
 *   <li>아직 없으면: 예전(아이디·비밀번호 시절) 관리자 계정에 그 이메일을 붙여 둔다.
 *       대학원장이 카카오로 로그인해 그 이메일을 인증하면 예전 계정이 그대로 이어진다</li>
 * </ul>
 */
@Component
@Order(1)
public class OwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final OwnerPolicy ownerPolicy;
    private final MemberRepository members;
    private final SchoolService schoolService;

    public OwnerBootstrap(OwnerPolicy ownerPolicy, MemberRepository members, SchoolService schoolService) {
        this.ownerPolicy = ownerPolicy;
        this.members = members;
        this.schoolService = schoolService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String ownerEmail = ownerPolicy.ownerEmail();
        if (ownerEmail == null) {
            log.info("OWNER_EMAIL 이 없어 대학원장 지정을 건너뜁니다");
            return;
        }
        Optional<Member> owner = members.findByEmail(ownerEmail);
        if (owner.isEmpty()) {
            owner = members.findAllByRole(MemberRole.OWNER).stream().filter(m -> m.getEmail() == null).findFirst();
            owner.ifPresent(legacy -> {
                legacy.assignEmail(ownerEmail, schoolService.findForEmail(ownerEmail).orElse(legacy.getSchool()));
                log.info("예전 관리자 계정({})에 대학원장 이메일을 붙였습니다. 그 이메일로 인증하면 이 계정으로 이어집니다",
                        legacy.getName());
            });
        }
        owner.ifPresent(ownerPolicy::apply);
    }
}
