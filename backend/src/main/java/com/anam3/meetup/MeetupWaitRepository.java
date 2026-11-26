package com.anam3.meetup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupWaitRepository extends JpaRepository<MeetupWait, Long> {

    boolean existsByMeetupIdAndMemberId(Long meetupId, Long memberId);

    long countByMeetupId(Long meetupId);

    /** 이 3교시에 자리 나기를 기다리는 사람 (알림 받을 사람) */
    @Query("select w.member.id from MeetupWait w where w.meetup.id = :meetupId")
    List<Long> findMemberIds(@Param("meetupId") Long meetupId);

    /** 참여했거나 신청을 취소했다 */
    @Modifying
    @Query("delete from MeetupWait w where w.meetup.id = :meetupId and w.member.id = :memberId")
    int deleteOne(@Param("meetupId") Long meetupId, @Param("memberId") Long memberId);

    /** 3교시를 지울 때 */
    @Modifying
    @Query("delete from MeetupWait w where w.meetup.id = :meetupId")
    int deleteByMeetupId(@Param("meetupId") Long meetupId);

    /** 이 원우가 빈자리를 기다리는, 아직 모이기 전인 3교시 (가까운 것부터) */
    @Query("""
            select m from MeetupWait w join w.meetup m join fetch m.host
            where w.member.id = :memberId and m.meetAt > :now and m.canceled = false
            order by m.meetAt
            """)
    List<Meetup> findOpenMeetupsOfMember(@Param("memberId") Long memberId, @Param("now") java.time.Instant now);

    /** 모임 시간이 지난 3교시의 대기 (매일 정리) */
    @Modifying
    @Query("delete from MeetupWait w where w.meetup.id in (select m.id from Meetup m where m.meetAt < :now)")
    int deleteForPastMeetups(@Param("now") java.time.Instant now);

    /** 탈퇴·이용 제한 때 */
    @Modifying
    @Query("delete from MeetupWait w where w.member.id = :memberId")
    int deleteAllOfMember(@Param("memberId") Long memberId);
}
