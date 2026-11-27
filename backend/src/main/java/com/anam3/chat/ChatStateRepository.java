package com.anam3.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatStateRepository extends JpaRepository<ChatState, Long> {

    @Query("select s from ChatState s where s.meetup.id = :meetupId and s.member.id = :memberId")
    Optional<ChatState> find(@Param("meetupId") Long meetupId, @Param("memberId") Long memberId);

    /** 이 톡방에서 알림을 끈 사람 */
    @Query("select s.member.id from ChatState s where s.meetup.id = :meetupId and s.muted = true")
    List<Long> findMutedMemberIds(@Param("meetupId") Long meetupId);

    /** 이 톡방 사람들이 어디까지 읽었는지: [원우 번호, 마지막으로 읽은 메시지 번호] */
    @Query("select s.member.id, s.lastReadId from ChatState s where s.meetup.id = :meetupId")
    List<Object[]> findReadIds(@Param("meetupId") Long meetupId);

    @Query("select s from ChatState s where s.member.id = :memberId and s.meetup.id in :meetupIds")
    List<ChatState> findOf(@Param("memberId") Long memberId, @Param("meetupIds") Collection<Long> meetupIds);
}
