package com.anam3.admin;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.admin.AdminDtos.AdminActionView;
import com.anam3.auth.LoginMember;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;

/**
 * 관리자 조치 기록. 조치를 한 서비스가 같은 트랜잭션 안에서 부른다 (조치가 롤백되면 기록도 남지 않는다).
 * 보기는 대학원장만 (SecurityConfig: /api/admin/actions)
 */
@Component
@Transactional
public class AdminActionLog {

    /** 관리 화면에 보여 줄 최근 기록 수 */
    static final int LIST_SIZE = 200;

    private final AdminActionRepository actions;
    private final MemberRepository members;
    private final Clock clock;

    public AdminActionLog(AdminActionRepository actions, MemberRepository members, Clock clock) {
        this.actions = actions;
        this.members = members;
        this.clock = clock;
    }

    /** target·meetupId·detail 은 없으면 null. detail 에 이름·이메일 같은 개인정보는 적지 않는다 */
    public void record(LoginMember actor, AdminAction.Type type, Member target, Long meetupId, String detail) {
        actions.save(new AdminAction(members.getReferenceById(actor.id()), actor.role(), type, target, meetupId,
                detail, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AdminActionView> recent() {
        return actions.findRecent(PageRequest.of(0, LIST_SIZE)).stream().map(AdminActionView::from).toList();
    }
}
