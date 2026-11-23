package com.anam3.auth;

import com.anam3.member.MemberRole;

/**
 * 컨트롤러에서 @AuthenticationPrincipal LoginMember me 로 받는 현재 로그인 사용자.
 */
public record LoginMember(Long id, MemberRole role) {

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }
}
