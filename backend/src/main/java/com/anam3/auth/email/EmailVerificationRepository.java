package com.anam3.auth.email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 이 사람(카카오 계정 또는 이메일 가입 요청)이 이 이메일로 가장 최근에 받은 코드 */
    Optional<EmailVerification> findFirstByEmailAndRequesterIdOrderByCreatedAtDescIdDesc(String email, Long requesterId);

    /** 위와 같은 코드의 번호만 (잠가서 다시 읽으려고 — 엔티티를 먼저 읽어 두면 잠근 뒤에도 예전 값이 남는다) */
    @Query("""
            select v.id from EmailVerification v
            where v.email = :email and v.requesterId = :requesterId
            order by v.createdAt desc, v.id desc
            """)
    List<Long> findLatestIds(@Param("email") String email, @Param("requesterId") Long requesterId, Pageable pageable);

    /** 코드 확인은 한 번에 하나씩: 동시에 여러 번 넣어 틀린 횟수 제한을 피하지 못하게 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.id = :id")
    Optional<EmailVerification> findByIdForUpdate(@Param("id") Long id);

    /** 이 이메일로 틀린 횟수 (누가 요청했든) */
    @Query("select coalesce(sum(v.attempts), 0) from EmailVerification v where v.email = :email and v.createdAt > :after")
    long sumAttemptsByEmailSince(@Param("email") String email, @Param("after") Instant after);

    long countByEmailAndRequesterIdAndCreatedAtAfter(String email, Long requesterId, Instant after);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    /** 이 이메일로 카카오 로그인한 사람들이 받은 코드 수 (요청한 사람 번호가 양수) */
    long countByEmailAndRequesterIdGreaterThanAndCreatedAtAfter(String email, Long requesterId, Instant after);

    /** 이 이메일로 "이메일로 시작"해서 받은 코드 수 (요청한 사람 번호가 음수 — 누구나 만들 수 있다) */
    long countByEmailAndRequesterIdLessThanAndCreatedAtAfter(String email, Long requesterId, Instant after);

    long countByRequesterIdAndCreatedAtAfter(Long requesterId, Instant after);

    /** 이메일로 시작한 요청(요청한 사람 번호가 음수) 전체 수: 카카오 없이 메일을 마구 보내게 하지 못하게 */
    long countByRequesterIdLessThanAndCreatedAtAfter(Long requesterId, Instant after);

    @Modifying
    @Query("delete from EmailVerification v where v.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
