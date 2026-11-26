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

    /**
     * 이 원우가 열었거나 참여한 3교시 중 모임 시간이 from ~ to 사이(양 끝 제외)인 것 (취소 제외, exceptId 는 뺀다).
     * 시간이 겹치는 3교시를 찾는다
     */
    @Query("""
            select p.meetup from Participation p
            where p.member.id = :memberId and p.meetup.canceled = false
              and p.meetup.meetAt > :from and p.meetup.meetAt < :to and p.meetup.id <> :exceptId
            order by p.meetup.meetAt asc
            """)
    List<Meetup> findNearby(@Param("memberId") Long memberId, @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to, @Param("exceptId") Long exceptId);

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

    /** 후기: 이 원우가 함께한, 모임 시간이 since~now 사이인 3교시 (취소 빼고, 최근 것부터) */
    @Query("""
            select m from Participation p join p.meetup m
            where p.member.id = :memberId and m.canceled = false and m.meetAt > :since and m.meetAt < :now
            order by m.meetAt desc
            """)
    List<Meetup> findPastMeetupsOf(@Param("memberId") Long memberId, @Param("since") java.time.Instant since,
            @Param("now") java.time.Instant now);

    /** 랭킹: 성사된 뒤 모임 시간이 지난 3교시(모임 시간이 since ~ now)에 탄 기록. MeetupRepository.countConfirmed 와 같은 성사 기준 */
    @Query("""
            select new com.anam3.ranking.RankingRide(p.member.id, m.id, m.host.id, m.federated, p.member.school.id,
                   m.school.id)
            from Participation p join p.meetup m
            where m.canceled = false and m.meetAt < :now and m.meetAt >= :since
              and m.participantCount >= coalesce(m.minPeople, 2)
            """)
    List<com.anam3.ranking.RankingRide> findRides(@Param("since") java.time.Instant since,
            @Param("now") java.time.Instant now);
}
