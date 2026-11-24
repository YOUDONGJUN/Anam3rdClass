package com.anam3.push;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.anam3.common.AppProperties;

/** 앱 푸시: Firebase 키가 있으면 진짜로 보내고, 없으면 로그만 남긴다. 마감 알림용 스케줄러도 켠다 */
@Configuration
@EnableScheduling
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    PushSender pushSender(AppProperties props, Clock clock) {
        String serviceAccount = props.firebaseServiceAccount();
        if (serviceAccount == null) {
            log.info("FIREBASE_SERVICE_ACCOUNT 가 없어 앱 푸시를 보내지 않고 로그에만 남깁니다");
            return new LoggingPushSender();
        }
        return new FcmPushSender(serviceAccount, clock);
    }

    @Bean
    PushDispatcher pushDispatcher() {
        return new AsyncPushDispatcher();
    }
}
