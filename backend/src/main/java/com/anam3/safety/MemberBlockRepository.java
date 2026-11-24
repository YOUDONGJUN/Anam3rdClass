package com.anam3.safety;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Optional<MemberBlock> findByIdAndBlockerId(Long id, Long blockerId);

    /** 내가 차단한 사람 (학교·학과까지 한 번에) */
    @Query("""
            select b from MemberBlock b join fetch b.blocked m join fetch m.school left join fetch m.department
            where b.blocker.id = :blockerId
            order by b.createdAt desc, b.id desc
            """)
    List<MemberBlock> findAllOfBlocker(@Param("blockerId") Long blockerId);

    @Query("select b.blocked.id from MemberBlock b where b.blocker.id = :memberId")
    Set<Long> findBlockedIds(@Param("memberId") Long memberId);

    @Query("select b.blocker.id from MemberBlock b where b.blocked.id = :memberId")
    Set<Long> findBlockerIds(@Param("memberId") Long memberId);

    @Query("""
            select count(b) > 0 from MemberBlock b
            where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    /** 탈퇴할 때 그 사람의 차단 관계를 모두 지운다 */
    @Modifying
    @Query("delete from MemberBlock b where b.blocker.id = :memberId or b.blocked.id = :memberId")
    void deleteAllOf(@Param("memberId") Long memberId);
}
