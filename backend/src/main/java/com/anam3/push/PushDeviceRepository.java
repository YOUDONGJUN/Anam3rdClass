package com.anam3.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anam3.member.MemberStatus;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByToken(String token);

    /** 이 회원의 기기, 최근에 연 것부터 */
    @Query("select d from PushDevice d where d.member.id = :memberId order by d.lastSeenAt desc, d.id desc")
    List<PushDevice> findAllOfMember(@Param("memberId") Long memberId);

    /** 이 회원들의 기기 (활동 중인 회원만) */
    @Query("""
            select new com.anam3.push.PushTarget(m.id, m.pushOff, d.token)
            from PushDevice d join d.member m
            where m.id in :memberIds and m.status = :active
            """)
    List<PushTarget> findTargets(@Param("memberIds") Collection<Long> memberIds,
            @Param("active") MemberStatus active);

    /** 한 학교 원우 모두의 기기 (활동 중인 회원만) */
    @Query("""
            select new com.anam3.push.PushTarget(m.id, m.pushOff, d.token)
            from PushDevice d join d.member m
            where m.school.id = :schoolId and m.status = :active
            """)
    List<PushTarget> findSchoolTargets(@Param("schoolId") Long schoolId, @Param("active") MemberStatus active);

    /** 한 학교 한 기수 원우의 기기 (활동 중인 회원만) — 우리 기수만 3교시 */
    @Query("""
            select new com.anam3.push.PushTarget(m.id, m.pushOff, d.token)
            from PushDevice d join d.member m
            where m.school.id = :schoolId and m.cohort = :cohort and m.status = :active
            """)
    List<PushTarget> findCohortTargets(@Param("schoolId") Long schoolId, @Param("cohort") int cohort,
            @Param("active") MemberStatus active);

    /** 다섯 학교 원우 모두의 기기 (활동 중인 회원만) */
    @Query("""
            select new com.anam3.push.PushTarget(m.id, m.pushOff, d.token)
            from PushDevice d join d.member m
            where m.status = :active
            """)
    List<PushTarget> findAllTargets(@Param("active") MemberStatus active);

    @Modifying
    @Query("delete from PushDevice d where d.token = :token")
    int deleteByToken(@Param("token") String token);

    @Modifying
    @Query("delete from PushDevice d where d.member.id = :memberId")
    int deleteAllOfMember(@Param("memberId") Long memberId);
}
