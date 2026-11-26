package com.anam3.admin;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    /** 최근 기록부터 (한 사람·대상 학교까지 한 번에) */
    @Query("""
            select a from AdminAction a join fetch a.actor
              left join fetch a.targetMember t left join fetch t.school
            order by a.createdAt desc, a.id desc
            """)
    List<AdminAction> findRecent(Pageable pageable);
}
