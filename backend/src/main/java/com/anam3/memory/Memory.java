package com.anam3.memory;

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

/** 추억: 모였던 3교시에서 찍은 사진(1~5장)과 한 줄. 계속 보관한다 */
@Entity
@Table(name = "memories")
public class Memory {

    public static final int CAPTION_MAX = 200;

    /** 누가 보나 */
    public enum Visibility {
        /** 함께한 원우만 */
        PARTICIPANTS,
        /** 그 3교시를 볼 수 있는 원우 모두 (우리 학교 · 우리 기수 · 연합) */
        PUBLIC
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

    @Column(length = CAPTION_MAX)
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "memory", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<MemoryPhoto> photos = new ArrayList<>();

    protected Memory() {
    }

    public Memory(Meetup meetup, Member member, String caption, Visibility visibility, Instant now) {
        this.meetup = meetup;
        this.member = member;
        this.caption = caption;
        this.visibility = visibility;
        this.createdAt = now;
    }

    public void addPhoto(String storageKey) {
        photos.add(new MemoryPhoto(this, storageKey, photos.size()));
    }

    public boolean isBy(Long memberId) {
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

    public String getCaption() {
        return caption;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<MemoryPhoto> getPhotos() {
        return photos;
    }
}
