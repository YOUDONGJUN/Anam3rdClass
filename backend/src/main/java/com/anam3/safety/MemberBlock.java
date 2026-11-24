package com.anam3.safety;

import java.time.Instant;

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

import com.anam3.member.Member;

/**
 * 차단. 차단하면 서로가 연 3교시가 목록에 보이지 않고, 서로의 3교시에 참여할 수 없다.
 * 차단당한 사람에게는 알리지 않는다.
 */
@Entity
@Table(name = "member_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_block", columnNames = {"blocker_id", "blocked_id"}))
public class MemberBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private Member blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private Member blocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemberBlock() {
    }

    public MemberBlock(Member blocker, Member blocked, Instant now) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Member getBlocker() {
        return blocker;
    }

    public Member getBlocked() {
        return blocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
