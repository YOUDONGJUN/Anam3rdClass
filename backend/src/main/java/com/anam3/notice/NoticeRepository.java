package com.anam3.notice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @Query("select n from Notice n join fetch n.author order by n.pinned desc, n.createdAt desc")
    List<Notice> findForList(Pageable pageable);

    @Query("select n from Notice n join fetch n.author where n.id = :id")
    Optional<Notice> findWithAuthor(@Param("id") Long id);
}
