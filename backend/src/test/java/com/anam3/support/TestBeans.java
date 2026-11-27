package com.anam3.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.anam3.push.PushDispatcher;

/** API 테스트에서 진짜 대신 끼우는 빈: 움직이는 시계, 가짜 카카오·장소 검색, 메일·푸시 대신 메모리 */
@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(ApiTestSupport.T0);
    }

    @Bean
    @Primary
    FakeKakaoClient fakeKakaoClient() {
        return new FakeKakaoClient();
    }

    @Bean
    @Primary
    FakePlaceSearch fakePlaceSearch() {
        return new FakePlaceSearch();
    }

    @Bean
    @Primary
    CapturingMailer capturingMailer() {
        return new CapturingMailer();
    }

    @Bean
    @Primary
    CapturingPushSender capturingPushSender() {
        return new CapturingPushSender();
    }

    /** 푸시를 커밋 직후 같은 스레드에서 바로 보낸다 (요청이 끝나면 이미 보낸 상태라 바로 확인할 수 있다) */
    @Bean
    @Primary
    PushDispatcher inlinePushDispatcher() {
        return Runnable::run;
    }
}
