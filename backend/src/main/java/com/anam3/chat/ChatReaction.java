package com.anam3.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.anam3.member.Member;

/** 메시지 하나에 한 사람이 누른 공감 (하나만 — 다른 것을 누르면 바뀐다) */
@Entity
@Table(name = "chat_reactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_reactions", columnNames = {"message_id", "member_id"}))
public class ChatReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatReactionKind reaction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatReaction() {
    }

    public ChatReaction(ChatMessage message, Member member, ChatReactionKind reaction, Instant now) {
        this.message = message;
        this.member = member;
        this.reaction = reaction;
        this.createdAt = now;
    }

    public void change(ChatReactionKind reaction, Instant now) {
        this.reaction = reaction;
        this.createdAt = now;
    }

    public ChatReactionKind getReaction() {
        return reaction;
    }
}
