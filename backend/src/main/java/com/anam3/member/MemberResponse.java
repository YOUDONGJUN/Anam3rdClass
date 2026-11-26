package com.anam3.member;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.anam3.push.PushType;
import com.anam3.school.SchoolDtos.SchoolTag;

/**
 * 내 정보 (본인에게만 실명과 이메일을 보여준다).
 * school.code 로 화면 테마(학교 색)를 고른다. termsAgreed=false 면 약관 동의 화면부터 보여준다.
 * kakaoLinked · hasPassword: 로그인 방법 (카카오 · 학교 이메일 + 비밀번호)
 * pushOff: 끈 앱 푸시 알림 종류
 * profileEditableAt: 원우가 학과·기수를 다시 고칠 수 있는 때 (30일에 한 번). null 이거나 지났으면 지금 고칠 수 있다
 * rankingHidden: 랭킹에 안 나오기
 * tossId · kakaoPayUrl: 정산에 쓰는 송금 링크 (없으면 null)
 */
public record MemberResponse(Long id, String email, SchoolTag school, String graduateSchool, String name, int cohort,
        String cohortLabel, Long departmentId, String departmentLabel, String departmentShortLabel, MemberRole role,
        boolean termsAgreed, boolean kakaoLinked, boolean hasPassword, List<PushType> pushOff,
        Instant profileEditableAt, Instant cohortEditableUntil, boolean rankingHidden, String tossId,
        String kakaoPayUrl) {

    /** 원우가 학과·기수를 다시 고치려면 이만큼 기다린다 */
    public static final Duration PROFILE_CHANGE_INTERVAL = Duration.ofDays(30);
    /**
     * 원우가 기수를 직접 고칠 수 있는 기간 (가입하고 하루 — 잘못 고른 것을 바로잡는 용도).
     * 그 뒤로는 교수·대학원장에게 요청한다 (기수를 바꿔 가며 다른 기수 전용 3교시에 들어가지 못하게)
     */
    public static final Duration COHORT_FIX_PERIOD = Duration.ofDays(1);

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getEmail(), SchoolTag.from(m.getSchool()),
                m.getDepartment() == null ? m.getSchool().getGraduateSchool() : m.getDepartment().graduateSchoolName(),
                m.getName(), m.getCohort(), m.cohortLabel(),
                m.getDepartment() == null ? null : m.getDepartment().getId(), m.departmentLabel(),
                m.departmentShortLabel(), m.getRole(), m.hasAgreedTerms(), m.isKakaoLinked(), m.hasEmailPassword(),
                List.copyOf(m.getPushOff()), profileEditableAt(m), cohortEditableUntil(m), m.isRankingHidden(),
                m.getTossId(), m.getKakaoPayUrl());
    }

    /** 원우가 기수를 직접 고칠 수 있는 마지막 때 (지났으면 교수·대학원장에게 요청). 교수·대학원장은 null = 언제나 */
    public static Instant cohortEditableUntil(Member m) {
        if (m.getRole() != MemberRole.USER) {
            return null;
        }
        return m.getCreatedAt() == null ? Instant.EPOCH : m.getCreatedAt().plus(COHORT_FIX_PERIOD);
    }

    /** 원우만 30일 제한이 있다 (교수·대학원장은 null = 언제나) */
    public static Instant profileEditableAt(Member m) {
        if (m.getRole() != MemberRole.USER || m.getProfileChangedAt() == null) {
            return null;
        }
        return m.getProfileChangedAt().plus(PROFILE_CHANGE_INTERVAL);
    }
}
