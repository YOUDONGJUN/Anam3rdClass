package com.anam3.meetup;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupLeaveRepository extends JpaRepository<MeetupLeave, Long> {

    /** 이 원우가 이때부터 성사된 3교시에서 나간 횟수 */
    @Query("select count(l) from MeetupLeave l where l.memberId = :memberId and l.leftAt > :since")
    long countSince(@Param("memberId") Long memberId, @Param("since") Instant since);

    /** 이 3교시에서 나간 기록 (최근 것부터) */
    java.util.List<MeetupLeave> findByMeetupIdOrderByLeftAtDesc(Long meetupId);

    @Modifying
    @Query("delete from MeetupLeave l where l.leftAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
