package com.anam3.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.anam3.meetup.Meetup;
import com.anam3.member.Member;

/** 톡방 메시지 한 개: 글, 사진(여러 장), 또는 둘 다 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = MAX_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ChatPhoto> photos = new ArrayList<>();

    protected ChatMessage() {
    }

    public ChatMessage(Meetup meetup, Member member, String body, Instant now) {
        this.meetup = meetup;
        this.member = member;
        this.body = body;
        this.createdAt = now;
    }

    public void addPhoto(String storageKey) {
        photos.add(new ChatPhoto(this, storageKey, photos.size()));
    }

    /** 지우기: 자리는 남기고 글·사진은 없앤다 */
    public void delete(Instant now) {
        this.deletedAt = now;
        this.body = null;
        this.photos.clear();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isSentBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public Meetup getMeetup() {
        return meetup;
    }

    public Member getMember() {
        return member;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ChatPhoto> getPhotos() {
        return photos;
    }
}
