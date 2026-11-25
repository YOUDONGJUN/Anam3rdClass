package com.anam3.ranking;

/** 랭킹(성사율): 모임 시간이 지난, 누군가 연 3교시 한 개 */
public record RankingHosted(Long hostId, boolean canceled, int participantCount, Integer minPeople) {

    /** 성사 = 취소 안 됨 + 최소 인원 달성 (최소 인원이 없으면 2명 이상). MeetupRepository.countConfirmed 와 같은 기준 */
    public boolean reached() {
        return !canceled && participantCount >= (minPeople == null ? 2 : minPeople);
    }
}
