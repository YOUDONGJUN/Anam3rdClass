package com.anam3.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 추억 사진 한 장. 저장소 memories/ 아래 — 지우지 않는다 */
@Entity
@Table(name = "memory_photos")
public class MemoryPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memory_id", nullable = false)
    private Memory memory;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(nullable = false)
    private int position;

    protected MemoryPhoto() {
    }

    MemoryPhoto(Memory memory, String storageKey, int position) {
        this.memory = memory;
        this.storageKey = storageKey;
        this.position = position;
    }

    public Long getMemoryId() {
        return memory.getId();
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getPosition() {
        return position;
    }
}
