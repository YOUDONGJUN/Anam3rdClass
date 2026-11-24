package com.anam3.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

import com.anam3.common.AppProperties;

/**
 * SPRING_MAIL_HOST 등 SMTP 설정이 있으면 실제 메일, 없으면 로그로만 남긴다.
 * (스프링이 spring.mail.host 가 있을 때만 JavaMailSender 를 만든다)
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public VerificationMailer verificationMailer(ObjectProvider<JavaMailSender> mailSender, AppProperties props,
            Environment env) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP 설정(SPRING_MAIL_HOST)이 없어 인증코드를 메일 대신 로그에 남깁니다. 운영에서는 꼭 설정하세요");
            return new LoggingVerificationMailer();
        }
        String from = props.mail() != null && props.mail().from() != null && !props.mail().from().isBlank()
                ? props.mail().from().strip()
                : env.getProperty("spring.mail.username");
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("보내는 사람 주소가 없어요. MAIL_FROM 또는 SPRING_MAIL_USERNAME 을 설정해 주세요");
        }
        return new SmtpVerificationMailer(sender, from);
    }
}
