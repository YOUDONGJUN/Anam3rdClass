package com.anam3.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatReactionRepository extends JpaRepository<ChatReaction, Long> {

    @Query("select r from ChatReaction r where r.message.id = :messageId and r.member.id = :memberId")
    Optional<ChatReaction> find(@Param("messageId") Long messageId, @Param("memberId") Long memberId);

    /** 메시지들의 공감: [메시지 번호, 누른 원우 번호, 공감] */
    @Query("select r.message.id, r.member.id, r.reaction from ChatReaction r where r.message.id in :ids")
    List<Object[]> findOf(@Param("ids") Collection<Long> ids);
}
