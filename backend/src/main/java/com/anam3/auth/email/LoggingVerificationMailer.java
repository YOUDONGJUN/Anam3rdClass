package com.anam3.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SMTP 설정이 없을 때 (로컬 개발): 메일 대신 서버 로그에 코드를 찍는다 */
public class LoggingVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationMailer.class);

    @Override
    public void sendCode(String to, String code) {
        log.info("[메일 대신 로그] {} 인증코드: {}", to, code);
    }
}
