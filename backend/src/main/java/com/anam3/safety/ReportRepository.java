package com.anam3.safety;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 이 사람이 최근에 한 신고 (같은 대상을 또 신고하는지, 하루 한도를 넘었는지 본다) */
    List<Report> findByReporterIdAndCreatedAtAfter(Long reporterId, Instant since);

    /** 대학원장: 모든 신고. 처리 안 된 것(OPEN)부터 — 상태는 글자로 저장돼서 OPEN < RESOLVED */
    @Query("""
            select r from Report r left join fetch r.meetup left join fetch r.notice
              left join fetch r.member left join fetch r.school
            order by r.status asc, r.createdAt desc, r.id desc
            """)
    List<Report> findForOwner(Pageable pageable);

    /** 교수: 자기 학교 신고만 */
    @Query("""
            select r from Report r left join fetch r.meetup left join fetch r.notice
              left join fetch r.member left join fetch r.school
            where r.school.id = :schoolId
            order by r.status asc, r.createdAt desc, r.id desc
            """)
    List<Report> findForSchool(@Param("schoolId") Long schoolId, Pageable pageable);

    long countByStatus(Report.Status status);

    long countBySchoolIdAndStatus(Long schoolId, Report.Status status);
}
