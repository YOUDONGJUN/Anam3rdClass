package com.anam3.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 때가 된 알림을 보낸다 (app.push.reminders=false 면 끈다 — 테스트).
 * 모집 마감 30분 전 · 모임 1시간 전 · 밤사이 미룬 새 3교시·공지 (아침 8시부터)
 */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "reminders", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final PushNotifier notifier;

    public ReminderJob(PushNotifier notifier) {
        this.notifier = notifier;
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
}
