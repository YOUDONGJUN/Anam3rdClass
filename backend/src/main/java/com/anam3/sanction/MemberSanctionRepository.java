package com.anam3.sanction;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberSanctionRepository extends JpaRepository<MemberSanction, Long> {

    /** 한 원우의 제재 기록 (최근 것부터, 취소한 것도) */
    @Query("""
            select s from MemberSanction s join fetch s.actor left join fetch s.revokedBy
            where s.member.id = :memberId order by s.createdAt desc, s.id desc
            """)
    List<MemberSanction> findHistory(@Param("memberId") Long memberId);

    /** 지금 단계: 취소하지 않은 경고 수 */
    long countByMemberIdAndRevokedAtIsNull(Long memberId);

    /** 여러 원우의 단계 한 번에 — [회원 번호, 수] */
    @Query("""
            select s.member.id, count(s) from MemberSanction s
            where s.revokedAt is null and s.member.id in :memberIds group by s.member.id
            """)
    List<Object[]> countActive(@Param("memberIds") Collection<Long> memberIds);

    /** 아직 확인하지 않은 경고·기간 정지 (오래된 것부터) — 앱을 열면 하나씩 보여 준다 */
    @Query("""
            select s from MemberSanction s
            where s.member.id = :memberId and s.revokedAt is null and s.acknowledgedAt is null
              and s.kind <> com.anam3.sanction.MemberSanction.Kind.BAN
            order by s.createdAt asc, s.id asc
            """)
    List<MemberSanction> findUnacknowledged(@Param("memberId") Long memberId);

    /** 취소하지 않은 이 원우의 제재 (정지를 다시 계산할 때) */
    List<MemberSanction> findByMemberIdAndRevokedAtIsNull(Long memberId);
}
