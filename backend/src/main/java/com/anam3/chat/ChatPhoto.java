package com.anam3.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 톡방 사진. 저장소 chat/ 아래 — 올린 날부터 30일 뒤 저장소가 지운다 */
@Entity
@Table(name = "chat_photos")
public class ChatPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(nullable = false)
    private int position;

    protected ChatPhoto() {
    }

    ChatPhoto(ChatMessage message, String storageKey, int position) {
        this.message = message;
        this.storageKey = storageKey;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    /** 메시지를 읽지 않고 번호만 (프록시라 추가 조회가 없다) */
    public Long getMessageId() {
        return message.getId();
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getPosition() {
        return position;
    }
}
