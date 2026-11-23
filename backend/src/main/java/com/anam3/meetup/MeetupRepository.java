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

    /** 아직 모이기 전인 번개 (무산 여부는 서비스에서 걸러낸다) */
    @Query("""
            select m from Meetup m join fetch m.host
            where m.canceled = false and m.meetAt > :now
            order by m.meetAt asc
            """)
    List<Meetup> findUpcoming(@Param("now") Instant now, Pageable pageable);

    /** 성사되어 이미 모인 번개 */
    @Query("""
            select m from Meetup m join fetch m.host
            where m.canceled = false and m.meetAt <= :now and m.participantCount >= m.minPeople
            order by m.meetAt desc
            """)
    List<Meetup> findPastConfirmed(@Param("now") Instant now, Pageable pageable);

    @Query("select m from Meetup m join fetch m.host order by m.meetAt desc")
    List<Meetup> findAllWithHost(Pageable pageable);

    @Query("select count(m) from Meetup m where m.canceled = false and m.participantCount >= m.minPeople")
    long countConfirmed();
}
