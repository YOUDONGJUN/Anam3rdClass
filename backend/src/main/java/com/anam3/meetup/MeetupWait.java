package com.anam3.meetup;

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
 * 빈자리 대기: 정원이 찬 3교시에 "자리 나면 알림"을 신청했다.
 * 자리가 나면(누가 나가거나 정원이 늘면) 대기한 사람 모두에게 알리고, 먼저 참여한 사람이 탄다.
 * 거절은 없다 — 연 사람은 몇 명이 기다리는지만 보고 정원을 늘릴 수 있다.
 */
@Entity
@Table(name = "meetup_waits",
        uniqueConstraints = @UniqueConstraint(name = "uk_meetup_wait", columnNames = {"meetup_id", "member_id"}))
public class MeetupWait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MeetupWait() {
    }

    public MeetupWait(Meetup meetup, Member member, Instant now) {
        this.meetup = meetup;
        this.member = member;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }
}
