package com.anam3.common;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * 몰아치는 요청을 막는 횟수 제한을 만든다. 서버가 한 대(Render starter)라 메모리로 센다 — 배포하면 처음부터 다시 센다.
 * 테스트는 매번 clearAll 로 비운다
 */
@Component
public class RateLimits {

    private final List<RateLimiter> all = new CopyOnWriteArrayList<>();

    /** 기간(window) 안에 limit 번까지 */
    public RateLimiter create(int limit, Duration window) {
        RateLimiter limiter = new RateLimiter(limit, window);
        all.add(limiter);
        return limiter;
    }

    public void clearAll() {
        all.forEach(RateLimiter::clear);
    }
}
