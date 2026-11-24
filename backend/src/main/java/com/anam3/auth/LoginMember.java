package com.anam3.auth;

import com.anam3.member.MemberRole;

/**
 * 컨트롤러에서 @AuthenticationPrincipal LoginMember me 로 받는 현재 로그인 사용자.
 * 요청마다 DB 에서 읽어 만든다 (JwtAuthenticationFilter) — 직책·기수·학교가 바뀌면 바로 반영된다.
 */
public record LoginMember(Long id, MemberRole role, int cohort, Long schoolId) {

    /** 대학원장 (최고 관리자, 모든 학교) */
    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }

    /** 교수 또는 대학원장 */
    public boolean isStaff() {
        return role != null && role.isStaff();
    }

    /** 이 학교를 관리할 수 있는지: 대학원장은 모든 학교, 교수는 자기 학교만 */
    public boolean manages(Long otherSchoolId) {
        return isOwner() || (isStaff() && schoolId.equals(otherSchoolId));
    }
}
