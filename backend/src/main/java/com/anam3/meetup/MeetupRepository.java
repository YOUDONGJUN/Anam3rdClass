package com.anam3.meetup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    /** 참여/나가기 동시 요청으로 정원이 넘치지 않게 행을 잠그고 가져온다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meetup m where m.id = :id")
    Optional<Meetup> findByIdForUpdate(@Param("id") Long id);

    @Query("select m from Meetup m join fetch m.host where m.id = :id")
    Optional<Meetup> findWithHost(@Param("id") Long id);

    /** 아직 모이기 전인 3교시 (무산 여부는 서비스에서 걸러낸다) */
    @Query("""
            select m from Meetup m join fetch m.host
            where m.canceled = false and m.meetAt > :now
            order by m.meetAt asc
            """)
    List<Meetup> findUpcoming(@Param("now") Instant now, Pageable pageable);

    /**
     * 성사되어 실제로 모인 3교시. 최소 인원이 없는(무조건 진행) 3교시는 2명 이상 모였을 때만 친다.
     */
    @Query("""
            select m from Meetup m join fetch m.host
            where m.canceled = false and m.meetAt <= :now and m.participantCount >= coalesce(m.minPeople, 2)
            order by m.meetAt desc
            """)
    List<Meetup> findPastConfirmed(@Param("now") Instant now, Pageable pageable);

    @Query("select m from Meetup m join fetch m.host order by m.meetAt desc")
    List<Meetup> findAllWithHost(Pageable pageable);

    /** 성사된(최소 인원이 모인) 3교시 수. 최소 인원이 없으면 2명 이상 모였을 때만 친다 */
    @Query("select count(m) from Meetup m where m.canceled = false and m.participantCount >= coalesce(m.minPeople, 2)")
    long countConfirmed();
}
