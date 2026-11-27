package com.anam3.safety;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anam3.member.MemberRole;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 3교시를 지워도 신고는 남긴다 (신고 당시 내용은 targetSnapshot 에 있다) */
    @Modifying
    @Query("update Report r set r.meetup = null where r.meetup.id = :meetupId")
    int detachMeetup(@Param("meetupId") Long meetupId);

    /** 이 사람이 최근에 한 신고 (같은 대상을 또 신고하는지, 하루 한도를 넘었는지 본다) */
    List<Report> findByReporterIdAndCreatedAtAfter(Long reporterId, Instant since);

    /** 대학원장: 모든 신고. 처리 안 된 것(OPEN)부터 — 상태는 글자로 저장돼서 OPEN < RESOLVED */
    @Query("""
            select r from Report r left join fetch r.meetup left join fetch r.notice
              left join fetch r.member left join fetch r.school
            order by r.status asc, r.createdAt desc, r.id desc
            """)
    List<Report> findForOwner(Pageable pageable);

    /** 교수: 자기 학교 신고 중 일반 원우가 대상인 것만 (교수·대학원장이 대상이면 대학원장만 본다) */
    @Query("""
            select r from Report r left join fetch r.meetup left join fetch r.notice
              left join fetch r.member target left join fetch r.school
            where r.school.id = :schoolId and (target is null or target.role = :user)
            order by r.status asc, r.createdAt desc, r.id desc
            """)
    List<Report> findForSchool(@Param("schoolId") Long schoolId, @Param("user") MemberRole user, Pageable pageable);

    long countByStatus(Report.Status status);

    /** 대학원장 후기 통계: 후기에서 '아쉬웠어요' 뒤 한 신고 수 */
    long countByFromFeedbackTrueAndCreatedAtGreaterThanEqual(Instant since);

    /** 교수 현황: 자기 학교의, 일반 원우가 대상인 처리 안 된 신고 수 */
    @Query("""
            select count(r) from Report r left join r.member target
            where r.school.id = :schoolId and r.status = :status and (target is null or target.role = :user)
            """)
    long countForSchool(@Param("schoolId") Long schoolId, @Param("status") Report.Status status,
            @Param("user") MemberRole user);

    /** "주의" 표시: 이 사람들을 since 뒤에 신고한 서로 다른 원우 수 — [회원 번호, 수] */
    @Query("""
            select r.member.id, count(distinct r.reporter.id) from Report r
            where r.member.id in :memberIds and r.createdAt >= :since group by r.member.id
            """)
    List<Object[]> countReporters(@Param("memberIds") java.util.Collection<Long> memberIds,
            @Param("since") Instant since);
}
