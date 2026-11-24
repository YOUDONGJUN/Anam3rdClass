package com.anam3.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.anam3.push.PushMessage;
import com.anam3.push.PushSender;

/** 테스트용 푸시: 보내는 대신 (토큰, 알림)을 기억한다. dead 로 정한 토큰은 "앱을 지운 기기"처럼 답한다 */
public class CapturingPushSender implements PushSender {

    public record Sent(String token, PushMessage message) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> dead = ConcurrentHashMap.newKeySet();

    @Override
    public Result send(String token, PushMessage message) {
        if (dead.contains(token)) {
            return Result.INVALID_TOKEN;
        }
        sent.add(new Sent(token, message));
        return Result.SENT;
    }

    /** 이 기기가 받은 알림 (받은 순서) */
    public List<PushMessage> to(String token) {
        return sent.stream().filter(s -> s.token().equals(token)).map(Sent::message).toList();
    }

    public List<Sent> all() {
        return List.copyOf(sent);
    }

    public void markDead(String token) {
        dead.add(token);
    }

    public void clear() {
        sent.clear();
        dead.clear();
    }
}
