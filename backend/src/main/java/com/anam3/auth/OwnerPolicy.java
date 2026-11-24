package com.anam3.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.common.AppProperties;
import com.anam3.member.Member;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberRole;

/**
 * 대학원장은 늘 1명 — OWNER_EMAIL 로 인증한 계정이다.
 * 다른 대학원장이 있으면 교수로 내린다. API 로는 대학원장을 만들거나 넘길 수 없다.
 */
@Component
public class OwnerPolicy {

    private final MemberRepository members;
    private final String ownerEmail;

    public OwnerPolicy(MemberRepository members, AppProperties props) {
        this.members = members;
        this.ownerEmail = props.ownerEmail();
    }

    public String ownerEmail() {
        return ownerEmail;
    }

    /** 이 회원이 대학원장 이메일이면 대학원장으로 올리고, 다른 대학원장은 교수로 내린다 */
    @Transactional
    public void apply(Member member) {
        if (ownerEmail == null || !ownerEmail.equals(member.getEmail())) {
            return;
        }
        if (!member.isOwner()) {
            member.changeRole(MemberRole.OWNER);
        }
        members.findAllByRole(MemberRole.OWNER).stream()
                .filter(other -> !other.getId().equals(member.getId()))
                .forEach(other -> other.changeRole(MemberRole.MANAGER));
    }
}
