package com.anam3.safety;

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

import com.anam3.meetup.Meetup;
import com.anam3.member.Member;
import com.anam3.notice.Notice;
import com.anam3.school.School;

/**
 * 신고. 교수(자기 학교)·대학원장(모든 학교)이 관리 화면에서 보고 처리한다.
 * 신고한 사람은 관리 화면에도 보이지 않는다.
 */
@Entity
@Table(name = "reports")
public class Report {

    public enum TargetType {
        /** 3교시 (제목·설명·장소 등) */
        MEETUP,
        /** 공지 글 */
        NOTICE,
        /** 3교시에서 만난 사람 */
        MEMBER
    }

    public enum Reason {
        SPAM, ABUSE, SEXUAL, ILLEGAL, PRIVACY, OTHER
    }

    public enum Status {
        OPEN, RESOLVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    /** 신고한 3교시, 또는 사람을 신고한 3교시 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meetup_id")
    private Meetup meetup;

    /** 공지가 지워지면 null 이 된다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notice notice;

    /** 신고당한 사람 (3교시를 연 사람, 공지 글쓴이, 신고한 사람) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 신고 대상의 학교. 교수는 자기 학교 신고만 본다 (null 이면 연합 공지 — 대학원장만) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(length = 500)
    private String detail;

    /** 신고 당시 제목·내용 (글이 지워지거나 고쳐져도 확인할 수 있게) */
    @Column(name = "target_snapshot", length = 1000)
    private String targetSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private Member resolvedBy;

    protected Report() {
    }

    public Report(Member reporter, TargetType targetType, Meetup meetup, Notice notice, Member member, School school,
            Reason reason, String detail, Instant now) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.meetup = meetup;
        this.notice = notice;
        this.member = member;
        this.school = school;
        this.reason = reason;
        this.detail = detail;
        this.status = Status.OPEN;
        this.createdAt = now;
    }

    public void resolve(Member by, Instant now) {
        this.status = Status.RESOLVED;
        this.resolvedBy = by;
        this.resolvedAt = now;
    }

    public void reopen() {
        this.status = Status.OPEN;
        this.resolvedBy = null;
        this.resolvedAt = null;
    }

    public Long getId() {
        return id;
    }

    public Member getReporter() {
        return reporter;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Meetup getMeetup() {
        return meetup;
    }

    public Notice getNotice() {
        return notice;
    }

    public Member getMember() {
        return member;
    }

    public School getSchool() {
        return school;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public String getTargetSnapshot() {
        return targetSnapshot;
    }

    public void snapshot(String title, String body) {
        String text = title + (body == null || body.isBlank() ? "" : "\n" + body.strip());
        this.targetSnapshot = text.length() > 1000 ? text.substring(0, 999) + "…" : text;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Member getResolvedBy() {
        return resolvedBy;
    }
}
