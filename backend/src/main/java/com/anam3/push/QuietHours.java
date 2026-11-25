package com.anam3.push;

import java.time.Instant;
import java.time.ZoneId;

/**
 * 밤에는 조용히: 다 같이 받는 알림(새 3교시 · 새 공지)은 한국 시간 밤 12시 ~ 아침 8시에 보내지 않고 아침 8시에 보낸다.
 * 내 3교시 알림(참여 · 성사 · 마감 · 변경 · 빈자리 · 1시간 전)은 3교시가 밤에 모이므로 밤에도 보낸다.
 */
public final class QuietHours {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 아침 몇 시부터 보내나 (0시부터 이 시각 전까지 조용히) */
    public static final int END_HOUR = 8;

    private QuietHours() {
    }

    public static boolean contains(Instant now) {
        return now.atZone(SEOUL).getHour() < END_HOUR;
    }
}
