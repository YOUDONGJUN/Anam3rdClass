package com.anam3.push;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/** 스레드 2개로 차례차례 보낸다. 밀린 일이 너무 많으면(서버 메모리 512MB) 새 일은 버리고 로그만 */
public class AsyncPushDispatcher implements PushDispatcher, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AsyncPushDispatcher.class);

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000),
            Thread.ofPlatform().name("push-", 0).daemon(true).factory(),
            (task, pool) -> log.warn("보낼 푸시가 너무 많이 밀려서 하나를 건너뜁니다"));

    @Override
    public void dispatch(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("푸시를 보내다 문제가 생겼습니다", e);
            }
        });
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
