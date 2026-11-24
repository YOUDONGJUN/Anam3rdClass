package com.anam3.auth.email;

import java.io.UnsupportedEncodingException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.anam3.common.ApiException;

/** SMTP 로 인증코드 메일을 보낸다 (spring.mail.* 설정이 있을 때) */
public class SmtpVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationMailer.class);
    private static final String SENDER_NAME = "안암3교시";

    private final JavaMailSender sender;
    private final String from;

    public SmtpVerificationMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendCode(String to, String code) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(from, SENDER_NAME, "UTF-8"));
            helper.setTo(to);
            helper.setSubject("[안암3교시] 인증코드 " + code);
            helper.setText("""
                    안암3교시 가입 인증코드예요.

                        %s

                    10분 안에 화면에 입력해 주세요.
                    본인이 요청하지 않았다면 이 메일은 무시해도 돼요.
                    """.formatted(code));
            sender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("인증 메일을 보내지 못했습니다: {}", to, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_FAILED",
                    "인증 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요");
        }
    }
}
