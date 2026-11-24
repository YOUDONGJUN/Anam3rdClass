package com.anam3.member;

/**
 * 직책. 화면에는 대학원 느낌의 이름으로 보인다 (frontend/src/lib/person.ts 의 ROLE_LABEL).
 */
public enum MemberRole {
    /** 원우 — 일반 회원 */
    USER,
    /** 교수 — 대학원장이 임명한다. 원우 차단·프로필 수정, 3교시 취소, 공지 관리 */
    MANAGER,
    /** 대학원장 — 최고 관리자. 늘 1명 (OWNER_EMAIL 로 정해진다) */
    OWNER;

    /** 관리 화면에 들어갈 수 있는 직책 (교수, 대학원장) */
    public boolean isStaff() {
        return this == MANAGER || this == OWNER;
    }
}
