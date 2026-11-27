package com.anam3.admin;

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
import com.anam3.member.MemberRole;

/**
 * 관리자 조치 한 건 (누가 · 언제 · 무엇을 · 누구에게). 대학원장이 관리 화면 '기록'에서 본다.
 * 사람은 번호로만 남긴다 — 이름은 볼 때 읽어서, 탈퇴한 사람은 "탈퇴한 원우"로 보인다 (이름을 detail 에 적지 않는다)
 */
@Entity
@Table(name = "admin_actions")
public class AdminAction {

    public enum Type {
        BAN("이용 정지"),
        UNBAN("정지 풀기"),
        WARN("경고"),
        REVOKE_SANCTION("경고 취소"),
        APPOINT("교수 임명"),
        DISMISS("교수 해임"),
        EDIT_PROFILE("정보 고치기"),
        ASSIGN_EMAIL("이메일 넣기"),
        WITHDRAW("탈퇴시키기"),
        DELETE_MEETUP("3교시 지우기"),
        DELETE_NOTICE("공지 지우기"),
        RESOLVE_REPORT("신고 처리"),
        REOPEN_REPORT("신고 다시 열기"),
        ADD_SCHOOL("학교 추가"),
        EDIT_SCHOOL("학교 고치기"),
        ADD_DOMAIN("도메인 추가"),
        REMOVE_DOMAIN("도메인 빼기"),
        ADD_DEPARTMENT("학과 추가"),
        EDIT_DEPARTMENT("학과 고치기");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private Member actor;

    /** 그때 직책 (나중에 해임돼도 그때 직책으로 보인다) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_role", nullable = false, length = 10)
    private MemberRole actorRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_member_id")
    private Member targetMember;

    /** 지운 3교시도 남도록 번호만 (외래 키 없음) */
    @Column(name = "target_meetup_id")
    private Long targetMeetupId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Type action;

    @Column(length = 300)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminAction() {
    }

    public AdminAction(Member actor, MemberRole actorRole, Type action, Member targetMember, Long targetMeetupId,
            String detail, Instant now) {
        this.actor = actor;
        this.actorRole = actorRole;
        this.action = action;
        this.targetMember = targetMember;
        this.targetMeetupId = targetMeetupId;
        this.detail = detail == null || detail.isBlank() ? null
                : detail.length() > 300 ? detail.substring(0, 299) + "…" : detail;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Member getActor() {
        return actor;
    }

    public MemberRole getActorRole() {
        return actorRole;
    }

    public Member getTargetMember() {
        return targetMember;
    }

    public Long getTargetMeetupId() {
        return targetMeetupId;
    }

    public Type getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
