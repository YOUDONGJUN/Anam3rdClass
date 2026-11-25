package com.anam3.ranking;

/** 랭킹 카테고리. 화면 이름은 frontend/src/lib/ranking.ts */
public enum RankingCategory {
    /** 방장왕: 성사된 3교시를 연 횟수 */
    HOST,
    /** 출석왕: 성사된 3교시에 탄 횟수 (연 것 포함) */
    ATTEND,
    /** 성사율: 연 3교시 중 성사된 비율 (3번 이상 연 사람만) */
    RATE,
    /** 연합왕: 다른 학교 원우와 함께한 횟수 */
    FEDERATION
}
