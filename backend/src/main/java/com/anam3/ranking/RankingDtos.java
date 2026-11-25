package com.anam3.ranking;

import java.time.Instant;
import java.util.List;

import com.anam3.school.SchoolDtos.SchoolTag;

public final class RankingDtos {

    private RankingDtos() {
    }

    /**
     * 랭킹 한 판. period: month(이번 달, KST) | all(전체). scope: all(연합) | school(우리 학교).
     * since: 이번 달이면 1일 0시(KST), 전체면 null.
     */
    public record RankingView(String period, String scope, Instant since, List<CategoryView> categories,
            List<SchoolRow> schools) {
    }

    /** 카테고리 하나: 상위 10명 + 내 기록 (기록이 없으면 null) */
    public record CategoryView(RankingCategory category, List<Entry> top, Entry mine) {
    }

    /**
     * 한 사람. 이름은 가려서 (김*준) — 내 이름과 대학원장 이름만 그대로 (MeetupMapper 와 같은 규칙).
     * 기수는 넣지 않는다 (학교 · 학과만).
     * value: 횟수. 성사율은 value = 성사된 수, of = 연 수. rank 가 null 이면 랭킹에 안 나오는 중(내 기록만)
     */
    public record Entry(Integer rank, String name, boolean masked, boolean me, boolean owner, SchoolTag school,
            String departmentLabel, int value, Integer of) {
    }

    /** 학교 대항: 참여율 = 이 기간에 한 번이라도 탄 원우(active) ÷ 가입 원우(members) */
    public record SchoolRow(int rank, SchoolTag school, long members, long active, long meetups, boolean mine) {
    }
}
