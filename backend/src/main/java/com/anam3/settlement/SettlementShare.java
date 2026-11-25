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

import com.anam3.member.Member;

/** 정산에서 한 사람 몫 (계산한 사람은 빼고). 보낸 사람이 [보냈어요]를 누르거나, 계산한 사람이 [받았어요]로 표시한다 */
@Entity
@Table(name = "settlement_shares",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_share", columnNames = {"settlement_id", "member_id"}))
public class SettlementShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 보낸 시각. 비어 있으면 아직 */
    @Column(name = "sent_at")
    private Instant sentAt;

    protected SettlementShare() {
    }

    public SettlementShare(Settlement settlement, Member member) {
        this.settlement = settlement;
        this.member = member;
    }

    /** 보냄 표시 · 취소 (이미 보냄이면 처음 시각을 그대로 둔다) */
    public void mark(boolean sent, Instant now) {
        if (!sent) {
            this.sentAt = null;
        } else if (sentAt == null) {
            this.sentAt = now;
        }
    }

    public boolean isSent() {
        return sentAt != null;
    }

    public Long getId() {
        return id;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public Member getMember() {
        return member;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
