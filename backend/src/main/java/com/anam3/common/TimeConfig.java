package com.anam3.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 항상 Clock 빈에서 가져온다. 테스트에서 시간을 앞으로 돌려 마감/무산을 검증할 수 있다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
