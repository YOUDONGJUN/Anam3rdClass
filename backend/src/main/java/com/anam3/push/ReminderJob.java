package com.anam3.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.anam3.meetup.MeetupService;

/**
 * 1분마다 때가 된 알림을 보낸다 (app.push.reminders=false 면 끈다 — 테스트).
 * 모집 마감 30분 전 · 모임 1시간 전 · 밤사이 미룬 새 3교시·공지 (아침 8시부터).
 * 하루 한 번(새벽 4시 30분) 30일 지난 알림함 알림과 '성사된 3교시에서 나간 기록'을 지운다
 */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "reminders", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final PushNotifier notifier;
    private final NotificationService notifications;
    private final MeetupService meetupService;

    public ReminderJob(PushNotifier notifier, NotificationService notifications, MeetupService meetupService) {
        this.notifier = notifier;
        this.notifications = notifications;
        this.meetupService = meetupService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        // 하나가 실패해도 나머지는 보낸다
        try {
            notifier.remindDeadlines();
        } catch (RuntimeException e) {
            log.warn("마감 알림을 찾다가 문제가 생겼습니다", e);
        }
        try {
            notifier.remindMeetups();
        } catch (RuntimeException e) {
            log.warn("모임 1시간 전 알림을 찾다가 문제가 생겼습니다", e);
        }
        try {
            notifier.announceHeld();
        } catch (RuntimeException e) {
            log.warn("밤사이 미룬 알림을 보내다가 문제가 생겼습니다", e);
        }
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void purgeNotifications() {
        try {
            int n = notifications.purgeOld();
            if (n > 0) {
                log.info("30일 지난 알림 {}개를 지웠습니다", n);
            }
        } catch (RuntimeException e) {
            log.warn("지난 알림을 지우다가 문제가 생겼습니다", e);
        }
        try {
            meetupService.purgeOldLeaves();
        } catch (RuntimeException e) {
            log.warn("지난 3교시 나간 기록을 지우다가 문제가 생겼습니다", e);
        }
    }
}
