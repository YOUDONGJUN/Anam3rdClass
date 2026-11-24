package com.anam3.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** API 테스트에서 진짜 대신 끼우는 빈: 움직이는 시계, 가짜 카카오, 메일 대신 메모리 */
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
    CapturingMailer capturingMailer() {
        return new CapturingMailer();
    }
}
