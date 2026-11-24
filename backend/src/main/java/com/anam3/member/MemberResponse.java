package com.anam3.member;

import java.util.List;

import com.anam3.push.PushType;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 내 정보 (본인에게만 실명과 이메일을 보여준다).
 * school.code 로 화면 테마(학교 색)를 고른다. termsAgreed=false 면 약관 동의 화면부터 보여준다.
 * kakaoLinked · hasPassword: 로그인 방법 (카카오 · 학교 이메일 + 비밀번호)
 * pushOff: 끈 앱 푸시 알림 종류
 */
public record MemberResponse(Long id, String email, SchoolTag school, String graduateSchool, String name, int cohort,
        String cohortLabel, Long departmentId, String departmentLabel, String departmentShortLabel, MemberRole role,
        boolean termsAgreed, boolean kakaoLinked, boolean hasPassword, List<PushType> pushOff) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getEmail(), SchoolTag.from(m.getSchool()),
                m.getDepartment() == null ? m.getSchool().getGraduateSchool() : m.getDepartment().graduateSchoolName(),
                m.getName(), m.getCohort(), m.cohortLabel(),
                m.getDepartment() == null ? null : m.getDepartment().getId(), m.departmentLabel(),
                m.departmentShortLabel(), m.getRole(), m.hasAgreedTerms(), m.isKakaoLinked(), m.hasEmailPassword(),
                List.copyOf(m.getPushOff()));
    }
}
