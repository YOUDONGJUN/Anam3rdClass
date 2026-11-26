package com.anam3.push;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anam3.member.MemberStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림, 최근 것부터 */
    @Query("select n from Notification n where n.member.id = :memberId order by n.createdAt desc, n.id desc")
    List<Notification> findRecent(@Param("memberId") Long memberId, Pageable page);

    @Query("select count(n) from Notification n where n.member.id = :memberId and n.readAt is null")
    long countUnread(@Param("memberId") Long memberId);

    /** 이 회원들의 같은 묶음 안 읽은 알림 (새 알림으로 바꿔 쓴다) */
    @Query("select n from Notification n where n.member.id in :memberIds and n.tag = :tag and n.readAt is null")
    List<Notification> findUnreadByTag(@Param("memberIds") Collection<Long> memberIds, @Param("tag") String tag);

    /** 이 중 활동 중인 회원 (탈퇴·차단된 회원의 알림함에는 담지 않는다) */
    @Query("select m.id from Member m where m.id in :memberIds and m.status = :active")
    List<Long> findActiveMemberIds(@Param("memberIds") Collection<Long> memberIds,
            @Param("active") MemberStatus active);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.member.id = :memberId and n.readAt is null")
    int markAllRead(@Param("memberId") Long memberId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.member.id = :memberId")
    int deleteAllOfMember(@Param("memberId") Long memberId);

    @Modifying
    @Query("delete from Notification n where n.path = :path")
    int deleteByPath(@Param("path") String path);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
