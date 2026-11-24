package com.anam3.meetup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByMeetupIdAndMemberId(Long meetupId, Long memberId);

    Optional<Participation> findByMeetupIdAndMemberId(Long meetupId, Long memberId);

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

    @Query("select p.meetup.id from Participation p where p.member.id = :memberId and p.meetup.id in :meetupIds")
    Set<Long> findJoinedMeetupIds(@Param("memberId") Long memberId, @Param("meetupIds") Collection<Long> meetupIds);

    @Query("""
            select m from Participation p join p.meetup m join fetch m.host
            where p.member.id = :memberId
            order by m.meetAt desc
            """)
    List<Meetup> findMeetupsOfMember(@Param("memberId") Long memberId, Pageable pageable);
}
