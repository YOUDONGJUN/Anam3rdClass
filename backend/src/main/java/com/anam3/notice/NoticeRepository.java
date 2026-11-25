package com.anam3.notice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 밤에 올려서 새 공지 알림을 아침으로 미룬 공지 */
    @Query("select n from Notice n join fetch n.author left join fetch n.school where n.announcePending = true order by n.id")
    List<Notice> findHeldAnnouncements();

    /** 미룬 알림을 이제 처리했다고 적는다. 이미 처리됐으면 0 (서버가 둘이어도 한 번만 보낸다) */
    @Modifying
    @Query("update Notice n set n.announcePending = false where n.id = :id and n.announcePending = true")
    int releaseHeldAnnouncement(@Param("id") Long id);
}
