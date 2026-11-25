package com.anam3.ranking;

/** 랭킹: 성사된 뒤 모임 시간이 지난 3교시에 탄 기록 한 줄 (엔티티를 통째로 읽지 않으려고 필요한 칸만) */
public record RankingRide(Long memberId, Long meetupId, Long hostId, boolean federated, Long memberSchoolId,
        Long meetupSchoolId) {
}
