package com.anam3.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.anam3.meetup.Meetup;
import com.anam3.member.Member;

/** 톡방 메시지 한 개: 글, 사진(여러 장), 또는 둘 다 · 이모티콘 하나 · 들어옴/나감 안내 줄. 답장이면 원래 메시지 번호 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatKind kind = ChatKind.TEXT;

    /** 이모티콘 이름 (ChatStickers) — 이모티콘 메시지만 */
    @Column(length = ChatStickers.MAX_KEY)
    private String sticker;

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

    /** 답장: 어느 메시지에 답했는지 */
    @Column(name = "reply_to_id")
    private Long replyToId;

    /** 톡방 공지로 올린 시각 (연 사람이 올린다. 톡방마다 하나) */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

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

    public static ChatMessage sticker(Meetup meetup, Member member, String sticker, Instant now) {
        ChatMessage message = new ChatMessage(meetup, member, null, now);
        message.sticker = sticker;
        return message;
    }

    /** 안내 줄: member 가 들어왔다(JOIN) · 나갔다(LEAVE) */
    public static ChatMessage notice(Meetup meetup, Member member, ChatKind kind, Instant now) {
        ChatMessage message = new ChatMessage(meetup, member, null, now);
        message.kind = kind;
        return message;
    }

    public void addPhoto(String storageKey) {
        photos.add(new ChatPhoto(this, storageKey, photos.size()));
    }

    /** 지우기: 자리는 남기고 글·사진은 없앤다. 공지였으면 내려간다 */
    public void delete(Instant now) {
        this.deletedAt = now;
        this.body = null;
        this.sticker = null;
        this.pinnedAt = null;
        this.photos.clear();
    }

    public void replyTo(Long messageId) {
        this.replyToId = messageId;
    }

    public void pin(Instant now) {
        this.pinnedAt = now;
    }

    public void unpin() {
        this.pinnedAt = null;
    }

    public Long getReplyToId() {
        return replyToId;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    /** 원우가 보낸 메시지가 아니라 들어옴/나감 안내 줄 */
    public boolean isNotice() {
        return kind != ChatKind.TEXT;
    }

    public ChatKind getKind() {
        return kind;
    }

    public String getSticker() {
        return sticker;
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
