package com.anam3.notice;

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

import com.anam3.member.Member;
import com.anam3.school.School;

/**
 * 공지 — 누구나 쓸 수 있고, 대학원장 글은 "대학원장" 배지가 붙는다. 상단 고정은 교수·대학원장만.
 * 학교별로 보인다. school 이 null 이면 연합 공지 (대학원장만 쓴다, 다섯 학교 모두에게 보인다).
 */
@Entity
@Table(name = "notices")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    /** null 이면 연합 공지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 3000)
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 밤(한국 시간 0~8시)에 올려서 새 공지 알림을 아침 8시로 미뤘다 */
    @Column(name = "announce_pending", nullable = false)
    private boolean announcePending;

    protected Notice() {
    }

    public Notice(Member author, School school, String title, String content, Instant now) {
        this.author = author;
        this.school = school;
        this.title = title;
        this.content = content;
        this.pinned = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isWrittenBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public void edit(String title, String content, Instant now) {
        this.title = title;
        this.content = content;
        this.updatedAt = now;
    }

    /** 새 공지 알림을 아침으로 미룬다 (PushNotifier.announceHeld 가 보낸다) */
    public void holdAnnouncement() {
        this.announcePending = true;
    }

    public boolean isAnnouncePending() {
        return announcePending;
    }

    public void pin(boolean pinned) {
        this.pinned = pinned;
    }

    public Long getId() {
        return id;
    }

    public Member getAuthor() {
        return author;
    }

    /** null 이면 연합 공지 */
    public School getSchool() {
        return school;
    }

    public boolean isFederated() {
        return school == null;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
