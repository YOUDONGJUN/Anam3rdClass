package com.anam3.settlement;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementShareRepository extends JpaRepository<SettlementShare, Long> {

    @Query("select sh from SettlementShare sh join fetch sh.member where sh.settlement.id = :settlementId order by sh.id")
    List<SettlementShare> findWithMember(@Param("settlementId") Long settlementId);

    /** 보낼 사람 모두 */
    @Query("select sh.member.id from SettlementShare sh where sh.settlement.id = :settlementId")
    List<Long> findMemberIds(@Param("settlementId") Long settlementId);

    /** 아직 안 보낸 사람 */
    @Query("select sh.member.id from SettlementShare sh where sh.settlement.id = :settlementId and sh.sentAt is null")
    List<Long> findUnsentMemberIds(@Param("settlementId") Long settlementId);

    /** 내가 보낼 정산: 아직 안 보냈고 정산이 안 끝난 것 (최근 3교시부터) */
    @Query("""
            select sh from SettlementShare sh join fetch sh.settlement s join fetch s.meetup m join fetch s.payer
            where sh.member.id = :memberId and sh.sentAt is null and s.doneAt is null
            order by m.meetAt desc
            """)
    List<SettlementShare> findDueOf(@Param("memberId") Long memberId);

    @Modifying
    @Query("delete from SettlementShare sh where sh.settlement.id = :settlementId")
    int deleteBySettlementId(@Param("settlementId") Long settlementId);

    @Modifying
    @Query("""
            delete from SettlementShare sh
            where sh.settlement.id in (select s.id from Settlement s where s.meetup.id = :meetupId)
            """)
    int deleteByMeetupId(@Param("meetupId") Long meetupId);
}
