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

import com.anam3.common.GeoPoint;
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

    /** 연합 3교시: 다섯 학교 누구나 보고 참여한다 */
    @Column(nullable = false)
    private boolean federated;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 60)
    private String place;

    /** 장소 위치: 장소 검색에서 골랐을 때만 (직접 쓴 장소는 null). 가까운 순 · 지도 바로가기 */
    @Column
    private Double lat;

    @Column
    private Double lng;

    /** 장소의 지역 (시·구, 예: 서울 성북구). 장소 검색에서 골랐을 때만 */
    @Column(length = 30)
    private String region;

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

    /** 중간 합류 OK: 늦게 와도 되고, 성사된 뒤에도 모임 시작 전까지 참여할 수 있다 */
    @Column(name = "late_join", nullable = false)
    private boolean lateJoin;

    /** 참여비(원). null 이면 참여비 없음 */
    @Column
    private Integer fee;

    /** 참여비를 미리 보내야 하는지 */
    @Column(name = "fee_prepaid", nullable = false)
    private boolean feePrepaid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 모집 마감 30분 전 알림을 보낸 시각 (마감을 바꾸면 다시 비운다 → 새 마감 전에 또 알린다) */
    @Column(name = "deadline_reminded_at")
    private Instant deadlineRemindedAt;

    /** 모임 1시간 전 알림을 보낸 시각 (모임 시간을 바꾸면 다시 비운다 → 새 시간 1시간 전에 또 알린다) */
    @Column(name = "meet_reminded_at")
    private Instant meetRemindedAt;

    /** 밤(한국 시간 0~8시)에 열려서 새 3교시 알림을 아침 8시로 미뤘다 */
    @Column(name = "announce_pending", nullable = false)
    private boolean announcePending;

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

    /**
     * 실제로 여럿이 모인 3교시: 성사돼서 끝났고(DONE) 연 사람 말고도 한 명 이상 있었다.
     * 최소 인원 없이 열어 혼자 끝난 3교시는 후기·정산을 하지 않는다 (랭킹의 '성사'와 같은 기준)
     */
    public boolean isGatheredAt(Instant now) {
        return statusAt(now) == MeetupStatus.DONE && participantCount >= 2;
    }

    public boolean hasNoMinimum() {
        return minPeople == null;
    }

    public boolean isRecruitingAt(Instant now) {
        return !canceled && now.isBefore(deadlineAt) && now.isBefore(meetAt);
    }

    /**
     * 참여를 받는 중인지: 모집 마감 전. '중간 합류 OK'면 성사된 뒤에도 모임 시작 전까지 받는다
     * (마감까지 최소 인원이 안 모여 무산된 3교시는 받지 않는다)
     */
    public boolean isTakingJoinsAt(Instant now) {
        if (canceled || !now.isBefore(meetAt)) {
            return false;
        }
        return now.isBefore(deadlineAt) || (lateJoin && isReached());
    }

    public boolean isJoinableAt(Instant now) {
        return isTakingJoinsAt(now) && !isFull();
    }

    /** 중간 합류 · 참여비 (열 때 정하고, 수정에서 바꿀 수 있다) */
    public void applyOptions(boolean lateJoin, Integer fee, boolean feePrepaid) {
        this.lateJoin = lateJoin;
        this.fee = fee;
        this.feePrepaid = fee != null && feePrepaid;
    }

    public boolean isLateJoin() {
        return lateJoin;
    }

    public Integer getFee() {
        return fee;
    }

    public boolean isFeePrepaid() {
        return feePrepaid;
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

    /**
     * 한 명이 빠진다. 이미 성사된 3교시는 확정을 유지한다 — 최소 인원을 남은 인원으로 낮춰서
     * 다시 "모집중"으로 돌아가거나 공개된 실명이 다시 가려지지 않게 한다 (남은 사람이 2명 밑이면 최소 인원 없음).
     */
    public void removeParticipant() {
        boolean wasReached = isReached();
        this.participantCount--;
        if (wasReached && !isReached()) {
            this.minPeople = participantCount >= 2 ? participantCount : null;
        }
    }

    /**
     * 연 사람이 "지금 인원으로 확정": 최소 인원을 지금 인원으로 낮춰 바로 성사시킨다.
     * 마감이 지나 무산된 3교시도 모임 시간 전이면 이렇게 되살린다 (조건은 MeetupService 가 먼저 본다).
     */
    public void confirmWithCurrent(Instant now) {
        this.minPeople = participantCount;
        this.updatedAt = now;
    }

    /** 지금 인원으로 확정할 수 있는 상태: 취소 안 됨 · 모임 전 · 아직 성사 전 · 2명 이상 */
    public boolean isConfirmableAt(Instant now) {
        return !canceled && now.isBefore(meetAt) && !isReached() && participantCount >= 2;
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

    /** 인원 바꾸기 (이미 참여한 사람과 어긋나지 않는지는 MeetupService 가 먼저 본다) */
    public void changePeople(Integer minPeople, Integer maxPeople, Instant now) {
        this.minPeople = minPeople;
        this.maxPeople = maxPeople;
        this.updatedAt = now;
    }

    /** 시간 바꾸기 (시간 규칙은 MeetupService 가 먼저 본다) */
    public void reschedule(Instant meetAt, Instant deadlineAt, Instant now) {
        if (!deadlineAt.equals(this.deadlineAt)) {
            this.deadlineRemindedAt = null;
        }
        if (!meetAt.equals(this.meetAt)) {
            this.meetRemindedAt = null;
        }
        this.meetAt = meetAt;
        this.deadlineAt = deadlineAt;
        this.updatedAt = now;
    }

    /** 대상 바꾸기. 우리 기수만이면 개설자의 기수로 (대학원장이 고쳐도 개설자 기준) */
    public void changeAudience(MeetupAudience audience, Instant now) {
        this.federated = audience == MeetupAudience.ALL;
        this.onlyCohort = audience == MeetupAudience.COHORT ? host.getCohort() : null;
        this.updatedAt = now;
    }

    /** 대상을 바꾼다면 이 사람이 그 대상에 들어가는지 (이미 참여한 원우가 빠지게 좁히지 않으려고 본다) */
    public boolean wouldBeOpenTo(MeetupAudience audience, Long schoolId, int cohort) {
        if (audience == MeetupAudience.ALL) {
            return true;
        }
        return isOfSchool(schoolId) && (audience == MeetupAudience.SCHOOL || host.getCohort() == cohort);
    }

    /** 새 3교시 알림을 아침으로 미룬다 (PushNotifier.announceHeld 가 보낸다) */
    public void holdAnnouncement() {
        this.announcePending = true;
    }

    public boolean isAnnouncePending() {
        return announcePending;
    }

    public void cancel(Instant now) {
        this.canceled = true;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    /** 연 사람 넘기기: 참여자 중 한 명이 연 사람이 된다 (원래 연 사람은 참여자로 남는다) */
    public void changeHost(Member next, Instant now) {
        this.host = next;
        this.updatedAt = now;
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

    /** 장소 위치 (없으면 null) */
    public GeoPoint getLocation() {
        return GeoPoint.of(lat, lng);
    }

    public String getRegion() {
        return region;
    }

    /** 장소 위치 바꾸기 (null 이면 지운다: 직접 쓴 장소) */
    public void changeLocation(GeoPoint location, String region) {
        this.lat = location == null ? null : location.lat();
        this.lng = location == null ? null : location.lng();
        this.region = location == null ? null : region;
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
