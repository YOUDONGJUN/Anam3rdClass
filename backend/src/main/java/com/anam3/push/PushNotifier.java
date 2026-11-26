package com.anam3.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.common.RateLimiter;
import com.anam3.common.RateLimits;
import com.anam3.meetup.Meetup;
import com.anam3.meetup.MeetupRepository;
import com.anam3.meetup.ParticipationRepository;
import com.anam3.notice.Notice;
import com.anam3.notice.NoticeRepository;
import com.anam3.safety.MemberBlockRepository;
import com.anam3.settlement.Settlement;
import com.anam3.settlement.SettlementRepository;
import com.anam3.settlement.SettlementShareRepository;

/**
 * 무슨 일이 생겼을 때 누구에게 어떤 알림을 보낼지. 서비스(3교시·공지·탈퇴)가 일을 마친 뒤 부른다.
 * 일을 한 본인에게는 보내지 않는다. 실명은 알림에 넣지 않는다 (성사 전에는 이름이 가려지는 서비스라서).
 */
@Component
public class PushNotifier {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN)
            .withZone(SEOUL);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(SEOUL);
    /** 모집 마감 몇 분 전에 알리나 */
    static final Duration DEADLINE_NOTICE = Duration.ofMinutes(30);
    /** 열고 금방 마감하는 3교시(마감까지 1시간 이하)는 마감 알림을 보내지 않는다 */
    private static final Duration SHORT_RECRUIT = Duration.ofHours(1);
    /** 모임 몇 분 전에 알리나 */
    static final Duration MEET_NOTICE = Duration.ofHours(1);
    /** 이보다 적게 남았으면 이미 이동 중이라 1시간 전 알림을 보내지 않는다 (서버가 늦게 깼을 때) */
    static final Duration MEET_NOTICE_TOO_LATE = Duration.ofMinutes(10);
    /** 모임 2시간 안쪽에 연 3교시는 방금 모였으니 1시간 전 알림을 보내지 않는다 */
    private static final Duration SHORT_NOTICE = Duration.ofHours(2);
    /**
     * 한 사람이 연 3교시로 학교 전체(연합이면 다섯 학교)에 보내는 새 3교시 알림은 하루에 이만큼까지.
     * 열고 지우기를 되풀이해 모두에게 알림을 퍼붓지 못하게 한다 (넘으면 3교시는 열리고 알림만 안 간다)
     */
    static final int DAILY_ANNOUNCEMENTS_PER_HOST = 3;

    private final PushService push;
    private final ParticipationRepository participations;
    private final MeetupRepository meetups;
    private final NoticeRepository notices;
    private final SettlementRepository settlements;
    private final SettlementShareRepository settlementShares;
    private final MemberBlockRepository blocks;
    private final Clock clock;
    private final RateLimiter announcementsPerHost;

    public PushNotifier(PushService push, ParticipationRepository participations, MeetupRepository meetups,
            NoticeRepository notices, SettlementRepository settlements, SettlementShareRepository settlementShares,
            MemberBlockRepository blocks, Clock clock, RateLimits limits) {
        this.push = push;
        this.participations = participations;
        this.meetups = meetups;
        this.notices = notices;
        this.settlements = settlements;
        this.settlementShares = settlementShares;
        this.blocks = blocks;
        this.clock = clock;
        this.announcementsPerHost = limits.create(DAILY_ANNOUNCEMENTS_PER_HOST, Duration.ofDays(1));
    }

    /** 누가 참여: 연 사람에게. 그 참여로 성사됐으면 참여자 모두에게 성사 알림으로 대신한다 */
    public void joined(Meetup meetup, Long joinerId, boolean reachedNow) {
        if (reachedNow) {
            reached(meetup, joinerId);
            return;
        }
        push.send(List.of(meetup.getHost().getId()), new PushMessage(PushType.JOIN, meetup.getTitle(),
                "한 명이 참여했어요 · " + headcount(meetup), path(meetup), tag(meetup, "people")));
    }

    /** 누가 나감: 연 사람에게 */
    public void left(Meetup meetup) {
        push.send(List.of(meetup.getHost().getId()), new PushMessage(PushType.JOIN, meetup.getTitle(),
                "한 명이 나갔어요 · " + headcount(meetup), path(meetup), tag(meetup, "people")));
    }

    /** 내보내기: 내보낸 원우에게 */
    public void kicked(Meetup meetup, Long memberId) {
        push.send(List.of(memberId), new PushMessage(PushType.CHANGED, meetup.getTitle(),
                "연 사람이 참여를 취소했어요 · 이 3교시에는 다시 참여할 수 없어요", path(meetup), tag(meetup, "changed")));
    }

    /** 연 사람 넘기기: 새 연 사람에게 (넘긴 사람 빼고 참여자에게도 알린다) */
    public void hostChanged(Meetup meetup, Long previousHostId) {
        Long next = meetup.getHost().getId();
        push.send(List.of(next), new PushMessage(PushType.CHANGED, meetup.getTitle(),
                "이 3교시의 연 사람이 됐어요 · 단톡방 링크와 시간을 챙겨 주세요", path(meetup), tag(meetup, "changed")));
    }

    /** 성사: 참여자 모두에게 (성사시킨 사람 빼고) */
    public void reached(Meetup meetup, Long actorId) {
        push.send(others(meetup, actorId), new PushMessage(PushType.REACHED, meetup.getTitle(),
                "성사됐어요! 참여자 실명과 단톡방이 공개됐어요", path(meetup), tag(meetup, "reached")));
    }

    /** 연 사람이 지금 인원으로 확정(무산에서 되살림 포함): 참여자 모두에게 (확정한 사람 빼고) */
    public void confirmedByHost(Meetup meetup, Long actorId, boolean revived) {
        String body = (revived ? "다시 진행해요! " : "성사됐어요! ") + meetup.getParticipantCount()
                + "명으로 확정했어요 · 참여자 실명과 단톡방이 공개됐어요";
        push.send(others(meetup, actorId), new PushMessage(PushType.REACHED, meetup.getTitle(), body, path(meetup),
                tag(meetup, "reached")));
    }

    /**
     * 시간·장소·참여비가 바뀌거나 단톡방 링크가 올라옴: 참여자에게 (고친 사람 빼고).
     * 한 번에 여럿이 바뀌면 시간·장소 → 참여비 → 단톡방 순으로 하나만 알린다 (자세한 건 상세에서)
     */
    public void changed(Meetup meetup, Long actorId, boolean timeChanged, boolean placeChanged, boolean feeChanged,
            boolean chatAdded) {
        String what;
        if (timeChanged || placeChanged) {
            what = timeChanged && placeChanged ? "시간·장소가 바뀌었어요 · " + WHEN.format(meetup.getMeetAt()) + " · "
                    + meetup.getPlace()
                    : timeChanged ? "시간이 바뀌었어요 · " + WHEN.format(meetup.getMeetAt())
                    : "장소가 바뀌었어요 · " + meetup.getPlace();
        } else if (feeChanged) {
            what = meetup.getFee() == null ? "참여비가 없어졌어요"
                    : "참여비가 바뀌었어요 · " + String.format(java.util.Locale.ROOT, "%,d원", meetup.getFee())
                            + (meetup.isFeePrepaid() ? " · 선입금" : "");
        } else if (chatAdded && meetup.isReached()) {
            what = "단톡방 링크가 올라왔어요";
        } else {
            return;
        }
        push.send(others(meetup, actorId), new PushMessage(PushType.CHANGED, meetup.getTitle(), what, path(meetup),
                tag(meetup, "changed")));
    }

    /**
     * 취소·삭제: 참여자에게 (취소한 사람 빼고). 지운 3교시는 참여 기록이 사라지므로 지우기 전에 읽어 둔 참여자를 넘긴다
     */
    public void canceled(Meetup meetup, List<Long> participantIds, Long actorId, boolean deleted) {
        List<Long> to = participantIds.stream().filter(id -> !id.equals(actorId)).toList();
        push.send(to, new PushMessage(PushType.CHANGED, meetup.getTitle(),
                "취소됐어요 · " + WHEN.format(meetup.getMeetAt()) + " 3교시", deleted ? "/" : path(meetup),
                tag(meetup, "changed")));
    }

    /**
     * 새 3교시: 연합이면 다섯 학교 모두, 아니면 우리 학교(우리 기수만이면 그 기수) 원우에게.
     * 밤(0~8시)에는 보내지 않고 아침 8시로 미룬다 (3교시를 여는 트랜잭션 안에서 불러야 미룬 것이 저장된다).
     * 한 사람이 하루에 보내는 새 3교시 알림은 DAILY_ANNOUNCEMENTS_PER_HOST 번까지 (미룬 것도 센다)
     */
    public void opened(Meetup meetup) {
        if (!announcementsPerHost.tryAcquire(String.valueOf(meetup.getHost().getId()), clock.instant())) {
            return;
        }
        if (QuietHours.contains(clock.instant())) {
            meetup.holdAnnouncement();
            return;
        }
        announce(meetup);
    }

    private void announce(Meetup meetup) {
        if (meetup.isFederated()) {
            federationOpened(meetup);
        } else {
            schoolOpened(meetup);
        }
    }

    /** 새 연합 3교시: 다섯 학교 원우 모두에게 (연 사람, 연 사람과 차단 관계인 사람 빼고) */
    public void federationOpened(Meetup meetup) {
        push.broadcast(null, new PushMessage(PushType.FEDERATION, "새 연합 3교시",
                meetup.getSchool().getShortName() + " · " + meetup.getTitle() + " · " + WHEN.format(meetup.getMeetAt()),
                path(meetup), "meetup-" + meetup.getId()), hostAndBlocked(meetup));
    }

    /**
     * 새 우리 학교 3교시: 그 학교 원우에게, 우리 기수만 3교시는 그 기수에게만 (연 사람, 연 사람과 차단 관계인 사람 빼고).
     * 이름은 넣지 않는다
     */
    public void schoolOpened(Meetup meetup) {
        Integer cohort = meetup.getOnlyCohort();
        PushMessage message = new PushMessage(PushType.SCHOOL, cohort == null ? "새 3교시" : "새 " + cohort + "기 3교시",
                meetup.getTitle() + " · " + WHEN.format(meetup.getMeetAt()) + " · " + meetup.getPlace(), path(meetup),
                "meetup-" + meetup.getId());
        Long schoolId = meetup.getSchool().getId();
        if (cohort == null) {
            push.broadcast(schoolId, message, hostAndBlocked(meetup));
        } else {
            push.broadcastCohort(schoolId, cohort, message, hostAndBlocked(meetup));
        }
    }

    /** 자리가 났어요: 자리 나면 알림을 신청한 사람에게. 먼저 참여한 사람이 탄다 */
    public void spotOpened(Meetup meetup, List<Long> waiterIds) {
        push.send(waiterIds, new PushMessage(PushType.SPOT, meetup.getTitle(),
                "자리가 났어요! 먼저 참여하는 원우가 타요 · " + headcount(meetup), path(meetup), tag(meetup, "spot")));
    }

    /**
     * 정산 요청 (고쳤으면 "정산을 고쳤어요"): 나눌 원우에게. 급하지 않은 알림이라 밤(0~8시)이면 아침 8시로 미룬다
     * (정산을 저장하는 트랜잭션 안에서 불러야 미룬 것이 저장된다)
     */
    public void settlementRequested(Settlement settlement, List<Long> to, boolean edited) {
        if (QuietHours.contains(clock.instant())) {
            settlement.holdRequest();
            return;
        }
        push.send(to, settleMessage(settlement, (edited ? "정산을 고쳤어요" : "정산 요청") + " · 1인 "
                + won(settlement.getPerPerson())));
    }

    /** 정산 취소: 보낼 원우 모두에게 (이미 보낸 원우는 다시 만들면 새로 요청받는다) */
    public void settlementCanceled(Settlement settlement, List<Long> to) {
        push.send(to, settleMessage(settlement, "정산이 취소됐어요. 다시 요청이 오면 그때 보내 주세요"));
    }

    /** 다시 알리기: 아직 안 보낸 원우에게. 밤이면 아침 8시로 미룬다 */
    public void settlementReminded(Settlement settlement, List<Long> unsent) {
        if (QuietHours.contains(clock.instant())) {
            settlement.holdRequest();
            return;
        }
        push.send(unsent, settleMessage(settlement, "아직 안 보낸 정산이 있어요 · 1인 " + won(settlement.getPerPerson())));
    }

    /** 모두 보냈어요: 계산한 사람에게 (계산한 사람이 마지막을 표시했으면 보내지 않는다). 밤이면 아침 8시로 미룬다 */
    public void settlementDone(Settlement settlement, Long actorId) {
        if (settlement.isPaidBy(actorId)) {
            return;
        }
        if (QuietHours.contains(clock.instant())) {
            settlement.holdDone();
            return;
        }
        push.send(List.of(settlement.getPayer().getId()), settleMessage(settlement, "정산 끝! 모두 보냈어요"));
    }

    private static PushMessage settleMessage(Settlement settlement, String body) {
        Meetup m = settlement.getMeetup();
        return new PushMessage(PushType.SETTLE, m.getTitle(), body, path(m), tag(m, "settle"));
    }

    private static String won(int amount) {
        return String.format("%,d원", amount);
    }

    private Set<Long> hostAndBlocked(Meetup meetup) {
        Long hostId = meetup.getHost().getId();
        Set<Long> except = new HashSet<>(blocks.findBlockedIds(hostId));
        except.addAll(blocks.findBlockerIds(hostId));
        except.add(hostId);
        return except;
    }

    /**
     * 새 공지: 그 학교 원우에게 (연합 공지는 다섯 학교 모두, 쓴 사람 빼고).
     * 공지는 원우 누구나 쓸 수 있어서, 알림은 교수·대학원장이 쓴 공지만 보낸다 (학교 전체에 가는 알림이라 남용 방지)
     */
    public void noticePosted(Notice notice) {
        if (!notice.getAuthor().isStaff()) {
            return;
        }
        if (QuietHours.contains(clock.instant())) {
            notice.holdAnnouncement(); // 아침 8시에 announceHeld 가 보낸다
            return;
        }
        announce(notice);
    }

    private void announce(Notice notice) {
        push.broadcast(notice.isFederated() ? null : notice.getSchool().getId(),
                new PushMessage(PushType.NOTICE, notice.isFederated() ? "새 연합 공지" : "새 공지", notice.getTitle(),
                        "/notices/" + notice.getId(), "notice-" + notice.getId()),
                Set.of(notice.getAuthor().getId()));
    }

    /**
     * 모집 마감 30분 전인데 아직 최소 인원이 안 모인 3교시: 참여자 모두에게 한 번 (ReminderJob 이 1분마다 부른다).
     * 연 사람에게는 지금 인원으로 진행할지 묻는 문구로 보낸다.
     * 보낸 3교시 수를 돌려준다
     */
    @Transactional
    public int remindDeadlines() {
        Instant now = clock.instant();
        int sent = 0;
        for (Meetup m : meetups.findDueForDeadlineReminder(now, now.plus(DEADLINE_NOTICE))) {
            if (Duration.between(m.getCreatedAt(), m.getDeadlineAt()).compareTo(SHORT_RECRUIT) <= 0) {
                continue;
            }
            if (meetups.markDeadlineReminded(m.getId(), now) == 0) {
                continue; // 다른 서버가 먼저 보냈다
            }
            long minutes = Math.max(1, (Duration.between(now, m.getDeadlineAt()).toSeconds() + 59) / 60);
            int left = m.getMinPeople() - m.getParticipantCount();
            String waiting = "모집 마감 " + minutes + "분 전이에요 · " + left + "명 더 모이면 성사돼요";
            Long hostId = m.getHost().getId();
            List<Long> others = participations.findMemberIds(m.getId()).stream().filter(id -> !id.equals(hostId)).toList();
            push.send(others, new PushMessage(PushType.DEADLINE, m.getTitle(), waiting, path(m), tag(m, "deadline")));
            // 연 사람에게는 "이 인원으로 진행할까요?" — 눌러서 상세에서 지금 인원으로 확정한다 (혼자면 기다리는 안내만)
            String toHost = m.getParticipantCount() >= 2
                    ? "모집 마감 " + minutes + "분 전 · " + m.getParticipantCount() + "명 모였어요. 이 인원으로 진행할까요?"
                    : waiting;
            push.send(List.of(hostId), new PushMessage(PushType.DEADLINE, m.getTitle(), toHost, path(m), tag(m, "deadline")));
            sent++;
        }
        return sent;
    }

    /**
     * 성사된 3교시가 1시간 뒤 모인다: 참여자 모두에게 한 번 (연 사람 포함, ReminderJob 이 1분마다 부른다).
     * 모임 2시간 안쪽에 연 3교시와, 서버가 늦게 깨서 10분 이하 남은 3교시는 보내지 않는다.
     * 보낸 3교시 수를 돌려준다
     */
    @Transactional
    public int remindMeetups() {
        Instant now = clock.instant();
        int sent = 0;
        for (Meetup m : meetups.findDueForMeetReminder(now.plus(MEET_NOTICE_TOO_LATE), now.plus(MEET_NOTICE))) {
            if (meetups.markMeetReminded(m.getId(), now) == 0) {
                continue; // 다른 서버가 먼저 보냈다
            }
            if (Duration.between(m.getCreatedAt(), m.getMeetAt()).compareTo(SHORT_NOTICE) < 0) {
                continue; // 방금 모였다 (보낸 것으로 적어 두고 다시 찾지 않는다)
            }
            long minutes = (Duration.between(now, m.getMeetAt()).toSeconds() + 59) / 60;
            String when = minutes >= 55 ? "1시간 뒤" : minutes + "분 뒤";
            push.send(participations.findMemberIds(m.getId()), new PushMessage(PushType.REMINDER, m.getTitle(),
                    when + " 3교시예요 · " + TIME.format(m.getMeetAt()) + " · " + m.getPlace(), path(m),
                    tag(m, "reminder")));
            sent++;
        }
        return sent;
    }

    /**
     * 밤사이 미룬 새 3교시 · 새 공지 · 정산 알림을 보낸다 (아침 8시부터, ReminderJob 이 1분마다 부른다).
     * 그사이 마감·취소됐거나 꽉 찬 3교시는 건너뛴다. 지운 공지·취소한 정산은 이미 없다.
     * 정산 요청은 그사이 모두 보냈으면, 정산 끝은 그사이 누가 보냄을 취소했으면 건너뛴다.
     * 보낸 수를 돌려준다
     */
    @Transactional
    public int announceHeld() {
        Instant now = clock.instant();
        if (QuietHours.contains(now)) {
            return 0;
        }
        int sent = 0;
        for (Meetup m : meetups.findHeldAnnouncements()) {
            if (meetups.releaseHeldAnnouncement(m.getId()) == 0 || !m.isJoinableAt(now)) {
                continue;
            }
            announce(m);
            sent++;
        }
        for (Notice n : notices.findHeldAnnouncements()) {
            if (notices.releaseHeldAnnouncement(n.getId()) == 0) {
                continue;
            }
            announce(n);
            sent++;
        }
        for (Settlement s : settlements.findHeld()) {
            boolean request = s.isRequestPending();
            boolean done = s.isDonePending();
            if (settlements.releaseHeld(s.getId()) == 0) {
                continue;
            }
            if (request && !s.isDone()) {
                push.send(settlementShares.findUnsentMemberIds(s.getId()),
                        settleMessage(s, "정산 요청 · 1인 " + won(s.getPerPerson())));
                sent++;
            }
            if (done && s.isDone()) {
                push.send(List.of(s.getPayer().getId()), settleMessage(s, "정산 끝! 모두 보냈어요"));
                sent++;
            }
        }
        return sent;
    }

    private List<Long> others(Meetup meetup, Long actorId) {
        return participations.findMemberIds(meetup.getId()).stream().filter(id -> !id.equals(actorId)).toList();
    }

    /** "성사까지 2명" 또는 "지금 3/6명" */
    private static String headcount(Meetup m) {
        int n = m.getParticipantCount();
        if (m.getMinPeople() != null && n < m.getMinPeople()) {
            return "성사까지 " + (m.getMinPeople() - n) + "명";
        }
        return "지금 " + n + (m.getMaxPeople() == null ? "명" : "/" + m.getMaxPeople() + "명");
    }

    private static String path(Meetup meetup) {
        return "/meetups/" + meetup.getId();
    }

    private static String tag(Meetup meetup, String kind) {
        return "meetup-" + meetup.getId() + "-" + kind;
    }
}
