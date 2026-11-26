package com.anam3.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 가장 최근부터 (화면은 뒤집어서 오래된 것부터 보여준다) */
    @Query("""
            select m from ChatMessage m join fetch m.member mb join fetch mb.school left join fetch mb.department
            where m.meetup.id = :meetupId order by m.id desc
            """)
    List<ChatMessage> findLatest(@Param("meetupId") Long meetupId, Pageable pageable);

    @Query("""
            select m from ChatMessage m join fetch m.member mb join fetch mb.school left join fetch mb.department
            where m.meetup.id = :meetupId and m.id < :before order by m.id desc
            """)
    List<ChatMessage> findBefore(@Param("meetupId") Long meetupId, @Param("before") Long before, Pageable pageable);

    @Query("""
            select m from ChatMessage m join fetch m.member mb join fetch mb.school left join fetch mb.department
            where m.meetup.id = :meetupId and m.id > :after order by m.id asc
            """)
    List<ChatMessage> findAfter(@Param("meetupId") Long meetupId, @Param("after") Long after, Pageable pageable);

    /** 사진 (한 번에 읽어 두려고) */
    @Query("select p from ChatPhoto p where p.message.id in :ids order by p.position")
    List<ChatPhoto> findPhotos(@Param("ids") Collection<Long> ids);

    /** 오늘 보낸 톡방 사진 장수 (하루 제한) */
    @Query("""
            select count(p) from ChatPhoto p
            where p.message.member.id = :memberId and p.message.createdAt >= :since
            """)
    long countPhotosSince(@Param("memberId") Long memberId, @Param("since") Instant since);

    /** 내가 들어가 있는 톡방 중 메시지가 있는 곳: [3교시 번호, 마지막 메시지 번호], 최근 순 */
    @Query("""
            select m.meetup.id, max(m.id) from ChatMessage m
            where m.meetup.id in (select p.meetup.id from Participation p where p.member.id = :memberId)
            group by m.meetup.id order by max(m.id) desc
            """)
    List<Object[]> findRoomsOf(@Param("memberId") Long memberId, Pageable pageable);

    /** 안 읽은 메시지 수: [3교시 번호, 개수]. 내가 보낸 것 · 지운 것 · 차단한 사람 것은 세지 않는다 */
    @Query("""
            select m.meetup.id, count(m.id) from ChatMessage m
            where m.meetup.id in :meetupIds and m.member.id <> :memberId and m.deletedAt is null
              and m.member.id not in :hidden
              and m.id > coalesce((select s.lastReadId from ChatState s
                                   where s.meetup.id = m.meetup.id and s.member.id = :memberId), 0)
            group by m.meetup.id
            """)
    List<Object[]> countUnread(@Param("memberId") Long memberId, @Param("meetupIds") Collection<Long> meetupIds,
            @Param("hidden") Collection<Long> hidden);

    @Query("""
            select m from ChatMessage m join fetch m.member mb join fetch m.meetup
            where m.id in :ids
            """)
    List<ChatMessage> findWithMeetup(@Param("ids") Collection<Long> ids);

    @Query("select max(m.id) from ChatMessage m where m.meetup.id = :meetupId")
    Long findLastId(@Param("meetupId") Long meetupId);
}
