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
import com.anam3.school.School;

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

    /** 연 사람의 학교. 연합이 아니면 이 학교 원우만 보고 참여한다 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    /** 연합 3교시: 다섯 학교 누구나 보고 참여한다 (열 때만 정하고 바꿀 수 없다) */
    @Column(nullable = false)
    private boolean federated;

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

    /** 우리 기수만: 열 때의 개설자 기수. null 이면 누구나. 다른 기수에게는 목록에 안 보이고 참여도 막힌다 */
    @Column(name = "only_cohort")
    private Integer onlyCohort;

    @Column(nullable = false)
    private boolean canceled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Meetup() {
    }

    public Meetup(Member host, MeetupAudience audience, String title, String description, String place,
            Collection<MeetupCategory> categories, List<String> purposes, Instant meetAt, Instant deadlineAt,
            Integer minPeople, Integer maxPeople, String openChatUrl, Instant now) {
        this.host = host;
        this.school = host.getSchool();
        this.federated = audience == MeetupAudience.ALL;
        this.onlyCohort = audience == MeetupAudience.COHORT ? host.getCohort() : null;
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

    /** 이 기수가 참여할 수 있는 3교시인지 (기수 제한이 없으면 누구나) */
    public boolean isOpenToCohort(int cohort) {
        return onlyCohort == null || onlyCohort == cohort;
    }

    public boolean isOfSchool(Long schoolId) {
        return school.getId().equals(schoolId);
    }

    /**
     * 이 사람이 보고 참여할 수 있는 3교시인지: 연합이면 누구나, 아니면 같은 학교(+ 우리 기수 전용이면 같은 기수)
     */
    public boolean isOpenTo(Long schoolId, int cohort) {
        return federated || (isOfSchool(schoolId) && isOpenToCohort(cohort));
    }

    public MeetupAudience audience() {
        if (federated) {
            return MeetupAudience.ALL;
        }
        return onlyCohort == null ? MeetupAudience.SCHOOL : MeetupAudience.COHORT;
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

    public School getSchool() {
        return school;
    }

    public boolean isFederated() {
        return federated;
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

    /** null 이면 누구나 */
    public Integer getOnlyCohort() {
        return onlyCohort;
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
