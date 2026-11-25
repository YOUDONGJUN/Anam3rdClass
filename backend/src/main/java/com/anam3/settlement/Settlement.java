package com.anam3.settlement;

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

import com.anam3.meetup.Meetup;
import com.anam3.member.Member;

/**
 * 정산: 3교시 하나에 하나. 계산한 사람(참여자 누구든)이 총액을 넣으면 1인 금액을 나머지에게 요청한다.
 * 돈은 서비스가 받지 않는다 — 송금은 각자 토스·카카오페이 앱에서 하고, 여기서는 누가 보냈는지만 적는다.
 */
@Entity
@Table(name = "settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_meetup", columnNames = "meetup_id"))
public class Settlement {

    /** 1인 금액은 이 단위로 올림 */
    public static final int ROUND_UNIT = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    /** 계산한 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Member payer;

    @Column(nullable = false)
    private int total;

    @Column(name = "per_person", nullable = false)
    private int perPerson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 마지막으로 정산 요청·다시 알리기를 한 시각 (다시 알리기는 하루 한 번) */
    @Column(name = "reminded_at", nullable = false)
    private Instant remindedAt;

    /** 모두 보낸 시각 */
    @Column(name = "done_at")
    private Instant doneAt;

    /** 밤이라 아침 8시로 미룬 요청·다시 알리기 */
    @Column(name = "request_pending", nullable = false)
    private boolean requestPending;

    /** 밤이라 아침 8시로 미룬 "정산 끝" */
    @Column(name = "done_pending", nullable = false)
    private boolean donePending;

    protected Settlement() {
    }

    public Settlement(Meetup meetup, Member payer, int total, int headcount, Instant now) {
        this.meetup = meetup;
        this.payer = payer;
        this.createdAt = now;
        this.remindedAt = now;
        change(total, headcount, now);
    }

    /** 1인 금액: 계산한 사람 포함 headcount 명으로 나누고 100원 단위로 올림 */
    public static int perPerson(int total, int headcount) {
        long unit = (long) headcount * ROUND_UNIT;
        return (int) ((total + unit - 1) / unit) * ROUND_UNIT;
    }

    /** 총액 · 인원 바꾸기 (아무도 보내기 전에만 — SettlementService 가 먼저 본다) */
    public void change(int total, int headcount, Instant now) {
        this.total = total;
        this.perPerson = perPerson(total, headcount);
        this.updatedAt = now;
    }

    /** 요청·다시 알리기를 했다 (다시 알리기는 이때부터 하루 뒤에) */
    public void reminded(Instant now) {
        this.remindedAt = now;
    }

    /** 밤이라 요청·다시 알리기 알림을 아침 8시로 미룬다 (PushNotifier.announceHeld 가 보낸다) */
    public void holdRequest() {
        this.requestPending = true;
    }

    /** 모두 보냈다 */
    public void done(Instant now) {
        this.doneAt = now;
    }

    /** 밤이라 "정산 끝" 알림을 아침 8시로 미룬다 */
    public void holdDone() {
        this.donePending = true;
    }

    /** 누가 보냄을 취소해서 다시 덜 보낸 정산이 됐다 */
    public void reopen() {
        this.doneAt = null;
        this.donePending = false;
    }

    public boolean isPaidBy(Long memberId) {
        return payer.getId().equals(memberId);
    }

    public boolean isDone() {
        return doneAt != null;
    }

    public Long getId() {
        return id;
    }

    public Meetup getMeetup() {
        return meetup;
    }

    public Member getPayer() {
        return payer;
    }

    public int getTotal() {
        return total;
    }

    public int getPerPerson() {
        return perPerson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public boolean isRequestPending() {
        return requestPending;
    }

    public boolean isDonePending() {
        return donePending;
    }
}
