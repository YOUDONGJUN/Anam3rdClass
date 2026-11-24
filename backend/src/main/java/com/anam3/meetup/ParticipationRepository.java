package com.anam3.meetup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByMeetupIdAndMemberId(Long meetupId, Long memberId);

    Optional<Participation> findByMeetupIdAndMemberId(Long meetupId, Long memberId);

    /** 3교시를 지울 때 참여 기록도 함께 */
    @Modifying
    @Query("delete from Participation p where p.meetup.id = :meetupId")
    int deleteByMeetupId(@Param("meetupId") Long meetupId);

    /** 남이 연, 아직 열려 있는 3교시 중 이 원우가 참여한 것 (번호만 — 잠가서 새로 읽는다) */
    @Query("""
            select p.meetup.id from Participation p
            where p.member.id = :memberId and p.meetup.host.id <> :memberId
              and p.meetup.canceled = false and p.meetup.meetAt > :now
            """)
    List<Long> findOpenJoinedMeetupIds(@Param("memberId") Long memberId, @Param("now") java.time.Instant now);

    @Query("""
            select p from Participation p join fetch p.member
            where p.meetup.id = :meetupId
            order by p.createdAt asc, p.id asc
            """)
    List<Participation> findWithMember(@Param("meetupId") Long meetupId);

    /** 이 3교시 참여자 번호 (연 사람 포함) — 알림 받을 사람 */
    @Query("select p.member.id from Participation p where p.meetup.id = :meetupId")
    List<Long> findMemberIds(@Param("meetupId") Long meetupId);

    @Query("select p.meetup.id from Participation p where p.member.id = :memberId and p.meetup.id in :meetupIds")
    Set<Long> findJoinedMeetupIds(@Param("memberId") Long memberId, @Param("meetupIds") Collection<Long> meetupIds);

    @Query("""
            select m from Participation p join p.meetup m join fetch m.host
            where p.member.id = :memberId
            order by m.meetAt desc
            """)
    List<Meetup> findMeetupsOfMember(@Param("memberId") Long memberId, Pageable pageable);
}
