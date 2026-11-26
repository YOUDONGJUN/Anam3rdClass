package com.anam3.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 횟수 제한 하나: 키(이메일·IP·회원)마다 최근 기록 시각을 모아 기간 안의 수를 센다. RateLimits 에서 만든다.
 * 키가 많아지면 1분에 한 번만 오래된 기록을 치운다 (아무 키로 마구 요청해도 요청마다 전체를 훑지 않게)
 */
public final class RateLimiter {

    /** 키가 이보다 많아지면 오래된 기록을 치운다 */
    private static final int SWEEP_ABOVE = 5_000;
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.EPOCH);

    RateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    /** 기간 안에 이미 limit 번 기록됐는지 (기록은 하지 않는다) */
    public boolean isLimited(String key, Instant now) {
        Deque<Instant> recent = hits.get(key);
        if (recent == null) {
            return false;
        }
        Instant from = now.minus(window);
        recent.removeIf(at -> !at.isAfter(from));
        return recent.size() >= limit;
    }

    /** 한 번 기록한다 (실패한 시도 등) */
    public void record(String key, Instant now) {
        sweepIfCrowded(now);
        hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).add(now);
    }

    /** 막히지 않았으면 기록하고 true, 막혔으면 기록하지 않고 false */
    public boolean tryAcquire(String key, Instant now) {
        if (isLimited(key, now)) {
            return false;
        }
        record(key, now);
        return true;
    }

    /** 이 키의 기록을 지운다 (로그인에 성공하면 틀린 기록을 지우는 등) */
    public void reset(String key) {
        hits.remove(key);
    }

    void clear() {
        hits.clear();
        lastSweep.set(Instant.EPOCH);
    }

    private void sweepIfCrowded(Instant now) {
        if (hits.size() <= SWEEP_ABOVE) {
            return;
        }
        Instant last = lastSweep.get();
        if (now.isBefore(last.plus(SWEEP_EVERY)) || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        Instant from = now.minus(window);
        hits.entrySet().removeIf(e -> {
            e.getValue().removeIf(at -> !at.isAfter(from));
            return e.getValue().isEmpty();
        });
    }
}
