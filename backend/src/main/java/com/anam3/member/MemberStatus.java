package com.anam3.member;

public enum MemberStatus {
    ACTIVE,
    /** 운영자가 이용을 막은 계정 */
    BANNED,
    /** 본인이 탈퇴한 계정 (이름·이메일·카카오 연결은 지워졌다) */
    WITHDRAWN
}
