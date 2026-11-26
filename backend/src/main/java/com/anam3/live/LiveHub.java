package com.anam3.live;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 화면에 바로 알리는 실시간 연결 (Server-Sent Events). 로그인한 화면마다 연결 하나.
 * 서버가 한 대(Render)라 메모리에 둔다 — 배포하면 끊기고, 화면이 다시 붙는다.
 * <p>
 * 보내는 건 "무엇이 바뀌었다"뿐이다 (예: chat {meetupId}). 내용은 화면이 API 로 다시 읽는다 —
 * 실명 공개 규칙을 API 한 곳에서만 지키려고.
 */
@Component
public class LiveHub {

    /** 연결은 30분마다 다시 (화면이 바로 다시 붙는다) */
    static final Duration TIMEOUT = Duration.ofMinutes(30);
    /** 한 사람이 동시에 열어 둘 수 있는 연결 (폰·PC·탭) */
    static final int MAX_PER_MEMBER = 4;
    /** 톡방을 보고 있다고 칠 시간 (화면이 읽음 표시를 보낼 때마다 늘린다) */
    public static final Duration VIEWING_FOR = Duration.ofSeconds(90);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    /** 사람 → (보고 있는 톡방, 언제까지) */
    private final Map<Long, Viewing> viewing = new ConcurrentHashMap<>();
    private final Clock clock;

    private record Viewing(Long meetupId, Instant until) {
    }

    public LiveHub(Clock clock) {
        this.clock = clock;
    }

    public SseEmitter connect(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        List<SseEmitter> mine = emitters.computeIfAbsent(memberId, id -> new CopyOnWriteArrayList<>());
        mine.add(emitter);
        while (mine.size() > MAX_PER_MEMBER) {
            SseEmitter oldest = mine.removeFirst();
            oldest.complete();
        }
        Runnable remove = () -> remove(memberId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(memberId, emitter, "ready", Map.of());
        return emitter;
    }

    /** 이 회원들의 화면에 알린다. 트랜잭션 안이면 저장이 끝난 뒤에 */
    public void publish(Collection<Long> memberIds, String event, Object data) {
        List<Long> to = List.copyOf(memberIds);
        Runnable task = () -> to.forEach(id -> emitters.getOrDefault(id, List.of())
                .forEach(e -> send(id, e, event, data)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    public boolean isConnected(Long memberId) {
        return !emitters.getOrDefault(memberId, List.of()).isEmpty();
    }

    /** 이 사람이 이 톡방을 보고 있다 (읽음 표시를 보낼 때) */
    public void viewing(Long memberId, Long meetupId) {
        viewing.put(memberId, new Viewing(meetupId, clock.instant().plus(VIEWING_FOR)));
    }

    public void leftRoom(Long memberId, Long meetupId) {
        viewing.computeIfPresent(memberId, (id, v) -> v.meetupId().equals(meetupId) ? null : v);
    }

    /** 지금 이 톡방 화면을 열어 두고 있으면 폰 알림을 보내지 않는다 */
    public boolean isViewing(Long memberId, Long meetupId) {
        Viewing v = viewing.get(memberId);
        return v != null && v.meetupId().equals(meetupId) && clock.instant().isBefore(v.until())
                && isConnected(memberId);
    }

    /** 프록시가 조용한 연결을 끊지 않게 25초마다 한 줄 */
    @Scheduled(fixedDelay = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((id, list) -> list.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                remove(id, e);
            }
        }));
        Instant now = clock.instant();
        viewing.values().removeIf(v -> !now.isBefore(v.until()));
    }

    public void clear() {
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
        emitters.clear();
        viewing.clear();
    }

    private void send(Long memberId, SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            remove(memberId, emitter);
        }
    }

    private void remove(Long memberId, SseEmitter emitter) {
        emitters.computeIfPresent(memberId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
