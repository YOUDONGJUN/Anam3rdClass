package com.anam3.feedback;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.anam3.meetup.Meetup;
import com.anam3.member.Member;

/**
 * 3교시 후기 한 줄 (한 사람이 한 3교시에 하나).
 * 답하지 않고 닫으면 met 이 비어 있는 줄이 남아서 다시 묻지 않는다. 그 뒤에도 기간 안이면 상세 화면에서 답할 수 있다.
 * 누가 어떻게 답했는지는 아무에게도 보여주지 않는다 (대학원장 통계에는 학교별 숫자만).
 */
@Entity
@Table(name = "meetup_feedbacks",
        uniqueConstraints = @UniqueConstraint(name = "uk_meetup_feedback", columnNames = {"meetup_id", "member_id"}))
public class MeetupFeedback {

    public enum Rating {
        /** 좋았어요 */
        GOOD,
        /** 괜찮았어요 */
        OK,
        /** 아쉬웠어요 — 이 뒤에 한 사람 신고는 "후기에서" 표시가 붙는다 */
        BAD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 실제로 모였는지. null 이면 답하지 않고 닫았다 */
    private Boolean met;

    /** 모였을 때만 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private Rating rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    protected MeetupFeedback() {
    }

    /** 아직 답하지 않은 줄 (닫기만 했을 때 이대로 남는다) */
    public MeetupFeedback(Meetup meetup, Member member, Instant now) {
        this.meetup = meetup;
        this.member = member;
        this.createdAt = now;
    }

    /** 안 모였으면 만족도는 묻지 않는다 */
    public void answer(boolean met, Rating rating, Instant now) {
        this.met = met;
        this.rating = met ? rating : null;
        this.answeredAt = now;
    }

    public boolean isAnswered() {
        return met != null;
    }

    public Long getId() {
        return id;
    }

    public Meetup getMeetup() {
        return meetup;
    }

    public Boolean getMet() {
        return met;
    }

    public Rating getRating() {
        return rating;
    }
}
