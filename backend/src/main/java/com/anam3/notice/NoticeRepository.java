package com.anam3.notice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 연합 공지 + 우리 학교 공지. 고정한 것부터 */
    @Query("""
            select n from Notice n join fetch n.author
            where n.school is null or n.school.id = :schoolId
            order by n.pinned desc, n.createdAt desc
            """)
    List<Notice> findForList(@Param("schoolId") Long schoolId, Pageable pageable);

    /** 대학원장: 다섯 학교 공지와 연합 공지 모두. 고정한 것부터 */
    @Query("""
            select n from Notice n join fetch n.author left join fetch n.school
            order by n.pinned desc, n.createdAt desc
            """)
    List<Notice> findAllForList(Pageable pageable);

    long countBySchoolId(Long schoolId);

    @Query("select n from Notice n join fetch n.author where n.id = :id")
    Optional<Notice> findWithAuthor(@Param("id") Long id);
}
