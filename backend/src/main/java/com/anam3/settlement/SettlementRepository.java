package com.anam3.settlement;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query("select s from Settlement s join fetch s.payer where s.meetup.id = :meetupId")
    Optional<Settlement> findByMeetupId(@Param("meetupId") Long meetupId);

    /** 고치기 · 보냄 표시가 겹치지 않게 잠그고 가져온다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.meetup.id = :meetupId")
    Optional<Settlement> findByMeetupIdForUpdate(@Param("meetupId") Long meetupId);

    /** 밤이라 아침으로 미룬 알림이 있는 정산 */
    @Query("""
            select s from Settlement s join fetch s.meetup join fetch s.payer
            where s.requestPending = true or s.donePending = true
            order by s.id
            """)
    List<Settlement> findHeld();

    /** 미룬 알림을 이제 처리했다고 적는다. 이미 처리됐으면 0 (서버가 둘이어도 한 번만 보낸다) */
    @Modifying
    @Query("""
            update Settlement s set s.requestPending = false, s.donePending = false
            where s.id = :id and (s.requestPending = true or s.donePending = true)
            """)
    int releaseHeld(@Param("id") Long id);

    @Modifying
    @Query("delete from Settlement s where s.meetup.id = :meetupId")
    int deleteByMeetupId(@Param("meetupId") Long meetupId);
}
