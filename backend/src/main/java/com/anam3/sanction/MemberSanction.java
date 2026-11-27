package com.anam3.sanction;

import java.time.Duration;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.anam3.member.Member;
import com.anam3.safety.Report;

/**
 * 제재 한 번 (경고 한 번 = 한 줄). 취소하지 않은 줄의 수가 단계다:
 * 1번째 경고 · 2번째 7일 정지 · 3번째 30일 정지 · 4번째부터 영구 정지.
 * 관리 화면에서 바로 "이용 차단"한 것도 영구 정지 한 줄로 남는다.
 */
@Entity
@Table(name = "member_sanctions")
public class MemberSanction {

    public enum Kind {
        WARNING("경고", null),
        SUSPEND_7("7일 정지", Duration.ofDays(7)),
        SUSPEND_30("30일 정지", Duration.ofDays(30)),
        BAN("영구 정지", null);

        private final String label;
        private final Duration length;

        Kind(String label, Duration length) {
            this.label = label;
            this.length = length;
        }

        public String label() {
            return label;
        }

        /** 기간 정지 길이 (경고 · 영구 정지는 null) */
        public Duration length() {
            return length;
        }

        /** 지금까지 받은 (취소하지 않은) 경고 수 → 이번 경고의 단계 */
        public static Kind forStrike(int strike) {
            return switch (strike) {
                case 1 -> WARNING;
                case 2 -> SUSPEND_7;
                case 3 -> SUSPEND_30;
                default -> strike < 1 ? WARNING : BAN;
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Report.Reason reason;

    @Column(length = 300)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private Member actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private Member revokedBy;

    protected MemberSanction() {
    }

    public MemberSanction(Member member, Kind kind, Report.Reason reason, String note, Report report, Member actor,
            Instant now) {
        this.member = member;
        this.kind = kind;
        this.reason = reason;
        this.note = note;
        this.report = report;
        this.actor = actor;
        this.createdAt = now;
        this.endsAt = kind.length() == null ? null : now.plus(kind.length());
    }

    public void acknowledge(Instant now) {
        if (acknowledgedAt == null) {
            this.acknowledgedAt = now;
        }
    }

    public void revoke(Member by, Instant now) {
        this.revokedAt = now;
        this.revokedBy = by;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Kind getKind() {
        return kind;
    }

    public Report.Reason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public Report getReport() {
        return report;
    }

    public Member getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
