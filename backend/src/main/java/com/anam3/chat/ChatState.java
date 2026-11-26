package com.anam3.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.anam3.meetup.Meetup;
import com.anam3.member.Member;

/** 톡방 하나에 대한 한 사람의 상태: 어디까지 읽었는지 · 알림을 껐는지 */
@Entity
@Table(name = "chat_states",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_states", columnNames = {"meetup_id", "member_id"}))
public class ChatState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "last_read_id", nullable = false)
    private long lastReadId;

    @Column(nullable = false)
    private boolean muted;

    protected ChatState() {
    }

    public ChatState(Meetup meetup, Member member) {
        this.meetup = meetup;
        this.member = member;
    }

    /** 읽은 곳은 앞으로만 간다 (늦게 도착한 요청이 되돌리지 않게) */
    public void readUpTo(long messageId) {
        this.lastReadId = Math.max(lastReadId, messageId);
    }

    public void mute(boolean muted) {
        this.muted = muted;
    }

    public Long getMeetupId() {
        return meetup.getId();
    }

    public long getLastReadId() {
        return lastReadId;
    }

    public boolean isMuted() {
        return muted;
    }
}
