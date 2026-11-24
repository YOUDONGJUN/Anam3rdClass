package com.anam3.auth.email;

/**
 * 인증코드 메일 보내기.
 * 운영: SMTP (SmtpVerificationMailer) / 로컬·SMTP 미설정: 로그로만 남김 (LoggingVerificationMailer) / 테스트: 메모리에 담음
 */
public interface VerificationMailer {

    void sendCode(String to, String code);
}
