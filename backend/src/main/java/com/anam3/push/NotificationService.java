package com.anam3.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.auth.LoginMember;
import com.anam3.member.MemberRepository;
import com.anam3.member.MemberStatus;

/**
 * 알림함: 앱 푸시와 같은 알림을 한 사람 몫씩 남겨 웹에서도 본다.
 * 푸시를 보내는 트랜잭션 안에서 담으므로, 롤백된 일은 알림함에도 남지 않는다.
 */
@Service
public class NotificationService {

    /** 알림함에 보여 줄 최근 알림 수 */
    static final int LIST_SIZE = 50;
    /** 이보다 오래된 알림은 지운다 */
    static final Duration KEEP = Duration.ofDays(30);

    private final NotificationRepository notifications;
    private final MemberRepository members;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, MemberRepository members, Clock clock) {
        this.notifications = notifications;
        this.members = members;
        this.clock = clock;
    }

    public record NotificationView(Long id, PushType type, String title, String body, String path, Instant createdAt,
            boolean read) {
    }

    public record NotificationList(List<NotificationView> items, long unreadCount) {
    }

    public record UnreadCount(long count) {
    }

    /**
     * 이 회원들의 알림함에 담는다 (활동 중인 회원만). 같은 묶음(tag)의 안 읽은 알림이 있으면 새로 쌓지 않고
     * 그 줄을 새 내용으로 바꿔 맨 위로 올린다 — "한 명이 참여했어요"가 여러 줄 쌓이지 않게
     */
    @Transactional
    public void record(Collection<Long> memberIds, PushMessage message) {
        if (memberIds.isEmpty()) {
            return;
        }
        List<Long> active = notifications.findActiveMemberIds(Set.copyOf(memberIds), MemberStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Set<Long> refreshed = new HashSet<>();
        if (message.tag() != null) {
            for (Notification n : notifications.findUnreadByTag(active, message.tag())) {
                if (refreshed.add(n.getMemberId())) {
                    n.refresh(message, now);
                }
            }
        }
        notifications.saveAll(active.stream()
                .filter(id -> !refreshed.contains(id))
                .map(id -> new Notification(members.getReferenceById(id), message, now))
                .toList());
    }

    /** 내 알림함: 최근 50개와 안 읽은 수 */
    @Transactional(readOnly = true)
    public NotificationList list(LoginMember viewer) {
        List<NotificationView> items = notifications.findRecent(viewer.id(), PageRequest.of(0, LIST_SIZE)).stream()
                .map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getPath(),
                        n.getCreatedAt(), n.isRead()))
                .toList();
        return new NotificationList(items, notifications.countUnread(viewer.id()));
    }

    /** 메뉴의 종 아이콘 숫자 */
    @Transactional(readOnly = true)
    public UnreadCount unreadCount(LoginMember viewer) {
        return new UnreadCount(notifications.countUnread(viewer.id()));
    }

    /** 알림함을 열어 봤다: 모두 읽음 */
    @Transactional
    public void readAll(LoginMember viewer) {
        notifications.markAllRead(viewer.id(), clock.instant());
    }

    /** 탈퇴: 이 사람의 알림을 모두 지운다 */
    @Transactional
    public void forget(Long memberId) {
        notifications.deleteAllOfMember(memberId);
    }

    /** 3교시를 지웠다: 그 3교시로 가는 알림을 지운다 (눌러도 열 화면이 없다) */
    @Transactional
    public void forgetMeetup(Long meetupId) {
        notifications.deleteByPath("/meetups/" + meetupId);
    }

    /** 30일 지난 알림을 지운다 (하루 한 번). 지운 수를 돌려준다 */
    @Transactional
    public int purgeOld() {
        return notifications.deleteOlderThan(clock.instant().minus(KEEP));
    }
}
