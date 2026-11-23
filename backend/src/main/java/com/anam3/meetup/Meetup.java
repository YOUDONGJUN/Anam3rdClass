package com.anam3.meetup;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

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

/** 3교시(번개 모임) */
@Entity
@Table(name = "meetups")
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 60)
    private String place;

    /** 마실 것. 여러 개면 "SOJU,BEER" (MeetupTags 참고) */
    @Column(nullable = false, length = 100)
    private String categories;

    /** 모임 목적 태그. 여러 개면 "친목,학업", 없으면 null */
    @Column(length = 100)
    private String purposes;

    /** 모이는 시각 */
    @Column(name = "meet_at", nullable = false)
    private Instant meetAt;

    /** 모집 마감 시각 (이때까지 최소 인원이 안 차면 무산) */
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    /** 개설자 포함 최소 인원. null 이면 최소 인원 없음 — 몇 명이 오든 진행(열자마자 성사) */
    @Column(name = "min_people")
    private Integer minPeople;

    /** 개설자 포함 정원. null 이면 인원 제한 없음 */
    @Column(name = "max_people")
    private Integer maxPeople;

    /** 개설자 포함 현재 인원 (참여/취소 시 행 잠금 후 갱신) */
    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    /** 카카오톡 오픈채팅 링크. 성사 후 참여자에게만 공개 */
    @Column(name = "open_chat_url", length = 300)
    private String openChatUrl;

    @Column(nullable = false)
    private boolean canceled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Meetup() {
    }

    public Meetup(Member host, String title, String description, String place,
            Collection<MeetupCategory> categories, List<String> purposes, Instant meetAt, Instant deadlineAt,
            Integer minPeople, Integer maxPeople, String openChatUrl, Instant now) {
        this.host = host;
        this.title = title;
        this.description = description;
        this.place = place;
        this.categories = MeetupTags.joinCategories(categories);
        this.purposes = MeetupTags.joinPurposes(purposes);
        this.meetAt = meetAt;
        this.deadlineAt = deadlineAt;
        this.minPeople = minPeople;
        this.maxPeople = maxPeople;
        this.openChatUrl = openChatUrl;
        this.participantCount = 1; // 개설자
        this.canceled = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public MeetupStatus statusAt(Instant now) {
        if (canceled) {
            return MeetupStatus.CANCELED;
        }
        boolean reached = isReached();
        if (!now.isBefore(meetAt)) {
            return reached ? MeetupStatus.DONE : MeetupStatus.FAILED;
        }
        if (!now.isBefore(deadlineAt)) {
            return reached ? MeetupStatus.CONFIRMED : MeetupStatus.FAILED;
        }
        return reached ? MeetupStatus.CONFIRMED : MeetupStatus.RECRUITING;
    }

    /** 최소 인원 달성 여부. 최소 인원이 없으면 늘 달성(열자마자 성사) */
    public boolean isReached() {
        return minPeople == null || participantCount >= minPeople;
    }

    public boolean hasNoMinimum() {
        return minPeople == null;
    }

    public boolean isRecruitingAt(Instant now) {
        return !canceled && now.isBefore(deadlineAt) && now.isBefore(meetAt);
    }

    public boolean isJoinableAt(Instant now) {
        return isRecruitingAt(now) && !isFull();
    }

    public boolean isOpenAt(Instant now) {
        MeetupStatus status = statusAt(now);
        return status == MeetupStatus.RECRUITING || status == MeetupStatus.CONFIRMED;
    }

    public boolean isUnlimited() {
        return maxPeople == null;
    }

    public boolean isFull() {
        return maxPeople != null && participantCount >= maxPeople;
    }

    public boolean isHostedBy(Long memberId) {
        return host.getId().equals(memberId);
    }

    public void addParticipant() {
        this.participantCount++;
    }

    public void removeParticipant() {
        this.participantCount--;
    }

    public void update(String title, String description, String place, Collection<MeetupCategory> categories,
            List<String> purposes, String openChatUrl, Instant now) {
        this.title = title;
        this.description = description;
        this.place = place;
        this.categories = MeetupTags.joinCategories(categories);
        this.purposes = MeetupTags.joinPurposes(purposes);
        this.openChatUrl = openChatUrl;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.canceled = true;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Member getHost() {
        return host;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPlace() {
        return place;
    }

    public List<MeetupCategory> getCategories() {
        return MeetupTags.splitCategories(categories);
    }

    public List<String> getPurposes() {
        return MeetupTags.splitPurposes(purposes);
    }

    public Instant getMeetAt() {
        return meetAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    /** null 이면 최소 인원 없음 */
    public Integer getMinPeople() {
        return minPeople;
    }

    /** null 이면 인원 제한 없음 */
    public Integer getMaxPeople() {
        return maxPeople;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public String getOpenChatUrl() {
        return openChatUrl;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
