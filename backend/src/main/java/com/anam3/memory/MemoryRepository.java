package com.anam3.memory;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    /**
     * 추억 모아보기: 내가 함께한 3교시의 추억 + 내가 볼 수 있는 3교시의 공개 추억 (우리 학교 · 연합).
     * 우리 기수 전용 3교시는 화면에 내기 전에 서비스가 한 번 더 거른다.
     */
    @Query("""
            select m from Memory m join fetch m.meetup mt join fetch mt.school join fetch m.member mb
              join fetch mb.school left join fetch mb.department
            where m.id < :before
              and (mt.id in (select p.meetup.id from Participation p where p.member.id = :memberId)
                   or (m.visibility = com.anam3.memory.Memory.Visibility.PUBLIC
                       and (mt.federated = true or mt.school.id = :schoolId)))
            order by m.id desc
            """)
    List<Memory> findFeed(@Param("memberId") Long memberId, @Param("schoolId") Long schoolId,
            @Param("before") Long before, Pageable pageable);

    @Query("""
            select m from Memory m join fetch m.meetup mt join fetch mt.school join fetch m.member mb
              join fetch mb.school left join fetch mb.department
            where mt.id = :meetupId order by m.id desc
            """)
    List<Memory> findOfMeetup(@Param("meetupId") Long meetupId);

    @Query("select p from MemoryPhoto p where p.memory.id in :ids order by p.position")
    List<MemoryPhoto> findPhotos(@Param("ids") Collection<Long> ids);

    /** 이 3교시에 올라온 추억 사진 장수 (3교시마다 10장) */
    @Query("select count(p) from MemoryPhoto p where p.memory.meetup.id = :meetupId")
    long countPhotosOf(@Param("meetupId") Long meetupId);

    @Query("select m from Memory m where m.member.id = :memberId")
    List<Memory> findAllOfMember(@Param("memberId") Long memberId);
}
