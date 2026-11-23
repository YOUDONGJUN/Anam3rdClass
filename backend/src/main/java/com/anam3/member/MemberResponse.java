package com.anam3.member;

/** 내 정보 (본인에게만 실명을 보여준다) */
public record MemberResponse(Long id, String loginId, String name, int cohort, String cohortLabel,
        Department department, String departmentLabel, MemberRole role) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getLoginId(), m.getName(), m.getCohort(), m.cohortLabel(),
                m.getDepartment(), m.departmentLabel(), m.getRole());
    }
}
