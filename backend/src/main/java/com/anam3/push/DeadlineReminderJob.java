package com.anam3.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 모집 마감 30분 전 3교시를 찾아 알린다 (app.push.reminders=false 면 끈다 — 테스트) */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "reminders", havingValue = "true", matchIfMissing = true)
public class DeadlineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);

    private final PushNotifier notifier;

    public DeadlineReminderJob(PushNotifier notifier) {
        this.notifier = notifier;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        try {
            notifier.remindDeadlines();
        } catch (RuntimeException e) {
            log.warn("마감 알림을 찾다가 문제가 생겼습니다", e);
        }
    }
}
